package com.example.client

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.audio.AudioEngine
import com.example.data.CameraDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

class CameraStreamClient(private val audioEngine: AudioEngine) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private var streamJob: Job? = null
    private var heartbeatJob: Job? = null
    private var audioListenJob: Job? = null
    private var audioSpeakJob: Job? = null

    @Volatile private var streamCall: okhttp3.Call? = null
    @Volatile private var heartbeatCall: okhttp3.Call? = null

    private var currentCameraDevice: CameraDevice? = null
    private var currentScope: CoroutineScope? = null
    private var currentBotToken: String = ""
    private var currentChatId: String = ""

    // State
    private val _currentFrame = MutableStateFlow<Bitmap?>(null)
    val currentFrame: StateFlow<Bitmap?> = _currentFrame.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _fps = MutableStateFlow(0)
    val fps: StateFlow<Int> = _fps.asStateFlow()

    private val _statusMessage = MutableStateFlow("Unconnected")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _cameraStatusJson = MutableStateFlow<JSONObject?>(null)
    val cameraStatusJson: StateFlow<JSONObject?> = _cameraStatusJson.asStateFlow()

    private val _isListeningAudio = MutableStateFlow(false)
    val isListeningAudio: StateFlow<Boolean> = _isListeningAudio.asStateFlow()

    private val _isSpeakingAudio = MutableStateFlow(false)
    val isSpeakingAudio: StateFlow<Boolean> = _isSpeakingAudio.asStateFlow()

    fun connect(cameraDevice: CameraDevice, scope: CoroutineScope, botToken: String = "", chatId: String = "") {
        this.currentCameraDevice = cameraDevice
        this.currentScope = scope
        this.currentBotToken = botToken
        this.currentChatId = chatId

        disconnectCallAndJobsOnly()

        _isConnecting.value = true
        _statusMessage.value = "Connecting to ${cameraDevice.name} (${cameraDevice.ipAddress})..."

        startHeartbeatLoop(cameraDevice, scope)
        startMjpegStreamLoop(cameraDevice, scope)

        if (botToken.isNotBlank() || chatId.isNotBlank()) {
            scope.launch(Dispatchers.IO) {
                delay(800) // Wait for server to be responsive
                syncTelegramConfig(cameraDevice, botToken, chatId)
            }
        }
    }

    fun onResume() {
        val device = currentCameraDevice ?: return
        val scope = currentScope ?: return
        Log.d("CameraStreamClient", "onResume: Reconnecting camera stream immediately...")
        connect(device, scope, currentBotToken, currentChatId)
    }

    fun onPause() {
        Log.d("CameraStreamClient", "onPause: Pausing active stream calls...")
        stopListeningAudio()
        stopSpeakingAudio()
        disconnectCallAndJobsOnly()
        _isConnected.value = false
        _isConnecting.value = false
        _statusMessage.value = "Paused"
    }

    private fun disconnectCallAndJobsOnly() {
        stopListeningAudio()
        stopSpeakingAudio()
        try { streamCall?.cancel() } catch (_: Exception) {}
        streamCall = null
        streamJob?.cancel()
        streamJob = null

        try { heartbeatCall?.cancel() } catch (_: Exception) {}
        heartbeatCall = null
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    suspend fun syncTelegramConfig(cameraDevice: CameraDevice, botToken: String, chatId: String): Boolean {
        if (botToken.isBlank() && chatId.isBlank()) return false
        val configJson = JSONObject().apply {
            put("token", botToken)
            put("chatId", chatId)
        }.toString()
        return sendControlCommand(cameraDevice, "telegram_config", configJson)
    }

    private fun startMjpegStreamLoop(cameraDevice: CameraDevice, scope: CoroutineScope) {
        streamJob = scope.launch(Dispatchers.IO) {
            var retryDelayMs = 1000L

            while (isActive) {
                try {
                    val request = Request.Builder()
                        .url(cameraDevice.getMjpegUrl())
                        .build()

                    val call = client.newCall(request)
                    streamCall = call
                    val response = call.execute()
                    if (!response.isSuccessful || response.body == null) {
                        response.close()
                        throw Exception("HTTP error code: ${response.code}")
                    }

                    _isConnected.value = true
                    _isConnecting.value = false
                    _statusMessage.value = "Live Streaming"
                    retryDelayMs = 1000L // Reset backoff on successful connect

                    val inputStream = response.body!!.byteStream()
                    readMjpegStream(inputStream)

                    response.close()
                } catch (e: Exception) {
                    if (!isActive) break
                    _isConnected.value = false
                    _isConnecting.value = true
                    _statusMessage.value = "Disconnected. Retrying in ${retryDelayMs / 1000}s..."
                    Log.w("CameraStreamClient", "Stream connection lost, retrying in ${retryDelayMs}ms", e)

                    delay(retryDelayMs)
                    retryDelayMs = (retryDelayMs * 2).coerceAtMost(30000L) // Exponential backoff max 30s
                } finally {
                    streamCall = null
                }
            }
        }
    }

    private fun readMjpegStream(inputStream: InputStream) {
        var frameCount = 0
        var lastFpsTime = System.currentTimeMillis()

        try {
            val buffer = ByteArray(16384)
            val streamBuffer = ByteArrayOutputStream()

            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                streamBuffer.write(buffer, 0, read)
                val bytes = streamBuffer.toByteArray()

                // Find the LAST completed JPEG frame (0xFF 0xD8 to 0xFF 0xD9) in the current buffer
                val lastEndIndex = findLastSequence(bytes, byteArrayOf(0xFF.toByte(), 0xD9.toByte()))
                if (lastEndIndex != -1) {
                    val startIndex = findSequenceBefore(bytes, byteArrayOf(0xFF.toByte(), 0xD8.toByte()), lastEndIndex)
                    if (startIndex != -1 && lastEndIndex > startIndex) {
                        val jpegLength = (lastEndIndex + 2) - startIndex
                        val jpegBytes = ByteArray(jpegLength)
                        System.arraycopy(bytes, startIndex, jpegBytes, 0, jpegLength)

                        val options = BitmapFactory.Options().apply {
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, options)
                        if (bitmap != null) {
                            _currentFrame.value = bitmap
                            frameCount++
                            val now = System.currentTimeMillis()
                            if (now - lastFpsTime >= 1000) {
                                _fps.value = frameCount
                                frameCount = 0
                                lastFpsTime = now
                            }
                        }

                        // Reset buffer discarding everything up to lastEndIndex + 2 to guarantee zero frame delay
                        val remainingLen = bytes.size - (lastEndIndex + 2)
                        streamBuffer.reset()
                        if (remainingLen > 0) {
                            streamBuffer.write(bytes, lastEndIndex + 2, remainingLen)
                        }
                    }
                } else if (bytes.size > 1000000) {
                    streamBuffer.reset()
                }
            }
        } catch (e: Exception) {
            Log.e("CameraStreamClient", "Error reading MJPEG stream", e)
        }
    }

    private fun findSequence(data: ByteArray, sequence: ByteArray): Int {
        if (data.size < sequence.size) return -1
        for (i in 0..data.size - sequence.size) {
            var match = true
            for (j in sequence.indices) {
                if (data[i + j] != sequence[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
    }

    private fun findLastSequence(data: ByteArray, sequence: ByteArray): Int {
        if (data.size < sequence.size) return -1
        for (i in data.size - sequence.size downTo 0) {
            var match = true
            for (j in sequence.indices) {
                if (data[i + j] != sequence[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
    }

    private fun findSequenceBefore(data: ByteArray, sequence: ByteArray, maxIndex: Int): Int {
        if (data.size < sequence.size || maxIndex < sequence.size) return -1
        val startFrom = (maxIndex - 1).coerceAtMost(data.size - sequence.size)
        for (i in startFrom downTo 0) {
            var match = true
            for (j in sequence.indices) {
                if (data[i + j] != sequence[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
    }

    suspend fun fetchCameraStatus(cameraDevice: CameraDevice): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(cameraDevice.getStatusUrl()).get().build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
                val bodyStr = response.body!!.string()
                val json = JSONObject(bodyStr)
                response.close()
                return@withContext json
            }
            response.close()
            return@withContext null
        } catch (e: Exception) {
            return@withContext null
        }
    }


    suspend fun fetchRemoteLogs(cameraDevice: CameraDevice): List<String> = withContext(Dispatchers.IO) {
        try {
            val logUrl = "http://${cameraDevice.ipAddress}:${cameraDevice.port}/logs"
            val request = Request.Builder().url(logUrl).get().build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
                val bodyStr = response.body!!.string()
                val json = JSONObject(bodyStr)
                response.close()
                val logsArray = json.optJSONArray("logs")
                val logsList = mutableListOf<String>()
                if (logsArray != null) {
                    for (i in 0 until logsArray.length()) {
                        logsList.add(logsArray.getString(i))
                    }
                }
                return@withContext logsList
            }
            response.close()
            return@withContext emptyList()
        } catch (e: Exception) {
            return@withContext emptyList()
        }
    }

    private fun startHeartbeatLoop(cameraDevice: CameraDevice, scope: CoroutineScope) {
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val request = Request.Builder().url(cameraDevice.getStatusUrl()).get().build()
                    val call = client.newCall(request)
                    heartbeatCall = call
                    val response = call.execute()
                    if (response.isSuccessful && response.body != null) {
                        val bodyStr = response.body!!.string()
                        val json = JSONObject(bodyStr)
                        _cameraStatusJson.value = json
                    }
                    response.close()
                } catch (e: Exception) {
                    _cameraStatusJson.value = null
                } finally {
                    heartbeatCall = null
                }
                delay(3000L) // Poll status every 3s
            }
        }
    }

    suspend fun sendControlCommand(cameraDevice: CameraDevice, command: String, value: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("command", command)
                put("value", value)
            }
            val requestBody = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(cameraDevice.getControlUrl())
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val success = response.isSuccessful
            response.close()
            return@withContext success
        } catch (e: Exception) {
            Log.e("CameraStreamClient", "Error sending control command", e)
            return@withContext false
        }
    }

    suspend fun postRemoteConfig(cameraDevice: CameraDevice, configJsonStr: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val requestBody = configJsonStr.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("http://${cameraDevice.ipAddress}:${cameraDevice.port}/config")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val success = response.isSuccessful
            response.close()
            return@withContext success
        } catch (e: Exception) {
            Log.e("CameraStreamClient", "Error posting remote config", e)
            return@withContext false
        }
    }

    suspend fun fetchRemoteConfig(cameraDevice: CameraDevice): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("http://${cameraDevice.ipAddress}:${cameraDevice.port}/config")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
                val str = response.body!!.string()
                response.close()
                return@withContext JSONObject(str)
            }
            response.close()
            return@withContext null
        } catch (e: Exception) {
            Log.e("CameraStreamClient", "Error fetching remote config", e)
            return@withContext null
        }
    }

    fun startListeningAudio(cameraDevice: CameraDevice, scope: CoroutineScope, context: android.content.Context? = null) {
        if (_isListeningAudio.value) return
        _isListeningAudio.value = true

        audioListenJob = scope.launch(Dispatchers.IO) {
            var socket: java.net.Socket? = null
            try {
                socket = java.net.Socket(cameraDevice.ipAddress, cameraDevice.port)
                socket.tcpNoDelay = true
                socket.receiveBufferSize = 16384
                val out = socket.getOutputStream()
                out.write("GET /audio HTTP/1.1\r\nHost: ${cameraDevice.ipAddress}:${cameraDevice.port}\r\nConnection: close\r\n\r\n".toByteArray())
                out.flush()

                val inputStream = socket.getInputStream()
                skipHttpHeaders(inputStream)

                audioEngine.startPlaying(context)
                val buffer = ByteArray(640)
                var read = 0
                while (isActive && _isListeningAudio.value && inputStream.read(buffer).also { read = it } != -1) {
                    if (read > 0) {
                        audioEngine.playChunk(buffer, read)
                    }
                }
            } catch (e: Exception) {
                Log.e("CameraStreamClient", "Error listening audio stream", e)
            } finally {
                try { socket?.close() } catch (_: Exception) {}
                _isListeningAudio.value = false
            }
        }
    }

    private fun skipHttpHeaders(inputStream: InputStream) {
        var state = 0
        while (state < 4) {
            val b = inputStream.read()
            if (b == -1) break
            if ((state == 0 || state == 2) && b == '\r'.code) state++
            else if ((state == 1 || state == 3) && b == '\n'.code) state++
            else if (b == '\r'.code) state = 1
            else state = 0
        }
    }

    fun stopListeningAudio() {
        _isListeningAudio.value = false
        audioListenJob?.cancel()
        audioListenJob = null
        audioEngine.stopPlaying()
    }

    fun startSpeakingAudio(cameraDevice: CameraDevice, scope: CoroutineScope) {
        if (_isSpeakingAudio.value) return
        _isSpeakingAudio.value = true

        audioEngine.startRecording(scope)

        audioSpeakJob = scope.launch(Dispatchers.IO) {
            var socket: java.net.Socket? = null
            try {
                socket = java.net.Socket(cameraDevice.ipAddress, cameraDevice.port)
                socket.tcpNoDelay = true
                socket.sendBufferSize = 16384
                val out = socket.getOutputStream()

                val header = "POST /speak HTTP/1.1\r\n" +
                        "Host: ${cameraDevice.ipAddress}:${cameraDevice.port}\r\n" +
                        "Content-Type: audio/pcm\r\n" +
                        "Connection: close\r\n\r\n"
                out.write(header.toByteArray())
                out.flush()

                audioEngine.audioBufferFlow.collect { chunk ->
                    if (!isActive || !_isSpeakingAudio.value) return@collect
                    try {
                        out.write(chunk)
                        out.flush()
                    } catch (e: Exception) {
                        Log.e("CameraStreamClient", "Error streaming speak chunk", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("CameraStreamClient", "Error speaking audio", e)
            } finally {
                try { socket?.close() } catch (_: Exception) {}
                _isSpeakingAudio.value = false
            }
        }
    }

    fun stopSpeakingAudio() {
        _isSpeakingAudio.value = false
        audioSpeakJob?.cancel()
        audioSpeakJob = null
        audioEngine.stopRecording()
    }

    fun disconnect() {
        disconnectCallAndJobsOnly()
        _isConnected.value = false
        _isConnecting.value = false
        _currentFrame.value = null
        _statusMessage.value = "Disconnected"
        currentCameraDevice = null
        currentScope = null
        currentBotToken = ""
        currentChatId = ""
    }
}
