package io.github.iokkai.ocularnode.webrtc

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Encapsulates a WebRTC PeerConnection session with coroutine-friendly SDP negotiation,
 * ICE candidate collection flows, and connection state management.
 */
class WebRtcPeerConnection(
    private val sessionManager: WebRtcSessionManager,
    private val iceServers: List<PeerConnection.IceServer> = sessionManager.getDefaultIceServers(),
    private val onRemoteTrack: ((MediaStreamTrack) -> Unit)? = null,
    private val onRemoteDataChannel: ((DataChannel) -> Unit)? = null
) {

    companion object {
        private const val TAG = "WebRtcPeerConnection"
    }

    private val _connectionState = MutableStateFlow(PeerConnection.PeerConnectionState.NEW)
    val connectionState: StateFlow<PeerConnection.PeerConnectionState> = _connectionState.asStateFlow()

    private val _iceConnectionState = MutableStateFlow(PeerConnection.IceConnectionState.NEW)
    val iceConnectionState: StateFlow<PeerConnection.IceConnectionState> = _iceConnectionState.asStateFlow()

    private val _iceCandidateFlow = MutableSharedFlow<IceCandidate>(extraBufferCapacity = 64)
    val iceCandidateFlow: SharedFlow<IceCandidate> = _iceCandidateFlow.asSharedFlow()

    private val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
        sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
    }

    private val observer = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState?) {
            Log.d(TAG, "Signaling state changed: $state")
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
            Log.i(TAG, "PeerConnection state changed: $newState")
            newState?.let { _connectionState.value = it }
        }

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
            Log.i(TAG, "ICE Connection state changed: $newState")
            newState?.let { _iceConnectionState.value = it }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) {
            Log.d(TAG, "ICE Connection receiving changed: $receiving")
        }

        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
            Log.d(TAG, "ICE Gathering state changed: $state")
        }

        override fun onIceCandidate(candidate: IceCandidate?) {
            candidate?.let {
                Log.d(TAG, "New local ICE candidate: ${it.sdp}")
                _iceCandidateFlow.tryEmit(it)
            }
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
            Log.d(TAG, "ICE candidates removed: ${candidates?.size}")
        }

        override fun onAddStream(stream: MediaStream?) {
            Log.d(TAG, "Stream added: ${stream?.id}")
        }

        override fun onRemoveStream(stream: MediaStream?) {
            Log.d(TAG, "Stream removed: ${stream?.id}")
        }

        override fun onDataChannel(dataChannel: DataChannel?) {
            Log.d(TAG, "DataChannel received: ${dataChannel?.label()}")
            dataChannel?.let { onRemoteDataChannel?.invoke(it) }
        }

        override fun onRenegotiationNeeded() {
            Log.d(TAG, "Renegotiation needed")
        }

        override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
            Log.d(TAG, "Track added via Unified Plan: ${receiver?.track()?.kind()}")
            receiver?.track()?.let { onRemoteTrack?.invoke(it) }
        }

        override fun onTrack(transceiver: RtpTransceiver?) {
            Log.d(TAG, "Transceiver track updated: ${transceiver?.receiver?.track()?.kind()}")
            transceiver?.receiver?.track()?.let { onRemoteTrack?.invoke(it) }
        }
    }

    val peerConnection: PeerConnection = checkNotNull(
        sessionManager.peerConnectionFactory.createPeerConnection(rtcConfig, observer)
    ) { "Failed to create PeerConnection" }

    suspend fun createOffer(iceRestart: Boolean = false): SessionDescription = suspendCoroutine { continuation ->
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            if (iceRestart) {
                mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
            }
        }

        peerConnection.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                if (sdp != null) {
                    continuation.resume(sdp)
                } else {
                    continuation.resumeWithException(IllegalStateException("Generated SDP offer is null"))
                }
            }

            override fun onCreateFailure(error: String?) {
                continuation.resumeWithException(IllegalStateException("Failed to create offer: $error"))
            }

            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    suspend fun createAnswer(): SessionDescription = suspendCoroutine { continuation ->
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        peerConnection.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                if (sdp != null) {
                    continuation.resume(sdp)
                } else {
                    continuation.resumeWithException(IllegalStateException("Generated SDP answer is null"))
                }
            }

            override fun onCreateFailure(error: String?) {
                continuation.resumeWithException(IllegalStateException("Failed to create answer: $error"))
            }

            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    suspend fun setLocalDescription(sdp: SessionDescription): Unit = suspendCoroutine { continuation ->
        peerConnection.setLocalDescription(object : SdpObserver {
            override fun onSetSuccess() {
                continuation.resume(Unit)
            }

            override fun onSetFailure(error: String?) {
                continuation.resumeWithException(IllegalStateException("Failed to set local description: $error"))
            }

            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, sdp)
    }

    suspend fun setRemoteDescription(sdp: SessionDescription): Unit = suspendCoroutine { continuation ->
        peerConnection.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                continuation.resume(Unit)
            }

            override fun onSetFailure(error: String?) {
                continuation.resumeWithException(IllegalStateException("Failed to set remote description: $error"))
            }

            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, sdp)
    }

    fun addIceCandidate(candidate: IceCandidate): Boolean {
        return peerConnection.addIceCandidate(candidate)
    }

    /**
     * Triggers native WebRTC ICE restart.
     */
    fun restartIce() {
        try {
            peerConnection.restartIce()
            Log.i(TAG, "PeerConnection native restartIce() invoked")
        } catch (e: Exception) {
            Log.w(TAG, "Error invoking native restartIce: ${e.message}")
        }
    }

    fun close() {
        try {
            peerConnection.close()
            peerConnection.dispose()
            Log.i(TAG, "PeerConnection closed and disposed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing PeerConnection", e)
        }
    }
}
