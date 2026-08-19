package io.github.iokkai.ocularnode.webrtc

import android.content.Context
import android.util.Log
import io.github.iokkai.ocularnode.webrtc.stun.StunPoolManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.Logging
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * Manages the WebRTC global infrastructure, including EglBase (OpenGL context),
 * JavaAudioDeviceModule (hardware AEC/NS), hardware video encoder/decoder factories,
 * and the shared PeerConnectionFactory.
 */
class WebRtcSessionManager(private val context: Context) {

    companion object {
        private const val TAG = "WebRtcSessionManager"

        @Volatile
        private var instance: WebRtcSessionManager? = null

        @Volatile
        private var isInitialized = false

        fun getInstance(context: Context): WebRtcSessionManager {
            return instance ?: synchronized(this) {
                instance ?: WebRtcSessionManager(context.applicationContext).also { instance = it }
            }
        }

        fun initializeWebRtc(context: Context) {
            if (!isInitialized) {
                synchronized(this) {
                    if (!isInitialized) {
                        val initOptions = PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                            .setEnableInternalTracer(false)
                            .createInitializationOptions()
                        PeerConnectionFactory.initialize(initOptions)
                        Logging.enableLogToDebugOutput(Logging.Severity.LS_INFO)
                        isInitialized = true
                        Log.i(TAG, "WebRTC global initialization complete")
                    }
                }
            }
        }
    }

    val eglBase: EglBase by lazy {
        EglBase.create()
    }

    private val audioDeviceModule: AudioDeviceModule by lazy {
        JavaAudioDeviceModule.builder(context.applicationContext)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .setAudioRecordErrorCallback(object : JavaAudioDeviceModule.AudioRecordErrorCallback {
                override fun onWebRtcAudioRecordInitError(errorMessage: String?) {
                    Log.e(TAG, "AudioRecord init error: $errorMessage")
                }
                override fun onWebRtcAudioRecordStartError(errorCode: JavaAudioDeviceModule.AudioRecordStartErrorCode?, errorMessage: String?) {
                    Log.e(TAG, "AudioRecord start error [$errorCode]: $errorMessage")
                }
                override fun onWebRtcAudioRecordError(errorMessage: String?) {
                    Log.e(TAG, "AudioRecord error: $errorMessage")
                }
            })
            .setAudioTrackErrorCallback(object : JavaAudioDeviceModule.AudioTrackErrorCallback {
                override fun onWebRtcAudioTrackInitError(errorMessage: String?) {
                    Log.e(TAG, "AudioTrack init error: $errorMessage")
                }
                override fun onWebRtcAudioTrackStartError(errorCode: JavaAudioDeviceModule.AudioTrackStartErrorCode?, errorMessage: String?) {
                    Log.e(TAG, "AudioTrack start error [$errorCode]: $errorMessage")
                }
                override fun onWebRtcAudioTrackError(errorMessage: String?) {
                    Log.e(TAG, "AudioTrack error: $errorMessage")
                }
            })
            .createAudioDeviceModule()
    }

    val videoEncoderFactory: DefaultVideoEncoderFactory by lazy {
        DefaultVideoEncoderFactory(
            eglBase.eglBaseContext,
            true, // enableIntelVp8Encoder
            true  // enableH264HighProfile
        )
    }

    val videoDecoderFactory: DefaultVideoDecoderFactory by lazy {
        DefaultVideoDecoderFactory(eglBase.eglBaseContext)
    }

    val peerConnectionFactory: PeerConnectionFactory by lazy {
        initializeWebRtc(context)

        val options = PeerConnectionFactory.Options().apply {
            disableEncryption = false
            networkIgnoreMask = 0
        }

        PeerConnectionFactory.builder()
            .setOptions(options)
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(videoEncoderFactory)
            .setVideoDecoderFactory(videoDecoderFactory)
            .createPeerConnectionFactory()
    }

    val stunPoolManager by lazy { StunPoolManager() }

    init {
        initializeWebRtc(context)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                stunPoolManager.probeAndRank()
            } catch (e: Exception) {
                Log.w(TAG, "Initial STUN probing encountered error: ${e.message}")
            }
        }
    }

    /**
     * Default ICE servers ranked by measured latency and reachability from StunPoolManager,
     * with optional user-configured TURN Relay server appended as fallback.
     */
    fun getDefaultIceServers(): List<PeerConnection.IceServer> {
        val servers = mutableListOf<PeerConnection.IceServer>()
        servers.addAll(stunPoolManager.getRankedIceServers())

        try {
            val settings = io.github.iokkai.ocularnode.data.SettingsManager.getInstance(context)
            val turnUrl = settings.customTurnServerUrl.trim()
            val turnUser = settings.customTurnUsername.trim()
            val turnPass = settings.customTurnPassword.trim()

            if (turnUrl.isNotBlank()) {
                val turnUri = if (!turnUrl.startsWith("turn:") && !turnUrl.startsWith("turns:")) {
                    "turn:$turnUrl"
                } else {
                    turnUrl
                }
                val builder = PeerConnection.IceServer.builder(turnUri)
                if (turnUser.isNotBlank()) {
                    builder.setUsername(turnUser)
                }
                if (turnPass.isNotBlank()) {
                    builder.setPassword(turnPass)
                }
                servers.add(builder.createIceServer())
                Log.i(TAG, "Injected custom TURN IceServer: $turnUri")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error injecting custom TURN server: ${e.message}")
        }

        return servers
    }

    fun release() {
        try {
            audioDeviceModule.release()
            peerConnectionFactory.dispose()
            eglBase.release()
            Log.i(TAG, "WebRtcSessionManager resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WebRtcSessionManager", e)
        }
    }
}
