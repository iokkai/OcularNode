package io.github.iokkai.ocularnode.client

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import io.github.iokkai.ocularnode.audio.AudioEngine
import io.github.iokkai.ocularnode.data.CameraDevice
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

    private val mjpegClient = client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // Unlimited read timeout for continuous stream
        .build()

    private val heartbeatClient = client.newBuilder()
        .readTimeout(3, TimeUnit.SECONDS)
        .connectTimeout(3, TimeUnit.SECONDS)
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
    private var sessionToken: String? = null

    /** 設定與相機節點通訊時使用的 Session Token 或 PIN 驗證標記 */
    fun setSessionToken(token: String?) {
        sessionToken = token
    }

    private fun Request.Builder.applyAuth(): Request.Builder {
        val token = sessionToken
        if (!token.isNullOrBlank()) {
            addHeader("Authorization", "Bearer $token")
            addHeader("X-Auth-Token", token)
        }
        return this
    }

    // State
    private val _currentFrame = MutableStateFlow<Bitmap?>(null)
    val currentFrame: StateFlow<Bitmap?> = _currentFrame.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _fps = MutableStateFlow(0)
    val fps: StateFlow<Int> = _fps.asStateFlow()

    private val _lastFrameTimestamp = MutableStateFlow(0L)
    val lastFrameTimestamp: StateFlow<Long> = _lastFrameTimestamp.asStateFlow()

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

        _isConnected.value = false
        _currentFrame.value = null
        _fps.value = 0
        _lastFrameTimestamp.value = 0L
        _cameraStatusJson.value = null
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
                        .applyAuth()
                        .build()

                    val call = mjpegClient.newCall(request)
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

    /**
     * 高效零配置緩衝區，避免在 30 FPS 高頻解析時重複配置 ByteArray
     */
    private class ReusableByteBuffer(initialCapacity: Int = 256 * 1024) {
        var buffer = ByteArray(initialCapacity)
            private set
        var size = 0
            private set

        fun write(bytes: ByteArray, offset: Int, length: Int) {
            ensureCapacity(size + length)
            System.arraycopy(bytes, offset, buffer, size, length)
            size += length
        }

        fun compact(startIndex: Int) {
            if (startIndex <= 0) return
            val remaining = size - startIndex
            if (remaining > 0) {
                System.arraycopy(buffer, startIndex, buffer, 0, remaining)
            }
            size = remaining.coerceAtLeast(0)
        }

        fun reset() {
            size = 0
        }

        private fun ensureCapacity(minCapacity: Int) {
            if (minCapacity > buffer.size) {
                var newCap = buffer.size * 2
                if (newCap < minCapacity) newCap = minCapacity
                val newBuf = ByteArray(newCap)
                System.arraycopy(buffer, 0, newBuf, 0, size)
                buffer = newBuf
            }
        }
    }

    private fun readMjpegStream(inputStream: InputStream) {
        var frameCount = 0
        var lastFpsTime = System.currentTimeMillis()

        try {
            val buffer = ByteArray(16384)
            val streamBuffer = ReusableByteBuffer(256 * 1024)

            val soi = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
            val eoi = byteArrayOf(0xFF.toByte(), 0xD9.toByte())

            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                streamBuffer.write(buffer, 0, read)

                while (true) {
                    val currentSize = streamBuffer.size
                    val data = streamBuffer.buffer
                    val startIndex = findSequence(data, soi, 0, currentSize)
                    if (startIndex == -1) {
                        if (currentSize > 100000) {
                            streamBuffer.reset()
                        }
                        break
                    }

                    val endIndex = findSequence(data, eoi, startIndex + 2, currentSize)
                    if (endIndex == -1) {
                        if (startIndex > 0) {
                            streamBuffer.compact(startIndex)
                        }
                        break
                    }

                    val jpegLength = (endIndex + 2) - startIndex
                    // 直接從緩衝區切片解碼，零額外 Java ByteArray 配置
                    val bitmap = BitmapFactory.decodeByteArray(data, startIndex, jpegLength, options)
                    if (bitmap != null) {
                        val now = System.currentTimeMillis()
                        _currentFrame.value = bitmap
                        _lastFrameTimestamp.value = now
                        frameCount++
                        if (now - lastFpsTime >= 1000) {
                            _fps.value = frameCount
                            frameCount = 0
                            lastFpsTime = now
                        }
                    }

                    val consumed = endIndex + 2
                    streamBuffer.compact(consumed)
                }
            }
        } catch (e: Exception) {
            Log.e("CameraStreamClient", "Error reading MJPEG stream", e)
        }
    }

    private fun findSequence(data: ByteArray, sequence: ByteArray, startOffset: Int = 0, limit: Int = data.size): Int {
        if (limit - startOffset < sequence.size) return -1
        for (i in startOffset..limit - sequence.size) {
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
            val request = Request.Builder().url(cameraDevice.getStatusUrl()).get().applyAuth().build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
                val bodyStr = response.body!!.string()
                val json = JSONObject(bodyStr)
                response.close()
                _cameraStatusJson.value = json
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
            val request = Request.Builder().url(logUrl).get().applyAuth().build()
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
            var failCount = 0
            while (isActive) {
                val start = System.currentTimeMillis()
                try {
                    val request = Request.Builder()
                        .url(cameraDevice.getStatusUrl())
                        .header("Connection", "close")
                        .get()
                        .applyAuth()
                        .build()
                    val call = heartbeatClient.newCall(request)
                    heartbeatCall = call
                    val response = call.execute()
                    if (response.isSuccessful && response.body != null) {
                        val bodyStr = response.body!!.string()
                        val json = JSONObject(bodyStr)
                        val ping = (System.currentTimeMillis() - start).toInt().coerceAtLeast(1)
                        json.put("pingMs", ping)
                        _cameraStatusJson.value = json
                        failCount = 0
                    } else {
                        failCount++
                    }
                    response.close()
                } catch (e: Exception) {
                    failCount++
                    Log.w("CameraStreamClient", "Heartbeat error ($failCount): ${e.message}")
                } finally {
                    heartbeatCall = null
                }

                if (failCount >= 3) {
                    _cameraStatusJson.value = null
                }

                delay(2000L) // Poll status every 2s
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
                .applyAuth()
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
                .applyAuth()
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
                .get()
                .applyAuth()
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
                val authHeader = sessionToken?.let { "Authorization: Bearer $it\r\nX-Auth-Token: $it\r\n" } ?: ""
                out.write("GET /audio HTTP/1.1\r\nHost: ${cameraDevice.ipAddress}:${cameraDevice.port}\r\n${authHeader}Connection: close\r\n\r\n".toByteArray())
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

                val authHeader = sessionToken?.let { "Authorization: Bearer $it\r\nX-Auth-Token: $it\r\n" } ?: ""
                val header = "POST /speak HTTP/1.1\r\n" +
                        "Host: ${cameraDevice.ipAddress}:${cameraDevice.port}\r\n" +
                        "Content-Type: audio/pcm\r\n" +
                        authHeader +
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
                audioEngine.stopRecording()
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
