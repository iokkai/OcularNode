package io.github.iokkai.ocularnode.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

class AudioEngine {
    private val sampleRate = 16000
    private val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
    private val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSizeIn = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioFormat).coerceAtLeast(1280)
    private val bufferSizeOut = AudioTrack.getMinBufferSize(sampleRate, channelConfigOut, audioFormat).coerceAtLeast(1280)

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private var recordJob: Job? = null
    private var isRecording = false
    private var isPlaying = false

    private val recordingClientsCount = AtomicInteger(0)
    private val playingClientsCount = AtomicInteger(0)
    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _audioBufferFlow = MutableSharedFlow<ByteArray>(extraBufferCapacity = 128)
    val audioBufferFlow: SharedFlow<ByteArray> = _audioBufferFlow

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (recordingClientsCount.getAndIncrement() > 0) {
            return // Already recording
        }
        try {
            val audioSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION
            audioRecord = try {
                AudioRecord(audioSource, sampleRate, channelConfigIn, audioFormat, bufferSizeIn)
            } catch (e: Exception) {
                AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfigIn, audioFormat, bufferSizeIn)
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("AudioEngine", "AudioRecord initialization failed")
                return
            }

            val sessionId = audioRecord?.audioSessionId ?: 0
            if (sessionId != 0) {
                try {
                    if (AcousticEchoCanceler.isAvailable()) {
                        aec = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
                    }
                    if (NoiseSuppressor.isAvailable()) {
                        ns = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
                    }
                } catch (e: Exception) {
                    Log.w("AudioEngine", "AudioFX init warning", e)
                }
            }

            audioRecord?.startRecording()
            isRecording = true

            recordJob = engineScope.launch {
                while (isActive && isRecording) {
                    val buf = ByteArray(640) // 20ms frame at 16kHz. Allocate fresh to prevent flow race conditions.
                    val read = audioRecord?.read(buf, 0, buf.size) ?: 0
                    if (read > 0) {
                        if (read == buf.size) {
                            _audioBufferFlow.emit(buf)
                        } else {
                            _audioBufferFlow.emit(buf.copyOf(read))
                        }
                    } else if (read < 0) {
                        Log.e("AudioEngine", "AudioRecord read error: $read")
                        delay(100) // Prevent 100% CPU lock if mic hardware is blocked
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AudioEngine", "Error starting AudioRecord", e)
            recordingClientsCount.decrementAndGet().coerceAtLeast(0)
        }
    }

    fun stopRecording() {
        if (recordingClientsCount.decrementAndGet() > 0) {
            return // Still in use by other clients
        }
        recordingClientsCount.set(0)
        isRecording = false
        recordJob?.cancel()
        recordJob = null
        try {
            aec?.release()
            ns?.release()
            aec = null
            ns = null
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e("AudioEngine", "Error stopping AudioRecord", e)
        } finally {
            audioRecord = null
        }
    }

    @Suppress("DEPRECATION")
    fun startPlaying(context: Context? = null) {
        if (playingClientsCount.getAndIncrement() > 0) {
            return // Already playing
        }
        try {
            if (context != null) {
                enableSpeakerphone(context, true)
            }

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
                .build()

            val format = AudioFormat.Builder()
                .setEncoding(audioFormat)
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfigOut)
                .build()

            audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferSizeOut)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    .build()
            } else {
                AudioTrack(
                    attributes,
                    format,
                    bufferSizeOut,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )
            }

            audioTrack?.play()
            isPlaying = true
        } catch (e: Exception) {
            Log.e("AudioEngine", "Error starting AudioTrack", e)
            playingClientsCount.decrementAndGet().coerceAtLeast(0)
        }
    }

    fun playChunk(bytes: ByteArray, length: Int) {
        if (!isPlaying || audioTrack == null) {
            startPlaying()
        }
        try {
            audioTrack?.write(bytes, 0, length)
        } catch (e: Exception) {
            Log.e("AudioEngine", "Error writing audio to track", e)
        }
    }

    fun stopPlaying() {
        if (playingClientsCount.decrementAndGet() > 0) {
            return // Still in use by other clients
        }
        playingClientsCount.set(0)
        isPlaying = false
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e("AudioEngine", "Error stopping AudioTrack", e)
        } finally {
            audioTrack = null
        }
    }

    @Suppress("DEPRECATION")
    fun enableSpeakerphone(context: Context, enable: Boolean) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                // If we use USAGE_MEDIA, we don't need to force MODE_IN_COMMUNICATION which degrades audio.
                // We just let it play as normal media.
                audioManager.mode = AudioManager.MODE_NORMAL
                if (enable) {
                    audioManager.isSpeakerphoneOn = true
                } else {
                    audioManager.isSpeakerphoneOn = false
                }
            }
        } catch (e: Exception) {
            Log.w("AudioEngine", "Error setting speakerphone state", e)
        }
    }

    fun release() {
        stopRecording()
        stopPlaying()
    }
}

