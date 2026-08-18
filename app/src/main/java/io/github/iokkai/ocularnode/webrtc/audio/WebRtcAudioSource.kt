package io.github.iokkai.ocularnode.webrtc.audio

import android.util.Log
import io.github.iokkai.ocularnode.webrtc.WebRtcSessionManager
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.MediaConstraints

/**
 * Audio source manager creating WebRTC AudioTrack backed by JavaAudioDeviceModule.
 * Supports dynamic mute/unmute and Push-to-Talk operations.
 */
class WebRtcAudioSource(
    private val sessionManager: WebRtcSessionManager
) {

    companion object {
        private const val TAG = "WebRtcAudioSource"
        const val AUDIO_TRACK_ID = "OCULAR_AUDIO_TRACK_0"
    }

    private var audioSource: AudioSource? = null

    var audioTrack: AudioTrack? = null
        private set

    var isEnabled: Boolean = true
        private set

    fun start(): AudioTrack {
        if (audioTrack != null) return audioTrack!!

        Log.i(TAG, "Starting WebRTC audio source")
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("echoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("noiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("autoGainControl", "true"))
        }

        val source = sessionManager.peerConnectionFactory.createAudioSource(constraints)
        audioSource = source

        val track = sessionManager.peerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, source)
        track.setEnabled(true)
        audioTrack = track
        isEnabled = true

        return track
    }

    fun setMute(muted: Boolean) {
        isEnabled = !muted
        audioTrack?.setEnabled(isEnabled)
        Log.i(TAG, "WebRTC audio track enabled state: $isEnabled")
    }

    fun dispose() {
        try {
            audioTrack?.dispose()
            audioTrack = null

            audioSource?.dispose()
            audioSource = null
            Log.i(TAG, "WebRtcAudioSource disposed")
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing WebRtcAudioSource", e)
        }
    }
}
