package io.github.iokkai.ocularnode.webrtc.server

import android.content.Context
import android.util.Log
import io.github.iokkai.ocularnode.webrtc.WebRtcPeerConnection
import io.github.iokkai.ocularnode.webrtc.WebRtcSessionManager
import io.github.iokkai.ocularnode.webrtc.audio.WebRtcAudioSource
import io.github.iokkai.ocularnode.webrtc.camera.WebRtcCameraCapturer
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
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import java.util.concurrent.ConcurrentHashMap

/**
 * Camera Node WebRTC orchestrator managing hardware video capture, audio engine,
 * multi-viewer sessions, thermal throttling adjustments, and signaling negotiation.
 */
class WebRtcCameraServerManager(
    private val context: Context,
    private val sessionManager: WebRtcSessionManager,
    private val scope: CoroutineScope,
    val deviceId: String,
    val secret: String,
    val onDataCommandReceived: ((DataChannelCommand) -> Unit)? = null
) {

    companion object {
        private const val TAG = "WebRtcCameraServer"
        const val MAX_CONCURRENT_VIEWERS = 4

        // Bitrate profiles (bps)
        const val BITRATE_HIGH = 1_500_000 // 1.5 Mbps
        const val BITRATE_NORMAL = 900_000  // 900 Kbps
        const val BITRATE_THROTTLED = 350_000 // 350 Kbps (when battery temp > 42°C)
    }

    val cameraCapturer = WebRtcCameraCapturer(context, sessionManager)
    val audioSource = WebRtcAudioSource(sessionManager)

    private val activeViewers = ConcurrentHashMap<String, WebRtcPeerConnection>()
    private val activeDataChannels = ConcurrentHashMap<String, WebRtcDataChannel>()

    private val _activeViewerCount = MutableStateFlow(0)
    val activeViewerCount: StateFlow<Int> = _activeViewerCount.asStateFlow()

    private var signalingRouter: SmartSignalingRouter? = null

    fun start(
        router: SmartSignalingRouter,
        width: Int = 1280,
        height: Int = 720,
        fps: Int = 30,
        useFrontCamera: Boolean = false
    ) {
        this.signalingRouter = router

        // Start hardware camera capture and audio
        cameraCapturer.startCapture(width, height, fps, useFrontCamera)
        audioSource.start()

        // Start listening to incoming signaling requests
        scope.launch(Dispatchers.IO) {
            router.startListening(scope) { payload, _ ->
                handleIncomingSignal(payload)
            }
        }
        Log.i(TAG, "WebRtcCameraServerManager started for device $deviceId")
    }

    /**
     * Handles dynamic thermal throttling: reduces video bitrate and frame rate
     * when device temperature exceeds safe thresholds.
     */
    fun applyThermalThrottle(isThrottled: Boolean) {
        val targetBitrate = if (isThrottled) BITRATE_THROTTLED else BITRATE_NORMAL
        val targetFps = if (isThrottled) 15 else 30
        Log.i(TAG, "Applying thermal throttle state: $isThrottled (Target bitrate: $targetBitrate bps, FPS: $targetFps)")

        cameraCapturer.changeCaptureFormat(
            if (isThrottled) 640 else 1280,
            if (isThrottled) 360 else 720,
            targetFps
        )

        activeViewers.values.forEach { viewerConnection ->
            try {
                viewerConnection.peerConnection.senders.forEach { sender ->
                    if (sender.track()?.kind() == "video") {
                        val parameters = sender.parameters
                        parameters.encodings.forEach { encoding ->
                            encoding.maxBitrateBps = targetBitrate
                            encoding.minBitrateBps = 100_000
                        }
                        sender.parameters = parameters
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error adjusting bitrate for viewer: ${e.message}")
            }
        }
    }

    private fun handleIncomingSignal(payload: SignalingPayload) {
        val sessionId = payload.sessionId
        if (sessionId.isBlank()) return

        when (payload.type) {
            SignalingType.REQUEST_STREAM -> {
                Log.i(TAG, "Received REQUEST_STREAM from ${payload.senderId} (session: $sessionId)")
                handleRequestStream(payload)
            }
            SignalingType.OFFER -> {
                Log.i(TAG, "Received OFFER from ${payload.senderId} (session: $sessionId)")
                handleRemoteOffer(payload)
            }
            SignalingType.ANSWER -> {
                Log.i(TAG, "Received ANSWER from ${payload.senderId} (session: $sessionId)")
                handleRemoteAnswer(payload)
            }
            SignalingType.CANDIDATE -> {
                handleRemoteCandidate(payload)
            }
            SignalingType.BYE -> {
                Log.i(TAG, "Received BYE from session: $sessionId, closing session")
                closeViewerSession(sessionId)
            }
            else -> {}
        }
    }

    private fun handleRequestStream(payload: SignalingPayload) {
        if (activeViewers.size >= MAX_CONCURRENT_VIEWERS) {
            Log.w(TAG, "Max concurrent viewers reached (${activeViewers.size}/$MAX_CONCURRENT_VIEWERS)")
            return
        }

        val sessionId = payload.sessionId
        closeViewerSession(sessionId)

        val peerConn = WebRtcPeerConnection(sessionManager)
        activeViewers[sessionId] = peerConn
        _activeViewerCount.value = activeViewers.size

        // Add local camera and audio tracks
        cameraCapturer.videoTrack?.let { peerConn.peerConnection.addTrack(it) }
        audioSource.audioTrack?.let { peerConn.peerConnection.addTrack(it) }

        // Create DataChannel on Camera side
        val dataChan = WebRtcDataChannel(peerConn.peerConnection, isInitiator = true)
        activeDataChannels[sessionId] = dataChan
        scope.launch {
            dataChan.incomingCommands.collect { cmd ->
                onDataCommandReceived?.invoke(cmd)
            }
        }

        // Listen and forward local ICE candidates
        scope.launch {
            peerConn.iceCandidateFlow.collect { candidate ->
                val candidatePayload = SignalingPayload.createCandidate(
                    senderId = deviceId,
                    sessionId = sessionId,
                    candidate = candidate.sdp,
                    sdpMid = candidate.sdpMid,
                    sdpMLineIndex = candidate.sdpMLineIndex,
                    targetId = payload.senderId
                )
                signalingRouter?.dispatchMessage(candidatePayload)
            }
        }

        // Create and send SDP Offer
        scope.launch(Dispatchers.IO) {
            try {
                val offer = peerConn.createOffer()
                peerConn.setLocalDescription(offer)

                val offerPayload = SignalingPayload.createOffer(
                    senderId = deviceId,
                    sessionId = sessionId,
                    sdp = offer.description,
                    targetId = payload.senderId
                )
                signalingRouter?.dispatchMessage(offerPayload)
                Log.i(TAG, "Generated and sent SDP Offer to session: $sessionId")
            } catch (e: Exception) {
                Log.e(TAG, "Error generating SDP offer for session $sessionId", e)
            }
        }
    }

    private fun handleRemoteAnswer(payload: SignalingPayload) {
        val sessionId = payload.sessionId
        val peerConn = activeViewers[sessionId] ?: return
        val sdpStr = payload.sdp ?: return

        scope.launch(Dispatchers.IO) {
            try {
                val sdp = SessionDescription(SessionDescription.Type.ANSWER, sdpStr)
                peerConn.setRemoteDescription(sdp)
                Log.i(TAG, "Applied remote SDP Answer for session: $sessionId")
            } catch (e: Exception) {
                Log.e(TAG, "Error applying remote SDP Answer for session $sessionId", e)
            }
        }
    }

    private fun handleRemoteOffer(payload: SignalingPayload) {
        val sessionId = payload.sessionId
        val sdpStr = payload.sdp ?: return

        val existingConn = activeViewers[sessionId]
        val peerConn = if (existingConn != null) {
            Log.i(TAG, "Reusing existing PeerConnection for session $sessionId (ICE Restart / Renegotiation)")
            existingConn
        } else {
            val newConn = WebRtcPeerConnection(sessionManager)
            activeViewers[sessionId] = newConn
            _activeViewerCount.value = activeViewers.size

            cameraCapturer.videoTrack?.let { newConn.peerConnection.addTrack(it) }
            audioSource.audioTrack?.let { newConn.peerConnection.addTrack(it) }
            newConn
        }

        scope.launch(Dispatchers.IO) {
            try {
                val offer = SessionDescription(SessionDescription.Type.OFFER, sdpStr)
                peerConn.setRemoteDescription(offer)

                val answer = peerConn.createAnswer()
                peerConn.setLocalDescription(answer)

                val answerPayload = SignalingPayload.createAnswer(
                    senderId = deviceId,
                    sessionId = sessionId,
                    sdp = answer.description,
                    targetId = payload.senderId
                )
                signalingRouter?.dispatchMessage(answerPayload)
                Log.i(TAG, "Sent Answer for offer/ICE restart to session $sessionId")
            } catch (e: Exception) {
                Log.e(TAG, "Error processing remote offer for session $sessionId", e)
            }
        }
    }

    private fun handleRemoteCandidate(payload: SignalingPayload) {
        val sessionId = payload.sessionId
        val peerConn = activeViewers[sessionId] ?: return
        val candidateStr = payload.candidate ?: return

        try {
            val candidate = IceCandidate(
                payload.sdpMid ?: "0",
                payload.sdpMLineIndex ?: 0,
                candidateStr
            )
            peerConn.addIceCandidate(candidate)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding remote candidate for session $sessionId", e)
        }
    }

    fun closeViewerSession(sessionId: String) {
        activeDataChannels.remove(sessionId)?.close()
        activeViewers.remove(sessionId)?.close()
        _activeViewerCount.value = activeViewers.size
    }

    fun stop() {
        activeViewers.keys().toList().forEach { closeViewerSession(it) }
        cameraCapturer.stopCapture()
        audioSource.dispose()
        signalingRouter?.close()
        signalingRouter = null
        Log.i(TAG, "WebRtcCameraServerManager stopped")
    }
}
