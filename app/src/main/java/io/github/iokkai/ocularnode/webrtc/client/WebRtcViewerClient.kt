package io.github.iokkai.ocularnode.webrtc.client

import android.content.Context
import android.util.Log
import io.github.iokkai.ocularnode.util.NetworkUtils
import io.github.iokkai.ocularnode.webrtc.WebRtcPeerConnection
import io.github.iokkai.ocularnode.webrtc.WebRtcSessionManager
import io.github.iokkai.ocularnode.webrtc.datachannel.DataChannelCommand
import io.github.iokkai.ocularnode.webrtc.datachannel.WebRtcDataChannel
import io.github.iokkai.ocularnode.webrtc.signaling.SignalingPayload
import io.github.iokkai.ocularnode.webrtc.signaling.SignalingType
import io.github.iokkai.ocularnode.webrtc.signaling.SmartSignalingRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import java.util.UUID

/**
 * Viewer Node WebRTC Client orchestrating signaling negotiation, PeerConnection establishment,
 * remote video track rendering, bidirectional DataChannel control, ICE Restart roaming,
 * and a 3-Level Self-Healing Watchdog (Level 1: Native multipath, Level 2: ICE Restart, Level 3: Hard reset after 10s).
 */
class WebRtcViewerClient(
    private val sessionManager: WebRtcSessionManager
) {

    companion object {
        private const val TAG = "WebRtcViewerClient"
        const val LEVEL_2_DISCONNECT_THRESHOLD_MS = 2500L
        const val LEVEL_3_HARD_RESET_THRESHOLD_MS = 10_000L
    }

    private var currentScope: CoroutineScope? = null
    private var signalingRouter: SmartSignalingRouter? = null
    private var peerConnection: WebRtcPeerConnection? = null
    private var dataChannel: WebRtcDataChannel? = null
    private var networkMonitoringJob: Job? = null

    // 3-Level Watchdog Timers
    private var level2WatchdogJob: Job? = null
    private var level3WatchdogJob: Job? = null

    private data class ConnectParams(
        val channelKey: String,
        val secret: String,
        val context: Context?,
        val cameraLocalIp: String?,
        val cameraPort: Int,
        val telegramBotToken: String?,
        val telegramChatId: String?,
        val cameraIpv6: String?
    )
    private var lastConnectParams: ConnectParams? = null

    val viewerSessionId: String = "viewer-" + UUID.randomUUID().toString().take(8)

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _isRoaming = MutableStateFlow(false)
    val isRoaming: StateFlow<Boolean> = _isRoaming.asStateFlow()

    private val _statusMessage = MutableStateFlow("Unconnected")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _incomingDataCommands = MutableStateFlow<DataChannelCommand?>(null)
    val incomingDataCommands: StateFlow<DataChannelCommand?> = _incomingDataCommands.asStateFlow()

    val isDataChannelOpen: Boolean
        get() = dataChannel?.isOpen == true

    fun connect(
        scope: CoroutineScope,
        channelKey: String,
        secret: String,
        context: Context? = null,
        cameraLocalIp: String? = null,
        cameraPort: Int = 8080,
        telegramBotToken: String? = null,
        telegramChatId: String? = null,
        cameraIpv6: String? = null
    ) {
        lastConnectParams = ConnectParams(
            channelKey, secret, context, cameraLocalIp, cameraPort, telegramBotToken, telegramChatId, cameraIpv6
        )
        connectInternal(scope, channelKey, secret, context, cameraLocalIp, cameraPort, telegramBotToken, telegramChatId, cameraIpv6)
    }

    private fun connectInternal(
        scope: CoroutineScope,
        channelKey: String,
        secret: String,
        context: Context?,
        cameraLocalIp: String?,
        cameraPort: Int,
        telegramBotToken: String?,
        telegramChatId: String?,
        cameraIpv6: String?
    ) {
        disconnectInternal(keepLastParams = true)

        this.currentScope = scope
        _isConnecting.value = true
        _statusMessage.value = "Connecting to camera..."

        val router = SmartSignalingRouter(
            channelKey = channelKey,
            secret = secret,
            cameraLocalIp = cameraLocalIp,
            cameraPort = cameraPort,
            telegramBotToken = telegramBotToken,
            telegramChatId = telegramChatId,
            cameraIpv6 = cameraIpv6
        )
        this.signalingRouter = router

        val peerConn = WebRtcPeerConnection(
            sessionManager = sessionManager,
            onRemoteTrack = { track ->
                if (track.kind() == MediaStreamTrack.VIDEO_TRACK_KIND && track is VideoTrack) {
                    Log.i(TAG, "Received remote VideoTrack")
                    _remoteVideoTrack.value = track
                }
            },
            onRemoteDataChannel = { remoteChan ->
                Log.i(TAG, "Received remote DataChannel in Viewer: ${remoteChan.label()}")
                val peer = peerConnection?.peerConnection ?: return@WebRtcPeerConnection
                val dc = WebRtcDataChannel(peer, isInitiator = false, label = remoteChan.label())
                dc.attachRemoteDataChannel(remoteChan)
                this.dataChannel = dc
                scope.launch {
                    dc.incomingCommands.collect { cmd ->
                        Log.d(TAG, "Viewer received DataChannel command/telemetry: ${cmd.action}")
                        _incomingDataCommands.value = cmd
                    }
                }
            }
        )
        this.peerConnection = peerConn

        // Setup Three-Level Watchdog System
        setupWatchdog(scope, peerConn)

        // Collect and send local ICE candidates
        scope.launch {
            peerConn.iceCandidateFlow.collect { candidate ->
                val candidatePayload = SignalingPayload.createCandidate(
                    senderId = viewerSessionId,
                    sessionId = viewerSessionId,
                    candidate = candidate.sdp,
                    sdpMid = candidate.sdpMid,
                    sdpMLineIndex = candidate.sdpMLineIndex
                )
                router.dispatchMessage(candidatePayload)
            }
        }

        // Start network roaming monitoring flow (reusing NetworkUtils.observeNetworkStatus)
        if (context != null) {
            networkMonitoringJob = scope.launch(Dispatchers.IO) {
                var isInitial = true
                NetworkUtils.observeNetworkStatus(context).collect { ipInfo ->
                    if (isInitial) {
                        isInitial = false
                        return@collect
                    }

                    // Network interface changed or IP changed
                    if (_isConnected.value || _isConnecting.value) {
                        Log.i(TAG, "Network change / roaming detected (IPv4: ${ipInfo.localIp}, IPv6: ${ipInfo.ipv6GlobalAddress}). Initiating ICE Restart...")
                        triggerIceRestart()
                    }
                }
            }
        }

        // Start listening to incoming signaling messages
        scope.launch(Dispatchers.IO) {
            router.startListening(scope) { payload, _ ->
                // Filter out messages not targeted to this viewer session
                if (payload.sessionId == viewerSessionId || payload.targetId == viewerSessionId) {
                    handleIncomingSignal(payload)
                }
            }

            // Send initial stream request
            val requestStreamPayload = SignalingPayload.createRequestStream(
                senderId = viewerSessionId,
                sessionId = viewerSessionId
            )
            router.dispatchMessage(requestStreamPayload)
            Log.i(TAG, "Dispatched initial REQUEST_STREAM for session $viewerSessionId")
        }
    }

    /**
     * Three-Level Watchdog Self-Healing Logic:
     * - Level 1: Tracks native multipath & candidate pair updates
     * - Level 2: Triggers fast ICE Restart on FAILED or DISCONNECTED > 2.5s
     * - Level 3: Performs hard PeerConnection reset after 10s persistent disconnection
     */
    private fun setupWatchdog(scope: CoroutineScope, peerConn: WebRtcPeerConnection) {
        // Monitor PeerConnection state
        scope.launch {
            peerConn.connectionState.collect { state ->
                Log.i(TAG, "Viewer PeerConnection state: $state")
                when (state) {
                    PeerConnection.PeerConnectionState.CONNECTED -> {
                        cancelWatchdogTimers()
                        _isConnected.value = true
                        _isConnecting.value = false
                        _isRoaming.value = false
                        _statusMessage.value = "Connected (P2P Stream Active)"
                    }
                    PeerConnection.PeerConnectionState.CONNECTING -> {
                        if (!_isRoaming.value) {
                            _statusMessage.value = "Establishing P2P tunnel..."
                        }
                    }
                    PeerConnection.PeerConnectionState.DISCONNECTED -> {
                        _statusMessage.value = "Connection lost, reconnecting..."
                        startLevel2WatchdogTimer(scope)
                        startLevel3WatchdogTimer(scope)
                    }
                    PeerConnection.PeerConnectionState.FAILED -> {
                        _isConnected.value = false
                        _isConnecting.value = false
                        _isRoaming.value = false
                        _statusMessage.value = "Connection failed, attempting self-healing..."
                        // Immediate Level 2 ICE Restart on failure
                        triggerIceRestart()
                        startLevel3WatchdogTimer(scope)
                    }
                    else -> {}
                }
            }
        }

        // Level 1 Watchdog: Monitor ICE Connection State (WebRTC native multipath handling)
        scope.launch {
            peerConn.iceConnectionState.collect { iceState ->
                Log.d(TAG, "Level 1 Watchdog: ICE connection state: $iceState")
                when (iceState) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        // Level 1: Multipath candidate pair active and healthy
                        cancelWatchdogTimers()
                        _isConnected.value = true
                        _isConnecting.value = false
                        _isRoaming.value = false
                        _statusMessage.value = "Connected (P2P Stream Active)"
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        startLevel2WatchdogTimer(scope)
                        startLevel3WatchdogTimer(scope)
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        Log.w(TAG, "Level 2 Watchdog: ICE FAILED. Triggering immediate ICE Restart...")
                        triggerIceRestart()
                        startLevel3WatchdogTimer(scope)
                    }
                    else -> {}
                }
            }
        }
    }

    private fun cancelWatchdogTimers() {
        level2WatchdogJob?.cancel()
        level2WatchdogJob = null
        level3WatchdogJob?.cancel()
        level3WatchdogJob = null
    }

    private fun startLevel2WatchdogTimer(scope: CoroutineScope) {
        if (level2WatchdogJob?.isActive == true) return

        level2WatchdogJob = scope.launch(Dispatchers.IO) {
            delay(LEVEL_2_DISCONNECT_THRESHOLD_MS)
            if (isActive && !_isConnected.value) {
                Log.w(TAG, "Level 2 Watchdog: Disconnected > ${LEVEL_2_DISCONNECT_THRESHOLD_MS}ms. Triggering ICE Restart...")
                triggerIceRestart()
            }
        }
    }

    private fun startLevel3WatchdogTimer(scope: CoroutineScope) {
        if (level3WatchdogJob?.isActive == true) return

        level3WatchdogJob = scope.launch(Dispatchers.IO) {
            delay(LEVEL_3_HARD_RESET_THRESHOLD_MS)
            if (isActive && !_isConnected.value) {
                Log.e(TAG, "Level 3 Watchdog: Disconnected > ${LEVEL_3_HARD_RESET_THRESHOLD_MS}ms. Executing Hard Reset and full reconnection...")
                reconnectFull()
            }
        }
    }

    /**
     * Level 3 Watchdog Action: Destroys the stale PeerConnection and executes a complete fresh handshake.
     */
    fun reconnectFull() {
        val params = lastConnectParams ?: return
        val scope = currentScope ?: return

        _statusMessage.value = "連線逾時，深度自癒重新握手中..."
        Log.i(TAG, "Level 3 Watchdog: Reconnecting session $viewerSessionId from scratch")

        connectInternal(
            scope = scope,
            channelKey = params.channelKey,
            secret = params.secret,
            context = params.context,
            cameraLocalIp = params.cameraLocalIp,
            cameraPort = params.cameraPort,
            telegramBotToken = params.telegramBotToken,
            telegramChatId = params.telegramChatId,
            cameraIpv6 = params.cameraIpv6
        )
    }

    /**
     * Level 2 Watchdog Action: Triggers WebRTC ICE Restart without tearing down the media pipeline.
     */
    fun triggerIceRestart() {
        val peerConn = peerConnection ?: return
        val scope = currentScope ?: return
        val router = signalingRouter ?: return

        _isRoaming.value = true
        _statusMessage.value = "網路漫遊切換中，重新連線中..."
        Log.i(TAG, "Executing ICE Restart for session $viewerSessionId")

        peerConn.restartIce()

        scope.launch(Dispatchers.IO) {
            try {
                val offer = peerConn.createOffer(iceRestart = true)
                peerConn.setLocalDescription(offer)

                val offerPayload = SignalingPayload.createOffer(
                    senderId = viewerSessionId,
                    sessionId = viewerSessionId,
                    sdp = offer.description
                )
                router.dispatchMessage(offerPayload)
                Log.i(TAG, "Dispatched ICE restart Offer to camera for session $viewerSessionId")
            } catch (e: Exception) {
                Log.e(TAG, "Error executing ICE restart offer", e)
            }
        }
    }

    private fun handleIncomingSignal(payload: SignalingPayload) {
        val peerConn = peerConnection ?: return
        val scope = currentScope ?: return

        when (payload.type) {
            SignalingType.OFFER -> {
                val sdpStr = payload.sdp ?: return
                scope.launch(Dispatchers.IO) {
                    try {
                        val offer = SessionDescription(SessionDescription.Type.OFFER, sdpStr)
                        peerConn.setRemoteDescription(offer)

                        val answer = peerConn.createAnswer()
                        peerConn.setLocalDescription(answer)

                        val answerPayload = SignalingPayload.createAnswer(
                            senderId = viewerSessionId,
                            sessionId = viewerSessionId,
                            sdp = answer.description,
                            targetId = payload.senderId
                        )
                        signalingRouter?.dispatchMessage(answerPayload)
                        Log.i(TAG, "Responded with SDP Answer to session $viewerSessionId")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error handling remote Offer in Viewer", e)
                    }
                }
            }
            SignalingType.ANSWER -> {
                val sdpStr = payload.sdp ?: return
                scope.launch(Dispatchers.IO) {
                    try {
                        val answer = SessionDescription(SessionDescription.Type.ANSWER, sdpStr)
                        peerConn.setRemoteDescription(answer)
                        Log.i(TAG, "Applied remote Answer in Viewer (ICE Restart/Renegotiation complete)")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error applying remote Answer in Viewer", e)
                    }
                }
            }
            SignalingType.CANDIDATE -> {
                val candidateStr = payload.candidate ?: return
                try {
                    val candidate = IceCandidate(
                        payload.sdpMid ?: "0",
                        payload.sdpMLineIndex ?: 0,
                        candidateStr
                    )
                    peerConn.addIceCandidate(candidate)
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding remote candidate in Viewer", e)
                }
            }
            SignalingType.BYE -> {
                disconnect()
            }
            else -> {}
        }
    }

    /**
     * Sends a control command via WebRTC DataChannel.
     */
    fun sendControlCommand(command: DataChannelCommand): Boolean {
        return dataChannel?.sendCommand(command) ?: false
    }

    /**
     * Sends a legacy (command, value) pair via WebRTC DataChannel.
     */
    fun sendControlCommand(command: String, value: String): Boolean {
        val dcCmd = DataChannelCommand.fromLegacy(command, value)
        return sendControlCommand(dcCmd)
    }

    fun toggleTorch(enable: Boolean? = null): Boolean {
        val value = if (enable != null) (if (enable) "on" else "off") else "toggle"
        return sendControlCommand(DataChannelCommand.ACTION_TORCH_TOGGLE, value)
    }

    fun switchCamera(): Boolean {
        return sendControlCommand(DataChannelCommand.ACTION_SWITCH_CAMERA, "")
    }

    fun setNightVision(mode: String): Boolean {
        return sendControlCommand(DataChannelCommand.ACTION_NIGHT_VISION, mode)
    }

    fun setResolution(resolution: String): Boolean {
        return sendControlCommand(DataChannelCommand.ACTION_RESOLUTION, resolution)
    }

    fun setQuality(quality: Int): Boolean {
        return sendControlCommand(DataChannelCommand.ACTION_QUALITY, quality.toString())
    }

    fun setFps(fps: Int): Boolean {
        return sendControlCommand(DataChannelCommand.ACTION_FPS, fps.toString())
    }

    fun setRotation(rotation: String): Boolean {
        return sendControlCommand(DataChannelCommand.ACTION_ROTATION, rotation)
    }

    fun setMotionDetection(enabled: Boolean): Boolean {
        return sendControlCommand(DataChannelCommand.ACTION_MOTION_TOGGLE, enabled.toString())
    }

    fun playAlarm(): Boolean {
        return sendControlCommand(DataChannelCommand.ACTION_PLAY_ALARM, "")
    }

    fun setZoom(zoomRatio: Float): Boolean {
        return sendControlCommand(DataChannelCommand(DataChannelCommand.ACTION_PTZ_ZOOM, mapOf("zoom" to zoomRatio)))
    }

    fun disconnect() {
        disconnectInternal(keepLastParams = false)
    }

    private fun disconnectInternal(keepLastParams: Boolean) {
        cancelWatchdogTimers()

        networkMonitoringJob?.cancel()
        networkMonitoringJob = null

        dataChannel?.close()
        dataChannel = null

        peerConnection?.close()
        peerConnection = null

        signalingRouter?.close()
        signalingRouter = null

        if (!keepLastParams) {
            lastConnectParams = null
        }

        _remoteVideoTrack.value = null
        _isConnected.value = false
        _isConnecting.value = false
        _isRoaming.value = false
        _statusMessage.value = "Disconnected"
        Log.i(TAG, "WebRtcViewerClient disconnected (keepParams: $keepLastParams)")
    }
}
