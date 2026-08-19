package io.github.iokkai.ocularnode.webrtc.client

import android.util.Log
import io.github.iokkai.ocularnode.webrtc.WebRtcPeerConnection
import io.github.iokkai.ocularnode.webrtc.WebRtcSessionManager
import io.github.iokkai.ocularnode.webrtc.datachannel.DataChannelCommand
import io.github.iokkai.ocularnode.webrtc.datachannel.WebRtcDataChannel
import io.github.iokkai.ocularnode.webrtc.signaling.SignalingPayload
import io.github.iokkai.ocularnode.webrtc.signaling.SignalingType
import io.github.iokkai.ocularnode.webrtc.signaling.SmartSignalingRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import java.util.UUID

/**
 * Viewer Node WebRTC Client orchestrating signaling negotiation, PeerConnection establishment,
 * remote video track rendering, and bidirectional DataChannel control.
 */
class WebRtcViewerClient(
    private val sessionManager: WebRtcSessionManager
) {

    companion object {
        private const val TAG = "WebRtcViewerClient"
    }

    private var currentScope: CoroutineScope? = null
    private var signalingRouter: SmartSignalingRouter? = null
    private var peerConnection: WebRtcPeerConnection? = null
    private var dataChannel: WebRtcDataChannel? = null

    val viewerSessionId: String = "viewer-" + UUID.randomUUID().toString().take(8)

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _statusMessage = MutableStateFlow("Unconnected")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _incomingDataCommands = MutableStateFlow<DataChannelCommand?>(null)
    val incomingDataCommands: StateFlow<DataChannelCommand?> = _incomingDataCommands.asStateFlow()

    fun connect(
        scope: CoroutineScope,
        channelKey: String,
        secret: String,
        cameraLocalIp: String? = null,
        cameraPort: Int = 8080,
        telegramBotToken: String? = null,
        telegramChatId: String? = null
    ) {
        disconnect()

        this.currentScope = scope
        _isConnecting.value = true
        _statusMessage.value = "Connecting to camera..."

        val router = SmartSignalingRouter(
            channelKey = channelKey,
            secret = secret,
            cameraLocalIp = cameraLocalIp,
            cameraPort = cameraPort,
            telegramBotToken = telegramBotToken,
            telegramChatId = telegramChatId
        )
        this.signalingRouter = router

        val peerConn = WebRtcPeerConnection(
            sessionManager = sessionManager,
            onRemoteTrack = { track ->
                if (track.kind() == MediaStreamTrack.VIDEO_TRACK_KIND && track is VideoTrack) {
                    Log.i(TAG, "Received remote VideoTrack")
                    _remoteVideoTrack.value = track
                }
            }
        )
        this.peerConnection = peerConn

        // Monitor connection states
        scope.launch {
            peerConn.connectionState.collect { state ->
                Log.i(TAG, "Viewer PeerConnection state: $state")
                when (state) {
                    PeerConnection.PeerConnectionState.CONNECTED -> {
                        _isConnected.value = true
                        _isConnecting.value = false
                        _statusMessage.value = "Connected (P2P Stream Active)"
                    }
                    PeerConnection.PeerConnectionState.CONNECTING -> {
                        _statusMessage.value = "Establishing P2P tunnel..."
                    }
                    PeerConnection.PeerConnectionState.DISCONNECTED -> {
                        _statusMessage.value = "Connection lost, reconnecting..."
                    }
                    PeerConnection.PeerConnectionState.FAILED -> {
                        _isConnected.value = false
                        _isConnecting.value = false
                        _statusMessage.value = "Connection failed"
                    }
                    else -> {}
                }
            }
        }

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

        // Start listening to incoming signaling messages
        scope.launch(Dispatchers.IO) {
            router.startListening(scope) { payload, channelType ->
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
                        Log.i(TAG, "Applied remote Answer in Viewer")
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

    fun sendControlCommand(command: DataChannelCommand): Boolean {
        return dataChannel?.sendCommand(command) ?: false
    }

    fun toggleTorch(): Boolean {
        return sendControlCommand(DataChannelCommand(DataChannelCommand.ACTION_TORCH_TOGGLE))
    }

    fun switchCamera(): Boolean {
        return sendControlCommand(DataChannelCommand(DataChannelCommand.ACTION_SWITCH_CAMERA))
    }

    fun disconnect() {
        dataChannel?.close()
        dataChannel = null

        peerConnection?.close()
        peerConnection = null

        signalingRouter?.close()
        signalingRouter = null

        _remoteVideoTrack.value = null
        _isConnected.value = false
        _isConnecting.value = false
        _statusMessage.value = "Disconnected"
        Log.i(TAG, "WebRtcViewerClient disconnected")
    }
}
