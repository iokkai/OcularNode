/**
 * OcularNode WebRTC P2P Client & E2EE Crypto Engine (JavaScript)
 *
 * Implements:
 * 1. Web Crypto API AES-256-GCM authenticated encryption (100% compatible with Android AesGcmCipher)
 * 2. MQTT over WebSocket (WSS 443) decentralized signaling with multi-broker fallback
 * 3. WebRTC PeerConnection with STUN ICE gathering, H.264 video rendering, and DataChannel control
 */

// =============================================================================
// 1. Web Crypto AES-256-GCM (End-to-End Encryption)
// =============================================================================

class AesGcmCrypto {
    static async deriveKey(secret) {
        if (!secret || secret.trim() === '') {
            throw new Error("Device secret passphrase must not be blank");
        }
        const enc = new TextEncoder();
        // SHA-256 digest produces 32 bytes (256-bit key)
        const hash = await window.crypto.subtle.digest("SHA-256", enc.encode(secret));
        return await window.crypto.subtle.importKey(
            "raw",
            hash,
            { name: "AES-GCM" },
            false,
            ["encrypt", "decrypt"]
        );
    }

    static async encrypt(plaintext, secret) {
        const key = await this.deriveKey(secret);
        const iv = window.crypto.getRandomValues(new Uint8Array(12)); // 12-byte IV
        const enc = new TextEncoder();
        const cipherBuffer = await window.crypto.subtle.encrypt(
            { name: "AES-GCM", iv: iv, tagLength: 128 },
            key,
            enc.encode(plaintext)
        );

        const ivBase64 = this.bufferToBase64(iv);
        const dataBase64 = this.bufferToBase64(new Uint8Array(cipherBuffer));

        return JSON.stringify({
            iv: ivBase64,
            data: dataBase64
        });
    }

    static async decrypt(encryptedJsonStr, secret) {
        const parsed = typeof encryptedJsonStr === 'string' ? JSON.parse(encryptedJsonStr) : encryptedJsonStr;
        if (!parsed.iv || !parsed.data) {
            throw new Error("Invalid encrypted payload format (missing iv or data)");
        }

        const iv = this.base64ToBuffer(parsed.iv);
        const data = this.base64ToBuffer(parsed.data);
        const key = await this.deriveKey(secret);

        const decryptedBuffer = await window.crypto.subtle.decrypt(
            { name: "AES-GCM", iv: iv, tagLength: 128 },
            key,
            data
        );

        return new TextDecoder().decode(decryptedBuffer);
    }

    static bufferToBase64(uint8Array) {
        let binary = '';
        const len = uint8Array.byteLength;
        for (let i = 0; i < len; i++) {
            binary += String.fromCharCode(uint8Array[i]);
        }
        return window.btoa(binary);
    }

    static base64ToBuffer(base64) {
        const binaryString = window.atob(base64);
        const len = binaryString.length;
        const bytes = new Uint8Array(len);
        for (let i = 0; i < len; i++) {
            bytes[i] = binaryString.charCodeAt(i);
        }
        return bytes;
    }
}

// =============================================================================
// 2. MQTT over WebSocket Signaling Channel (Multi-Broker Failover)
// =============================================================================

const DEFAULT_WSS_BROKERS = [
    "wss://broker.hivemq.com:8884/mqtt",
    "wss://broker.emqx.io:8084/mqtt",
    "wss://test.mosquitto.org:8081/mqtt"
];

class MqttSignalingClient {
    constructor(deviceId, deviceSecret, onMessage, onStatusChange) {
        this.deviceId = deviceId;
        this.deviceSecret = deviceSecret;
        this.onMessage = onMessage;
        this.onStatusChange = onStatusChange || (() => {});
        this.client = null;
        this.topic = `ocularnode/v1/${deviceId}/signal`;
        this.senderId = "viewer-web-" + Math.random().toString(36).substring(2, 9);
        this.isConnected = false;
        this.currentBrokerIndex = 0;
    }

    async connect() {
        this.disconnect();

        const brokerUrl = DEFAULT_WSS_BROKERS[this.currentBrokerIndex % DEFAULT_WSS_BROKERS.length];
        this.onStatusChange(`正在連接信令通道 (${brokerUrl.split('/')[2]})...`);

        return new Promise((resolve, reject) => {
            try {
                const clientId = "OcularWeb-" + Math.random().toString(36).substring(2, 10);
                this.client = mqtt.connect(brokerUrl, {
                    clientId: clientId,
                    clean: true,
                    connectTimeout: 5000,
                    reconnectPeriod: 4000
                });

                this.client.on('connect', () => {
                    this.isConnected = true;
                    this.onStatusChange("信令伺服器已連線，訂閱頻道中...");
                    this.client.subscribe(this.topic, { qos: 0 }, (err) => {
                        if (err) {
                            console.error("MQTT subscription error:", err);
                        } else {
                            console.log(`Subscribed to topic: ${this.topic}`);
                            this.onStatusChange("信令頻道已就緒");
                            resolve();
                        }
                    });
                });

                this.client.on('message', async (topic, payload) => {
                    if (topic !== this.topic) return;
                    try {
                        const rawStr = payload.toString();
                        const decryptedJson = await AesGcmCrypto.decrypt(rawStr, this.deviceSecret);
                        const message = JSON.parse(decryptedJson);

                        // Ignore messages sent by ourselves
                        if (message.senderId === this.senderId) return;

                        console.log(`[Signaling Rx] Type: ${message.type}, from: ${message.senderId}`);
                        this.onMessage(message);
                    } catch (e) {
                        console.warn("Failed to decrypt or parse incoming signaling message:", e);
                    }
                });

                this.client.on('error', (err) => {
                    console.warn(`MQTT broker error on ${brokerUrl}:`, err);
                    this.tryNextBroker();
                });

                this.client.on('close', () => {
                    this.isConnected = false;
                });
            } catch (err) {
                console.error("MQTT initialization failed:", err);
                this.tryNextBroker();
                reject(err);
            }
        });
    }

    tryNextBroker() {
        if (this.client) {
            try { this.client.end(true); } catch (_) {}
            this.client = null;
        }
        this.currentBrokerIndex++;
        const nextBroker = DEFAULT_WSS_BROKERS[this.currentBrokerIndex % DEFAULT_WSS_BROKERS.length];
        this.onStatusChange(`切換備援信令伺服器: ${nextBroker.split('/')[2]}...`);
        setTimeout(() => this.connect(), 1000);
    }

    async sendSignal(payload) {
        if (!this.client || !this.isConnected) {
            console.warn("Cannot send signal: MQTT client not connected");
            return false;
        }

        try {
            payload.senderId = this.senderId;
            payload.targetId = this.deviceId;
            payload.timestamp = Date.now();

            const plainJson = JSON.stringify(payload);
            const encryptedJson = await AesGcmCrypto.encrypt(plainJson, this.deviceSecret);

            this.client.publish(this.topic, encryptedJson, { qos: 0 });
            console.log(`[Signaling Tx] Type: ${payload.type}`);
            return true;
        } catch (e) {
            console.error("Error encrypting/sending signaling message:", e);
            return false;
        }
    }

    disconnect() {
        if (this.client) {
            try {
                this.client.end(true);
            } catch (_) {}
            this.client = null;
        }
        this.isConnected = false;
    }
}

// =============================================================================
// 3. WebRTC P2P PeerConnection Manager
// =============================================================================

class OcularWebRtcClient {
    constructor(credentials, callbacks) {
        this.credentials = credentials; // { deviceId, deviceSecret, name, ip, ipv6, port }
        this.callbacks = callbacks || {};
        // callbacks: onTrack, onDataChannelMessage, onConnectionStateChange, onStatusMessage, onStatsUpdate

        this.sessionId = "session-" + Math.random().toString(36).substring(2, 9);
        this.pc = null;
        this.dataChannel = null;
        this.signaling = null;
        this.localAudioTrack = null;
        this.statsInterval = null;
        this.isConnecting = false;
        this.isConnected = false;
    }

    async start() {
        this.stop();
        this.isConnecting = true;
        this.callbacks.onStatusMessage?.("正在初始化 WebRTC 引擎與端對端密鑰...");

        // 1. Initialize Signaling
        this.signaling = new MqttSignalingClient(
            this.credentials.deviceId,
            this.credentials.deviceSecret,
            (msg) => this.handleSignalingMessage(msg),
            (status) => this.callbacks.onStatusMessage?.(status)
        );

        await this.signaling.connect();

        // 2. Initialize RTCPeerConnection
        const iceServers = [
            { urls: 'stun:stun.l.google.com:19302' },
            { urls: 'stun:stun1.l.google.com:19302' },
            { urls: 'stun:stun.cloudflare.com:3478' }
        ];

        this.pc = new RTCPeerConnection({
            iceServers: iceServers,
            iceCandidatePoolSize: 2
        });

        // 3. Remote Tracks Listener (Video / Audio)
        this.pc.ontrack = (event) => {
            console.log("Remote track received:", event.track.kind);
            if (event.streams && event.streams[0]) {
                this.callbacks.onTrack?.(event.streams[0], event.track);
            }
        };

        // 4. Local ICE Candidate Gathering
        this.pc.onicecandidate = (event) => {
            if (event.candidate) {
                this.signaling.sendSignal({
                    type: "CANDIDATE",
                    sessionId: this.sessionId,
                    candidate: event.candidate.candidate,
                    sdpMid: event.candidate.sdpMid,
                    sdpMLineIndex: event.candidate.sdpMLineIndex
                });
            }
        };

        // 5. Connection State Listener
        this.pc.onconnectionstatechange = () => {
            const state = this.pc ? this.pc.connectionState : 'closed';
            console.log("WebRTC Connection state:", state);
            this.callbacks.onConnectionStateChange?.(state);

            if (state === 'connected') {
                this.isConnected = true;
                this.isConnecting = false;
                this.callbacks.onStatusMessage?.("🟢 P2P 直連成功 (E2EE)");
                this.startStatsMonitoring();
            } else if (state === 'disconnected' || state === 'failed') {
                this.isConnected = false;
                this.callbacks.onStatusMessage?.("⚠️ 連線中斷，嘗試重新連線中...");
            }
        };

        // 6. Create DataChannel for Control Commands
        this.dataChannel = this.pc.createDataChannel("control", {
            ordered: true
        });

        this.setupDataChannel(this.dataChannel);

        // Also listen if remote opens data channel
        this.pc.ondatachannel = (event) => {
            this.setupDataChannel(event.channel);
        };

        // 7. Create SDP Offer with transceivers for Video and Audio
        this.pc.addTransceiver('video', { direction: 'recvonly' });
        this.pc.addTransceiver('audio', { direction: 'sendrecv' });

        this.callbacks.onStatusMessage?.("正在生成 SDP Offer 握手邀約...");
        const offer = await this.pc.createOffer({
            offerToReceiveVideo: true,
            offerToReceiveAudio: true
        });

        await this.pc.setLocalDescription(offer);

        // 8. Send Encrypted Offer to Camera
        await this.signaling.sendSignal({
            type: "OFFER",
            sessionId: this.sessionId,
            sdp: offer.sdp
        });

        this.callbacks.onStatusMessage?.("已發送加密 SDP Offer，等待相機端回應...");
    }

    setupDataChannel(channel) {
        channel.onopen = () => {
            console.log("DataChannel 'control' opened");
            this.callbacks.onStatusMessage?.("雙向控制通道已就緒");
            // Request camera status
            this.sendControlCommand("get_status", "");
        };

        channel.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data);
                this.callbacks.onDataChannelMessage?.(data);
            } catch (e) {
                console.log("DataChannel raw text:", event.data);
            }
        };

        channel.onerror = (err) => {
            console.warn("DataChannel error:", err);
        };
    }

    async handleSignalingMessage(msg) {
        if (!this.pc) return;

        if (msg.sessionId && msg.sessionId !== this.sessionId) {
            console.log(`Ignoring message for different session: ${msg.sessionId}`);
            return;
        }

        switch (msg.type) {
            case "ANSWER":
                console.log("Received SDP Answer from camera");
                this.callbacks.onStatusMessage?.("接收到 SDP Answer，正在建立 P2P 媒體串流...");
                try {
                    const remoteDesc = new RTCSessionDescription({
                        type: 'answer',
                        sdp: msg.sdp
                    });
                    await this.pc.setRemoteDescription(remoteDesc);
                } catch (e) {
                    console.error("Error setting remote description:", e);
                }
                break;

            case "CANDIDATE":
                if (msg.candidate) {
                    try {
                        const candidate = new RTCIceCandidate({
                            candidate: msg.candidate,
                            sdpMid: msg.sdpMid,
                            sdpMLineIndex: msg.sdpMLineIndex
                        });
                        await this.pc.addIceCandidate(candidate);
                    } catch (e) {
                        console.warn("Error adding remote ICE candidate:", e);
                    }
                }
                break;

            case "BYE":
                console.log("Camera sent BYE, disconnecting");
                this.stop();
                break;
        }
    }

    sendControlCommand(command, value = "") {
        const payload = JSON.stringify({
            command: command,
            value: value,
            timestamp: Date.now()
        });

        if (this.dataChannel && this.dataChannel.readyState === 'open') {
            this.dataChannel.send(payload);
            return true;
        } else if (this.signaling && this.signaling.isConnected) {
            // Fallback over MQTT signaling if DataChannel is not ready
            this.signaling.sendSignal({
                type: "CONTROL",
                sessionId: this.sessionId,
                command: command,
                sdp: value
            });
            return true;
        }
        return false;
    }

    async startAudioBroadcast() {
        try {
            const stream = await navigator.mediaDevices.getUserMedia({ audio: true, video: false });
            this.localAudioTrack = stream.getAudioTracks()[0];
            if (this.pc) {
                const senders = this.pc.getSenders();
                const audioSender = senders.find(s => s.track && s.track.kind === 'audio') ||
                                    senders.find(s => !s.track);
                if (audioSender) {
                    audioSender.replaceTrack(this.localAudioTrack);
                } else {
                    this.pc.addTrack(this.localAudioTrack, stream);
                }
            }
            return true;
        } catch (e) {
            console.error("Microphone access failed:", e);
            throw e;
        }
    }

    stopAudioBroadcast() {
        if (this.localAudioTrack) {
            this.localAudioTrack.stop();
            if (this.pc) {
                const senders = this.pc.getSenders();
                const audioSender = senders.find(s => s.track === this.localAudioTrack);
                if (audioSender) {
                    audioSender.replaceTrack(null);
                }
            }
            this.localAudioTrack = null;
        }
    }

    startStatsMonitoring() {
        if (this.statsInterval) clearInterval(this.statsInterval);

        let lastBytes = 0;
        let lastTimestamp = Date.now();

        this.statsInterval = setInterval(async () => {
            if (!this.pc || this.pc.connectionState !== 'connected') return;

            try {
                const stats = await this.pc.getStats();
                let fps = 0;
                let resolution = '--';
                let bitrateKbps = 0;
                let packetLoss = 0;

                stats.forEach(report => {
                    if (report.type === 'inbound-rtp' && report.kind === 'video') {
                        fps = report.framesPerSecond || 0;
                        if (report.frameWidth && report.frameHeight) {
                            resolution = `${report.frameWidth}x${report.frameHeight}`;
                        }
                        const now = Date.now();
                        const bytes = report.bytesReceived || 0;
                        if (lastBytes > 0 && now > lastTimestamp) {
                            bitrateKbps = Math.round(((bytes - lastBytes) * 8) / (now - lastTimestamp));
                        }
                        lastBytes = bytes;
                        lastTimestamp = now;
                        packetLoss = report.packetsLost || 0;
                    }
                });

                this.callbacks.onStatsUpdate?.({
                    fps: Math.round(fps),
                    resolution: resolution,
                    bitrateKbps: Math.max(0, bitrateKbps),
                    packetLoss: packetLoss
                });
            } catch (_) {}
        }, 1500);
    }

    stop() {
        if (this.statsInterval) {
            clearInterval(this.statsInterval);
            this.statsInterval = null;
        }
        this.stopAudioBroadcast();

        if (this.dataChannel) {
            try { this.dataChannel.close(); } catch (_) {}
            this.dataChannel = null;
        }

        if (this.pc) {
            try { this.pc.close(); } catch (_) {}
            this.pc = null;
        }

        if (this.signaling) {
            this.signaling.disconnect();
            this.signaling = null;
        }

        this.isConnected = false;
        this.isConnecting = false;
    }
}
