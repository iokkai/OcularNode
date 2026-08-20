/**
 * OcularNode Web Viewer - Application Controller
 * Handles UI interactions, multi-camera persistence, DataChannel commands, and media handling.
 */

// =============================================================================
// State & Storage
// =============================================================================

const STORAGE_KEY = 'ocular_paired_cameras';
let activeClient = null;
let pairedCameras = [];
let selectedCamera = null;

let isTorchOn = false;
let nightVisionMode = "auto";
let isPttActive = false;
let audioContext = null;
let audioAnalyser = null;
let html5QrCode = null;

// =============================================================================
// Initialization
// =============================================================================

document.addEventListener('DOMContentLoaded', () => {
    loadPairedCameras();
    checkUrlHashForCamera();
    setupEventListeners();
    setupAudioVisualizer();
});

// =============================================================================
// Camera Credentials & URL Hash
// =============================================================================

function loadPairedCameras() {
    try {
        const data = localStorage.getItem(STORAGE_KEY);
        pairedCameras = data ? JSON.parse(data) : [];
    } catch (e) {
        pairedCameras = [];
    }
    updateCameraSelectDropdown();
}

function savePairedCameras() {
    try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(pairedCameras));
    } catch (_) {}
    updateCameraSelectDropdown();
}

function updateCameraSelectDropdown() {
    const select = document.getElementById('cameraSelect');
    select.innerHTML = '<option value="">-- 選擇已配對鏡頭 --</option>';

    pairedCameras.forEach((cam, index) => {
        const option = document.createElement('option');
        option.value = index;
        option.textContent = `${cam.name} (${cam.deviceId.substring(0, 8)}...)`;
        if (selectedCamera && selectedCamera.deviceId === cam.deviceId) {
            option.selected = true;
        }
        select.appendChild(option);
    });
}

function checkUrlHashForCamera() {
    const hash = window.location.hash.substring(1);
    if (!hash) {
        if (pairedCameras.length > 0) {
            selectCamera(pairedCameras[0]);
        }
        return;
    }

    const params = new URLSearchParams(hash);
    const id = params.get('id');
    const secret = params.get('secret');

    if (id && secret) {
        const urlCam = {
            name: params.get('name') || `Camera (${id.substring(0, 6)})`,
            deviceId: id,
            deviceSecret: secret,
            ip: params.get('ip') || '',
            ipv6: params.get('ipv6') || '',
            port: parseInt(params.get('port') || '8080', 10)
        };

        // Add or update in list
        const existingIndex = pairedCameras.findIndex(c => c.deviceId === id);
        if (existingIndex >= 0) {
            pairedCameras[existingIndex] = urlCam;
        } else {
            pairedCameras.unshift(urlCam);
        }
        savePairedCameras();
        selectCamera(urlCam);

        // Auto connect when opened from URL hash
        setTimeout(() => connectCurrentCamera(), 500);
    }
}

function selectCamera(cam) {
    selectedCamera = cam;
    document.getElementById('cameraNameBadge').textContent = cam.name;
    document.getElementById('placeholderTitle').textContent = cam.name;
    document.getElementById('placeholderDesc').textContent = `設備 ID: ${cam.deviceId}\n點擊下方按鈕開始 P2P 直連觀看。`;
    document.getElementById('cipherBadge').style.display = 'inline-flex';
    updateCameraSelectDropdown();
}

// =============================================================================
// WebRTC Connection Lifecycle
// =============================================================================

function connectCurrentCamera() {
    if (!selectedCamera) {
        openPairModal();
        return;
    }

    const videoElem = document.getElementById('remoteVideo');
    const placeholder = document.getElementById('videoPlaceholder');
    const liveBadge = document.getElementById('liveBadge');
    const statusBadge = document.getElementById('statusMessageBadge');
    const btnConnect = document.getElementById('btnConnectCurrent');
    const btnDisconnect = document.getElementById('btnDisconnect');

    btnConnect.disabled = true;
    btnConnect.innerHTML = '<span>⏳ 連線中...</span>';

    activeClient = new OcularWebRtcClient(selectedCamera, {
        onTrack: (stream, track) => {
            if (track.kind === 'video') {
                videoElem.srcObject = stream;
                placeholder.style.display = 'none';
                liveBadge.style.display = 'inline-flex';
            }
        },
        onConnectionStateChange: (state) => {
            if (state === 'connected') {
                placeholder.style.display = 'none';
                liveBadge.style.display = 'inline-flex';
                btnConnect.style.display = 'none';
                btnDisconnect.style.display = 'inline-flex';
                document.getElementById('telemetryStatus').textContent = '已連線';
            } else if (state === 'closed' || state === 'failed') {
                placeholder.style.display = 'flex';
                liveBadge.style.display = 'none';
                btnConnect.style.display = 'inline-flex';
                btnConnect.disabled = false;
                btnConnect.innerHTML = '<span>⚡ 開始連線</span>';
                btnDisconnect.style.display = 'none';
                document.getElementById('telemetryStatus').textContent = '已斷開';
            }
        },
        onStatusMessage: (msg) => {
            statusBadge.textContent = msg;
        },
        onStatsUpdate: (stats) => {
            document.getElementById('streamStatsBadge').textContent = `${stats.fps} FPS | ${stats.bitrateKbps} kbps`;
            document.getElementById('statFps').textContent = `${stats.fps} FPS`;
            document.getElementById('statRes').textContent = stats.resolution;
        },
        onDataChannelMessage: (json) => {
            handleTelemetryUpdate(json);
        }
    });

    activeClient.start().catch((err) => {
        statusBadge.textContent = "連線失敗: " + err.message;
        btnConnect.disabled = false;
        btnConnect.innerHTML = '<span>⚡ 重新連線</span>';
    });
}

function disconnectCurrentCamera() {
    if (activeClient) {
        activeClient.stop();
        activeClient = null;
    }
    const videoElem = document.getElementById('remoteVideo');
    videoElem.srcObject = null;
    document.getElementById('videoPlaceholder').style.display = 'flex';
    document.getElementById('liveBadge').style.display = 'none';
    document.getElementById('btnConnectCurrent').style.display = 'inline-flex';
    document.getElementById('btnConnectCurrent').disabled = false;
    document.getElementById('btnConnectCurrent').innerHTML = '<span>⚡ 開始連線</span>';
    document.getElementById('btnDisconnect').style.display = 'none';
    document.getElementById('statusMessageBadge').textContent = '已手動斷開';
    document.getElementById('telemetryStatus').textContent = '待機';
}

function handleTelemetryUpdate(json) {
    if (json.cpuUsage !== undefined) {
        document.getElementById('statCpu').textContent = `${json.cpuUsage}%`;
    }
    if (json.memoryUsage !== undefined) {
        document.getElementById('statRam').textContent = `${json.memoryUsage}%`;
    }
    if (json.batteryTemp !== undefined) {
        document.getElementById('statTemp').textContent = `${(json.batteryTemp / 10).toFixed(1)}°C`;
    }
    if (json.batteryPct !== undefined) {
        document.getElementById('statBattery').textContent = `${json.batteryPct}%`;
    }
    if (json.activeViewers !== undefined) {
        document.getElementById('statViewers').textContent = `${json.activeViewers}/4`;
    }
    if (json.isTorchOn !== undefined) {
        isTorchOn = json.isTorchOn;
        document.getElementById('torchStatus').textContent = isTorchOn ? "開" : "關";
    }
    if (json.nightVisionMode !== undefined) {
        nightVisionMode = json.nightVisionMode;
        document.getElementById('nightStatus').textContent = nightVisionMode === "auto" ? "自動" : (nightVisionMode === "force_on" ? "開啟" : "關閉");
    }
}

// =============================================================================
// UI Event Handlers
// =============================================================================

function setupEventListeners() {
    // Connect / Disconnect / Reconnect
    document.getElementById('btnConnectCurrent').addEventListener('click', connectCurrentCamera);
    document.getElementById('btnDisconnect').addEventListener('click', disconnectCurrentCamera);
    document.getElementById('btnReconnect').addEventListener('click', () => {
        disconnectCurrentCamera();
        setTimeout(connectCurrentCamera, 300);
    });

    // Camera Switcher
    document.getElementById('cameraSelect').addEventListener('change', (e) => {
        const index = e.target.value;
        if (index !== "" && pairedCameras[index]) {
            disconnectCurrentCamera();
            selectCamera(pairedCameras[index]);
        }
    });

    // Fullscreen & PiP
    document.getElementById('btnFullscreen').addEventListener('click', () => {
        const videoWrapper = document.getElementById('videoWrapper');
        if (!document.fullscreenElement) {
            videoWrapper.requestFullscreen().catch(() => {});
        } else {
            document.exitFullscreen().catch(() => {});
        }
    });

    document.getElementById('btnPip').addEventListener('click', async () => {
        const video = document.getElementById('remoteVideo');
        if (document.pictureInPictureElement) {
            await document.exitPictureInPicture().catch(() => {});
        } else if (document.pictureInPictureEnabled && video.readyState >= 2) {
            await video.requestPictureInPicture().catch(() => {});
        }
    });

    // Snapshot Capture
    document.getElementById('btnSnapshot').addEventListener('click', takeSnapshot);

    // Digital Zoom
    const zoomSlider = document.getElementById('digitalZoomSlider');
    const zoomVal = document.getElementById('zoomVal');
    const video = document.getElementById('remoteVideo');
    zoomSlider.addEventListener('input', (e) => {
        const val = parseFloat(e.target.value);
        zoomVal.textContent = val.toFixed(1) + 'x';
        video.style.transform = `scale(${val})`;
    });

    // Hardware Optical Zoom Slider
    const hwZoomSlider = document.getElementById('hardwareZoomSlider');
    const hwZoomVal = document.getElementById('hardwareZoomVal');
    hwZoomSlider.addEventListener('change', (e) => {
        const val = parseFloat(e.target.value);
        hwZoomVal.textContent = val.toFixed(1) + 'x';
        if (activeClient) {
            activeClient.sendControlCommand('zoom', val.toString());
        }
    });

    // DataChannel Buttons
    document.getElementById('btnTorchToggle').addEventListener('click', () => {
        isTorchOn = !isTorchOn;
        document.getElementById('torchStatus').textContent = isTorchOn ? "開" : "關";
        if (activeClient) {
            activeClient.sendControlCommand('torch', isTorchOn ? 'on' : 'off');
        }
    });

    document.getElementById('btnNightVision').addEventListener('click', () => {
        const modes = ["auto", "force_on", "off"];
        const nextIdx = (modes.indexOf(nightVisionMode) + 1) % modes.length;
        nightVisionMode = modes[nextIdx];
        document.getElementById('nightStatus').textContent = nightVisionMode === "auto" ? "自動" : (nightVisionMode === "force_on" ? "開啟" : "關閉");
        if (activeClient) {
            activeClient.sendControlCommand('night_vision', nightVisionMode);
        }
    });

    document.getElementById('btnSwitchCamera').addEventListener('click', () => {
        if (activeClient) {
            activeClient.sendControlCommand('switch_camera', 'toggle');
        }
    });

    document.getElementById('btnTriggerAlarm').addEventListener('click', () => {
        if (confirm("確定要在遠端鏡頭播放高分貝警報聲嗎？")) {
            if (activeClient) {
                activeClient.sendControlCommand('alarm', 'trigger');
            }
        }
    });

    // Push-to-Talk (PTT)
    const btnPtt = document.getElementById('btnPtt');
    const startPtt = async () => {
        if (!activeClient || !activeClient.isConnected) return;
        try {
            isPttActive = true;
            btnPtt.classList.add('active');
            btnPtt.innerHTML = '<span>🔴</span> 放開結束對講...';
            await activeClient.startAudioBroadcast();
        } catch (e) {
            alert("無法存取麥克風: " + e.message);
            stopPtt();
        }
    };

    const stopPtt = () => {
        if (!isPttActive) return;
        isPttActive = false;
        btnPtt.classList.remove('active');
        btnPtt.innerHTML = '<span>🎙️</span> 按住說話 (對講廣播)';
        if (activeClient) {
            activeClient.stopAudioBroadcast();
        }
    };

    btnPtt.addEventListener('mousedown', startPtt);
    btnPtt.addEventListener('mouseup', stopPtt);
    btnPtt.addEventListener('mouseleave', stopPtt);
    btnPtt.addEventListener('touchstart', (e) => { e.preventDefault(); startPtt(); });
    btnPtt.addEventListener('touchend', (e) => { e.preventDefault(); stopPtt(); });

    // Modals
    document.getElementById('btnOpenPairModal').addEventListener('click', openPairModal);
    document.getElementById('btnClosePairModal').addEventListener('click', closePairModal);
    document.getElementById('btnCancelPair').addEventListener('click', closePairModal);
    document.getElementById('btnSaveAndConnect').addEventListener('click', saveAndConnectFromModal);
    document.getElementById('btnParsePaste').addEventListener('click', parsePasteInput);

    // Modal Tabs
    document.getElementById('btnTabManual').addEventListener('click', () => {
        document.getElementById('tabContentManual').style.display = 'flex';
        document.getElementById('tabContentQrScan').style.display = 'none';
        document.getElementById('btnTabManual').classList.add('btn-primary');
        document.getElementById('btnTabQrScan').classList.remove('btn-primary');
        stopQrScanner();
    });

    document.getElementById('btnTabQrScan').addEventListener('click', () => {
        document.getElementById('tabContentManual').style.display = 'none';
        document.getElementById('tabContentQrScan').style.display = 'flex';
        document.getElementById('btnTabQrScan').classList.add('btn-primary');
        document.getElementById('btnTabManual').classList.remove('btn-primary');
        startQrScanner();
    });
}

// =============================================================================
// Snapshot Capture
// =============================================================================

function takeSnapshot() {
    const video = document.getElementById('remoteVideo');
    if (!video || video.videoWidth === 0) {
        alert("目前無可用的即時視訊畫面");
        return;
    }

    const canvas = document.createElement('canvas');
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    const ctx = canvas.getContext('2d');
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);

    const dataUrl = canvas.toDataURL('image/jpeg', 0.92);
    const link = document.createElement('a');
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
    link.download = `OcularNode_${selectedCamera ? selectedCamera.name : 'Snapshot'}_${timestamp}.jpg`;
    link.href = dataUrl;
    link.click();
}

// =============================================================================
// Audio Visualizer
// =============================================================================

function setupAudioVisualizer() {
    const canvas = document.getElementById('audioVisualizer');
    const ctx = canvas.getContext('2d');

    function drawIdle() {
        ctx.fillStyle = '#1E293B';
        ctx.fillRect(0, 0, canvas.width, canvas.height);

        ctx.strokeStyle = '#475569';
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.moveTo(0, canvas.height / 2);
        ctx.lineTo(canvas.width, canvas.height / 2);
        ctx.stroke();
    }
    drawIdle();
}

// =============================================================================
// Pairing Modal & QR Code Scanner
// =============================================================================

function openPairModal() {
    document.getElementById('pairModal').classList.add('active');
    if (selectedCamera) {
        document.getElementById('inputName').value = selectedCamera.name;
        document.getElementById('inputId').value = selectedCamera.deviceId;
        document.getElementById('inputSecret').value = selectedCamera.deviceSecret;
        document.getElementById('inputIp').value = selectedCamera.ip || '';
        document.getElementById('inputPort').value = selectedCamera.port || 8080;
    }
}

function closePairModal() {
    document.getElementById('pairModal').classList.remove('active');
    stopQrScanner();
}

function parsePasteInput() {
    const text = document.getElementById('pasteInput').value.trim();
    if (!text) return;

    try {
        if (text.startsWith('{')) {
            // JSON Format
            const json = JSON.parse(text);
            if (json.name) document.getElementById('inputName').value = json.name;
            if (json.deviceId) document.getElementById('inputId').value = json.deviceId;
            if (json.deviceSecret) document.getElementById('inputSecret').value = json.deviceSecret;
            if (json.ipAddress || json.ip) document.getElementById('inputIp').value = json.ipAddress || json.ip;
            if (json.port) document.getElementById('inputPort').value = json.port;
        } else if (text.includes('#') || text.includes('?')) {
            // URL or Hash Format
            const queryString = text.includes('#') ? text.split('#')[1] : text.split('?')[1];
            const params = new URLSearchParams(queryString);
            if (params.get('name')) document.getElementById('inputName').value = params.get('name');
            if (params.get('id')) document.getElementById('inputId').value = params.get('id');
            if (params.get('secret')) document.getElementById('inputSecret').value = params.get('secret');
            if (params.get('ip')) document.getElementById('inputIp').value = params.get('ip');
            if (params.get('port')) document.getElementById('inputPort').value = params.get('port');
        }
    } catch (e) {
        alert("無法解析字串格式: " + e.message);
    }
}

function saveAndConnectFromModal() {
    const name = document.getElementById('inputName').value.trim() || 'Camera';
    const deviceId = document.getElementById('inputId').value.trim();
    const deviceSecret = document.getElementById('inputSecret').value.trim();
    const ip = document.getElementById('inputIp').value.trim();
    const port = parseInt(document.getElementById('inputPort').value.trim() || '8080', 10);

    if (!deviceId || !deviceSecret) {
        alert("請填寫設備 ID 與 E2EE 加密金鑰");
        return;
    }

    const cam = { name, deviceId, deviceSecret, ip, port };

    const idx = pairedCameras.findIndex(c => c.deviceId === deviceId);
    if (idx >= 0) {
        pairedCameras[idx] = cam;
    } else {
        pairedCameras.unshift(cam);
    }

    savePairedCameras();
    selectCamera(cam);
    closePairModal();
    connectCurrentCamera();
}

function startQrScanner() {
    if (typeof Html5Qrcode === 'undefined') {
        alert("QR Code 掃描庫載入中，請稍候再試");
        return;
    }

    html5QrCode = new Html5Qrcode("qrReader");
    html5QrCode.start(
        { facingMode: "environment" },
        { fps: 10, qrbox: 250 },
        (decodedText) => {
            console.log("QR Code scanned:", decodedText);
            document.getElementById('pasteInput').value = decodedText;
            parsePasteInput();
            document.getElementById('btnTabManual').click();
            stopQrScanner();
        },
        () => {}
    ).catch((err) => {
        console.warn("Unable to start webcam scanner:", err);
    });
}

function stopQrScanner() {
    if (html5QrCode) {
        try {
            html5QrCode.stop().then(() => {
                html5QrCode.clear();
                html5QrCode = null;
            }).catch(() => {});
        } catch (_) {}
    }
}
