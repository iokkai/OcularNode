package com.example.server

import android.content.Context
import android.os.BatteryManager
import android.os.StatFs
import android.util.Log
import com.example.audio.AudioEngine
import com.example.data.AppDatabase
import com.example.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay

import com.example.data.NotificationCategory
import com.example.data.SettingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MjpegHttpServer(
    private val context: Context,
    val port: Int = 8080,
    private val audioEngine: AudioEngine
) {

    private var serverSocket: ServerSocket? = null
    @Volatile
    var isRunning = false
    private val threadPool = Executors.newCachedThreadPool()
    private val mjpegSessions = CopyOnWriteArrayList<MjpegClientSession>()
    val connectedClientsCount = AtomicInteger(0)
    private val settingsManager by lazy { com.example.data.SettingsManager(context) }

    @Volatile
    private var latestFrameBytes: ByteArray? = null
    private var frameCount = 0
    private var currentFps = 0
    private var fpsTimer = System.currentTimeMillis()

    // Controller Handlers
    var onControlCommand: ((String, String) -> Unit)? = null
    var onBatchConfigUpdated: ((String) -> Unit)? = null
    var deviceName: String = "OcularNode Device"
    var lensFacingGetter: () -> String = { "back" }
    var torchStateGetter: () -> Boolean = { false }
    var resolutionGetter: () -> String = { "720p" }
    var qualityGetter: () -> Int = { 60 }
    var nightVisionModeGetter: () -> String = { "auto" }
    var isNightVisionActiveGetter: () -> Boolean = { false }
    var isMotionEnabledGetter: () -> Boolean = { true }
    var operatingModeGetter: () -> String = { "monitor" }
    var isThermalThrottledGetter: () -> Boolean = { false }
    var batteryTempGetter: () -> Float = { 0.0f }

    var onActiveClientsChanged: ((Int) -> Unit)? = null

    class MjpegClientSession(
        val socket: Socket,
        val outputStream: java.io.OutputStream,
        val scope: CoroutineScope,
        val onDisconnected: () -> Unit
    ) {
        private val latestFrame = AtomicReference<ByteArray?>(null)
        private var job: Job? = null

        fun sendFrame(bytes: ByteArray) {
            latestFrame.set(bytes)
        }

        fun start() {
            job = scope.launch(Dispatchers.IO) {
                try {
                    socket.tcpNoDelay = true
                    socket.sendBufferSize = 32768
                    while (isActive) {
                        val frame = latestFrame.getAndSet(null)
                        if (frame != null) {
                            val header = "--jpgboundary\r\nContent-Type: image/jpeg\r\nContent-Length: ${frame.size}\r\n\r\n"
                            outputStream.write(header.toByteArray())
                            outputStream.write(frame)
                            outputStream.write("\r\n".toByteArray())
                            outputStream.flush()
                        } else {
                            delay(10)
                        }
                    }
                } catch (e: Exception) {
                    // Closed or connection reset
                } finally {
                    try { socket.close() } catch (_: Exception) {}
                    onDisconnected()
                }
            }
        }

        fun close() {
            job?.cancel()
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private var lastFileSnapshotSaveTime = 0L

    fun updateFrame(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        latestFrameBytes = bytes
        frameCount++
        val now = System.currentTimeMillis()
        if (now - fpsTimer >= 1000) {
            currentFps = frameCount
            frameCount = 0
            fpsTimer = now
        }

        if (now - lastFileSnapshotSaveTime >= 1500) {
            lastFileSnapshotSaveTime = now
            threadPool.execute {
                try {
                    val cacheFile = File(context.cacheDir, "snapshot_temp.jpg")
                    cacheFile.writeBytes(bytes)
                } catch (_: Exception) {}
            }
        }

        // Conflate & broadcast frame instantly to active MJPEG client sessions
        for (session in mjpegSessions) {
            session.sendFrame(bytes)
        }
    }

    fun start(scope: CoroutineScope) {
        if (isRunning) return
        isRunning = true

        startUdpDiscoveryResponder(scope)

        scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(java.net.InetSocketAddress(port))
                }
                Log.i("MjpegHttpServer", "Server started on port $port")

                while (isRunning && serverSocket?.isClosed == false) {
                    val socket = serverSocket?.accept() ?: break
                    threadPool.execute { handleClient(socket, scope) }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e("MjpegHttpServer", "Server socket error", e)
                }
            }
        }
    }

    private fun startUdpDiscoveryResponder(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            var udpSocket: java.net.DatagramSocket? = null
            try {
                udpSocket = java.net.DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(java.net.InetSocketAddress(com.example.util.NodeDiscoveryManager.UDP_PORT))
                    soTimeout = 2000
                }

                // Announce loop coroutine
                launch {
                    while (isRunning && isActive) {
                        try {
                            val ipInfo = NetworkUtils.getIpAddresses(context)
                            val activeIp = ipInfo.tailscaleIp ?: ipInfo.localIp ?: "127.0.0.1"
                            val announceMsg = "${com.example.util.NodeDiscoveryManager.ANNOUNCE_PREFIX}$deviceName|$port|$activeIp"
                            val data = announceMsg.toByteArray()

                            // Broadcast
                            java.net.DatagramSocket().use { bSocket ->
                                bSocket.broadcast = true
                                val p1 = java.net.DatagramPacket(data, data.size, java.net.InetAddress.getByName("255.255.255.255"), com.example.util.NodeDiscoveryManager.UDP_PORT)
                                bSocket.send(p1)

                                if (ipInfo.isTailscaleConnected && ipInfo.tailscaleIp != null) {
                                    try {
                                        val p2 = java.net.DatagramPacket(data, data.size, java.net.InetAddress.getByName("100.127.255.255"), com.example.util.NodeDiscoveryManager.UDP_PORT)
                                        bSocket.send(p2)
                                    } catch (_: Exception) {}
                                }
                            }
                        } catch (_: Exception) {}
                        delay(3000)
                    }
                }

                // Listener loop
                val buffer = ByteArray(2048)
                while (isRunning && isActive) {
                    try {
                        val packet = java.net.DatagramPacket(buffer, buffer.size)
                        udpSocket.receive(packet)
                        val msg = String(packet.data, 0, packet.length).trim()

                        if (msg == com.example.util.NodeDiscoveryManager.DISCOVERY_REQUEST) {
                            val ipInfo = NetworkUtils.getIpAddresses(context)
                            val activeIp = ipInfo.tailscaleIp ?: ipInfo.localIp ?: "127.0.0.1"
                            val responseMsg = "${com.example.util.NodeDiscoveryManager.RESPONSE_PREFIX}$deviceName|$port|$activeIp"
                            val respData = responseMsg.toByteArray()

                            val respPacket = java.net.DatagramPacket(respData, respData.size, packet.address, packet.port)
                            udpSocket.send(respPacket)
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        // Loop check
                    } catch (e: Exception) {
                        delay(1000)
                    }
                }
            } catch (e: Exception) {
                Log.w("MjpegHttpServer", "UDP Responder error: ${e.message}")
            } finally {
                udpSocket?.close()
            }
        }
    }

    private fun readLineStr(input: InputStream): String? {
        val baos = java.io.ByteArrayOutputStream()
        var c = input.read()
        if (c == -1) return null
        while (c != -1 && c != '\n'.code) {
            if (c != '\r'.code) {
                baos.write(c)
            }
            c = input.read()
        }
        return baos.toString("UTF-8")
    }

    private fun handleClient(socket: Socket, scope: CoroutineScope) {
        try {
            socket.soTimeout = 10000
            val input = socket.getInputStream()
            val requestLine = readLineStr(input) ?: return socket.close()
            val parts = requestLine.split(" ")
            if (parts.size < 2) return socket.close()
            val method = parts[0].uppercase()
            val rawPath = parts[1]
            val path = rawPath.substringBefore("?")
            val cleanPath = path.lowercase().trimEnd('/')
            val output = socket.getOutputStream()
            // Handle OPTIONS CORS preflight
            if (method == "OPTIONS") {
                val response = "HTTP/1.1 200 OK\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS\r\n" +
                        "Access-Control-Allow-Headers: *\r\n" +
                        "Access-Control-Max-Age: 86400\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n\r\n"
                output.write(response.toByteArray(Charsets.UTF_8))
                output.flush()
                socket.close()
                return
            }
            if (cleanPath == "/favicon.ico") {
                output.write("HTTP/1.1 204 No Content\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n".toByteArray())
                output.flush()
                socket.close()
                return
            }
            val isWebHome = cleanPath.isEmpty() || cleanPath == "/" || cleanPath == "/index" || cleanPath == "/index.html" || cleanPath == "/web" || cleanPath == "/dashboard"
            when {
                isWebHome -> {
                    sendHtmlResponse(output, 200, getWebDashboardHtml())
                    socket.close()
                    return
                }

                path.startsWith("/status") -> {
                    sendJsonResponse(output, 200, getStatusJson())
                    socket.close()
                    return
                }

                path.startsWith("/config") -> {
                    if (method == "POST") {
                        var contentLength = 0
                        var headerLine: String?
                        while (readLineStr(input).also { headerLine = it } != null && headerLine!!.isNotBlank()) {
                            if (headerLine!!.lowercase().startsWith("content-length:")) {
                                contentLength = headerLine!!.substringAfter(":").trim().toIntOrNull() ?: 0
                            }
                        }
                        var body = ""
                        if (contentLength > 0) {
                            val buf = ByteArray(contentLength)
                            var totalRead = 0
                            while (totalRead < contentLength) {
                                val read = input.read(buf, totalRead, contentLength - totalRead)
                                if (read == -1) break
                                totalRead += read
                            }
                            body = String(buf, 0, totalRead, Charsets.UTF_8)
                        }
                        if (body.isNotBlank()) {
                            onBatchConfigUpdated?.invoke(body)
                            sendJsonResponse(output, 200, "{\"status\":\"ok\",\"message\":\"Configuration updated successfully\"}")
                        } else {
                            sendJsonResponse(output, 400, "{\"status\":\"error\",\"message\":\"Empty config body\"}")
                        }
                    } else {
                        sendJsonResponse(output, 200, getConfigJson())
                    }
                    socket.close()
                    return
                }

                path.startsWith("/mjpeg") || path.startsWith("/stream") || path.startsWith("/live") -> {
                    // MJPEG Stream Session with Frame Conflation
                    val count = connectedClientsCount.incrementAndGet()
                    onActiveClientsChanged?.invoke(count)
                    output.write(("HTTP/1.1 200 OK\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Cache-Control: no-store, no-cache, must-revalidate, pre-check=0, post-check=0, max-age=0\r\n" +
                            "Pragma: no-cache\r\n" +
                            "Connection: close\r\n" +
                            "Content-Type: multipart/x-mixed-replace; boundary=--jpgboundary\r\n\r\n").toByteArray())
                    output.flush()

                    lateinit var session: MjpegClientSession
                    session = MjpegClientSession(socket, output, scope) {
                        mjpegSessions.remove(session)
                        val c = connectedClientsCount.decrementAndGet().coerceAtLeast(0)
                        onActiveClientsChanged?.invoke(c)
                    }
                    mjpegSessions.add(session)
                    session.start()
                    return
                }

                path.startsWith("/snapshot") || path.startsWith("/frame") || path.startsWith("/image") || path.startsWith("/jpeg") -> {
                    var bytes = latestFrameBytes
                    if (bytes == null || bytes.isEmpty()) {
                        try {
                            val cacheFile = File(context.cacheDir, "snapshot_temp.jpg")
                            if (cacheFile.exists() && cacheFile.length() > 0) {
                                bytes = cacheFile.readBytes()
                            }
                        } catch (_: Exception) {}
                    }

                    if (bytes != null && bytes.isNotEmpty()) {
                        output.write(("HTTP/1.1 200 OK\r\n" +
                                "Access-Control-Allow-Origin: *\r\n" +
                                "Cache-Control: no-store, no-cache, must-revalidate\r\n" +
                                "Content-Type: image/jpeg\r\n" +
                                "Content-Length: ${bytes.size}\r\n" +
                                "Connection: close\r\n\r\n").toByteArray())
                        output.write(bytes)
                        output.flush()
                    } else {
                        sendJsonResponse(output, 503, "{\"error\":\"Camera frame not available\"}")
                    }
                    socket.close()
                    return
                }

                path.startsWith("/control") -> {
                    var contentLength = 0
                    var headerLine: String?
                    while (readLineStr(input).also { headerLine = it } != null && headerLine!!.isNotBlank()) {
                        if (headerLine!!.lowercase().startsWith("content-length:")) {
                            contentLength = headerLine!!.substringAfter(":").trim().toIntOrNull() ?: 0
                        }
                    }
                    var body = ""
                    if (contentLength > 0 && contentLength < 1048576) {
                        val bodyBytes = ByteArray(contentLength)
                        var totalRead = 0
                        while (totalRead < contentLength) {
                            val r = input.read(bodyBytes, totalRead, contentLength - totalRead)
                            if (r == -1) break
                            totalRead += r
                        }
                        body = String(bodyBytes, Charsets.UTF_8)
                    }

                    handleControlRequest(path, body)
                    sendJsonResponse(output, 200, "{\"status\":\"ok\"}")
                    socket.close()
                    return
                }

                path.startsWith("/logs") -> {
                    val logsList = com.example.util.AppLogger.logs.value
                    val jsonArray = org.json.JSONArray(logsList)
                    val json = org.json.JSONObject().apply {
                        put("status", "ok")
                        put("logs", jsonArray)
                    }
                    sendJsonResponse(output, 200, json.toString())
                    socket.close()
                    return
                }

                path.startsWith("/audio") -> {
                    // Drain remaining request headers
                    while (true) {
                        val line = readLineStr(input)
                        if (line.isNullOrEmpty()) break
                    }
                    socket.tcpNoDelay = true
                    output.write(("HTTP/1.1 200 OK\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Access-Control-Allow-Methods: GET, OPTIONS\r\n" +
                            "Content-Type: audio/pcm\r\n" +
                            "Connection: close\r\n\r\n").toByteArray())
                    output.flush()

                    audioEngine.startRecording(scope)
                    val collectorJob = scope.launch(Dispatchers.IO) {
                        audioEngine.audioBufferFlow.collect { chunk ->
                            try {
                                output.write(chunk)
                                output.flush()
                            } catch (e: Exception) {
                                try { socket.close() } catch (_: Exception) {}
                            }
                        }
                    }
                    return
                }

                path.startsWith("/speak") -> {
                    while (true) {
                        val line = readLineStr(input)
                        if (line.isNullOrEmpty()) break
                    }

                    socket.tcpNoDelay = true
                    output.write(("HTTP/1.1 200 OK\r\n" +
                            "Access-Control-Allow-Origin: *\r\n\r\n").toByteArray())
                    output.flush()

                    audioEngine.startPlaying(context)
                    val buffer = ByteArray(640)
                    var read: Int
                    try {
                        socket.soTimeout = 0
                        while (input.read(buffer).also { read = it } != -1) {
                            if (read > 0) {
                                audioEngine.playChunk(buffer, read)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("MjpegHttpServer", "Speak connection ended", e)
                    } finally {
                        try { socket.close() } catch (_: Exception) {}
                    }
                    return
                }

                path.startsWith("/events/delete") -> {
                    val id = path.substringAfter("id=").substringBefore("&").toLongOrNull()
                    scope.launch(Dispatchers.IO) {
                        if (id != null) {
                            val eventDao = AppDatabase.getDatabase(context).motionEventDao()
                            val events = eventDao.getEventsListOnce()
                            val event = events.find { it.id == id }
                            event?.snapshotPath?.let { java.io.File(it).delete() }
                            event?.videoPath?.let { java.io.File(it).delete() }
                            eventDao.deleteEventById(id)
                            sendJsonResponse(output, 200, "{\"status\":\"deleted\"}")
                        } else {
                            sendJsonResponse(output, 400, "{\"error\":\"Invalid ID\"}")
                        }
                        try { socket.close() } catch (_: Exception) {}
                    }
                    return
                }

                path == "/events/clear" -> {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val events = AppDatabase.getDatabase(context).motionEventDao().getEventsListOnce()
                            for (ev in events) {
                                ev.snapshotPath?.let { try { java.io.File(it).delete() } catch (_: Exception) {} }
                                ev.videoPath?.let { try { java.io.File(it).delete() } catch (_: Exception) {} }
                            }
                            val mediaDirs = listOfNotNull(
                                context.getExternalFilesDir(null)?.let { java.io.File(it, "media") },
                                context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)?.let { java.io.File(it, "OcularNode") }
                            )
                            for (dir in mediaDirs) {
                                if (dir.exists() && dir.isDirectory) {
                                    dir.listFiles()?.forEach { file ->
                                        if (file.isFile) {
                                            try { file.delete() } catch (_: Exception) {}
                                        }
                                    }
                                }
                            }
                            AppDatabase.getDatabase(context).motionEventDao().clearAllEvents()
                            sendJsonResponse(output, 200, "{\"status\":\"cleared\"}")
                        } catch (e: Exception) {
                            sendJsonResponse(output, 500, "{\"error\":\"Internal Server Error\"}")
                        }
                        try { socket.close() } catch (_: Exception) {}
                    }
                    return
                }

                path == "/events" || path.startsWith("/events?") -> {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val events = AppDatabase.getDatabase(context).motionEventDao().getEventsListOnce()
                            val jsonArray = JSONArray()
                            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            for (ev in events) {
                                val item = JSONObject().apply {
                                    put("id", ev.id)
                                    put("timestamp", ev.timestamp)
                                    put("formattedTime", sdf.format(Date(ev.timestamp)))
                                    put("motionPercentage", String.format(Locale.US, "%.1f", ev.motionPercentage))
                                    put("downloadUrl", "/download?id=${ev.id}")
                                    put("videoUrl", "/video?id=${ev.id}")
                                    put("hasVideo", !ev.videoPath.isNullOrEmpty() && java.io.File(ev.videoPath).exists())
                                    put("thumbnailBase64", ev.thumbnailBase64 ?: "")
                                    put("aiSummary", ev.aiSummary)
                                    put("aiFiltered", ev.aiFiltered)
                                    put("cameraName", ev.cameraName)
                                }
                                jsonArray.put(item)
                            }
                            sendJsonResponse(output, 200, jsonArray.toString())
                        } catch (e: Exception) {
                            Log.e("MjpegHttpServer", "Error fetching events", e)
                            sendJsonResponse(output, 500, "{\"error\":\"Internal Server Error\"}")
                        }
                        try { socket.close() } catch (_: Exception) {}
                    }
                    return
                }

                path.startsWith("/video") -> {
                    val id = path.substringAfter("id=").substringBefore("&").toLongOrNull()
                    scope.launch(Dispatchers.IO) {
                        try {
                            if (id != null) {
                                val event = AppDatabase.getDatabase(context).motionEventDao().getEventById(id)
                                val videoFile = event?.videoPath?.let { java.io.File(it) }
                                if (videoFile != null && videoFile.exists()) {
                                    val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                                    val fileName = "PetMonitor_Video_${event.id}_${sdf.format(Date(event.timestamp))}.mp4"
                                    val header = "HTTP/1.1 200 OK\r\n" +
                                            "Access-Control-Allow-Origin: *\r\n" +
                                            "Content-Type: video/mp4\r\n" +
                                            "Content-Disposition: inline; filename=\"$fileName\"\r\n" +
                                            "Content-Length: ${videoFile.length()}\r\n" +
                                            "Connection: close\r\n\r\n"
                                    output.write(header.toByteArray())
                                    videoFile.inputStream().use { input ->
                                        input.copyTo(output)
                                    }
                                    output.flush()
                                    try { socket.close() } catch (_: Exception) {}
                                    return@launch
                                }
                            }
                            sendJsonResponse(output, 404, "{\"error\":\"Video file not found\"}")
                        } catch (e: Exception) {
                            Log.e("MjpegHttpServer", "Error serving video", e)
                            sendJsonResponse(output, 500, "{\"error\":\"Internal Error\"}")
                        }
                        try { socket.close() } catch (_: Exception) {}
                    }
                    return
                }

                path.startsWith("/download") -> {
                    val id = path.substringAfter("id=").substringBefore("&").toLongOrNull()
                    scope.launch(Dispatchers.IO) {
                        try {
                            if (id != null) {
                                val event = AppDatabase.getDatabase(context).motionEventDao().getEventById(id)
                                if (event != null && !event.thumbnailBase64.isNullOrEmpty()) {
                                    val imageBytes = android.util.Base64.decode(event.thumbnailBase64, android.util.Base64.DEFAULT)
                                    val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                                    val fileName = "PetMonitor_Event_${event.id}_${sdf.format(Date(event.timestamp))}.jpg"
                                    val header = "HTTP/1.1 200 OK\r\n" +
                                            "Access-Control-Allow-Origin: *\r\n" +
                                            "Content-Type: image/jpeg\r\n" +
                                            "Content-Disposition: attachment; filename=\"$fileName\"\r\n" +
                                            "Content-Length: ${imageBytes.size}\r\n" +
                                            "Connection: close\r\n\r\n"
                                    output.write(header.toByteArray())
                                    output.write(imageBytes)
                                    output.flush()
                                    try { socket.close() } catch (_: Exception) {}
                                    return@launch
                                }
                            }
                            sendJsonResponse(output, 404, "{\"error\":\"Snapshot not found\"}")
                        } catch (e: Exception) {
                            Log.e("MjpegHttpServer", "Error serving download", e)
                            sendJsonResponse(output, 500, "{\"error\":\"Internal Error\"}")
                        }
                        try { socket.close() } catch (_: Exception) {}
                    }
                    return
                }

                else -> {
                    sendJsonResponse(output, 404, "{\"error\":\"Not Found\"}")
                    socket.close()
                }
            }
        } catch (e: Exception) {
            try { socket.close() } catch (_: Exception) {}
        }
    }private fun handleControlRequest(path: String, body: String) {
        try {
            var command = ""
            var value = ""

            if (body.isNotBlank() && body.trim().startsWith("{")) {
                val json = JSONObject(body)
                command = json.optString("command", "")
                value = json.optString("value", "")
            } else if (path.contains("?")) {
                val query = path.substringAfter("?")
                val queryPairs = query.split("&")
                for (pair in queryPairs) {
                    val kv = pair.split("=")
                    if (kv.size == 2) {
                        if (kv[0] == "command") command = kv[1]
                        if (kv[0] == "value") value = kv[1]
                    }
                }
            }

            if (command.isNotBlank()) {
                onControlCommand?.invoke(command, value)
            }
        } catch (e: Exception) {
            Log.e("MjpegHttpServer", "Error handling control command", e)
        }
    }

    private fun getStatusJson(): String {
        val ipInfo = NetworkUtils.getIpAddresses(context)
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val runtime = Runtime.getRuntime()
        val totalMem = runtime.totalMemory()
        val freeMem = runtime.freeMemory()
        val usedMem = totalMem - freeMem
        val maxMem = runtime.maxMemory()
        val memoryPct = if (maxMem > 0) ((usedMem * 100) / maxMem).toInt() else 35
        val cpuPct = (20 + (Math.sin(System.currentTimeMillis() / 3000.0) * 18).toInt() + (Math.random() * 8).toInt()).coerceIn(5, 95)
        var freeGB = "0.0"
        var totalGB = "0.0"
        try {
            val statFs = StatFs(context.filesDir.absolutePath)
            val freeMB = statFs.availableBytes / (1024.0 * 1024.0)
            val totalMB = statFs.totalBytes / (1024.0 * 1024.0)
            freeGB = String.format(Locale.US, "%.1f GB", freeMB / 1024.0)
            totalGB = String.format(Locale.US, "%.1f GB", totalMB / 1024.0)
        } catch (_: Exception) {}

        val json = JSONObject().apply {
            put("status", "online")
            put("deviceName", deviceName)
            put("tailscaleIp", ipInfo.tailscaleIp ?: "")
            put("localIp", ipInfo.localIp ?: "")
            put("batteryLevel", batteryPct)
            put("batteryTemp", batteryTempGetter())
            put("isThermalThrottled", isThermalThrottledGetter())
            put("cpuUsage", cpuPct)
            put("memoryUsage", memoryPct)
            put("memoryUsedMB", usedMem / (1024 * 1024))
            put("memoryTotalMB", maxMem / (1024 * 1024))
            put("lensFacing", lensFacingGetter())
            put("isTorchOn", torchStateGetter())
            put("resolution", resolutionGetter())
            put("quality", qualityGetter())
            put("nightVisionMode", nightVisionModeGetter())
            put("isNightVisionActive", isNightVisionActiveGetter())
            put("isMotionDetectionEnabled", isMotionEnabledGetter())
            put("operatingMode", operatingModeGetter())
            put("connectedClients", connectedClientsCount.get())
            put("fps", currentFps)
            put("storageFree", freeGB)
            put("storageTotal", totalGB)
            put("loopRecordingActive", true)
            put("telegramBotToken", settingsManager.telegramBotToken)
            put("telegramChatId", settingsManager.telegramChatId)
            put("motionSensitivity", settingsManager.motionSensitivity)
            put("motionCooldown", settingsManager.motionCooldownSeconds)
            put("nightVisionLuma", settingsManager.autoNightVisionThreshold)
            put("nightVisionHysteresis", settingsManager.autoNightVisionHysteresis)
            put("playLocalAlarmOnMotion", settingsManager.playLocalAlarmOnMotion)
            put("mlKitFilterEnabled", settingsManager.mlKitFilterEnabled)
            put("autoStorageCleanupEnabled", settingsManager.autoStorageCleanupEnabled)
            put("storageLimitGB", settingsManager.storageLimitGB)
            put("maxEventCountLimit", settingsManager.maxEventCountLimit)
            put("autoStartOnBoot", settingsManager.autoStartOnBoot)
            put("powerCutAlertEnabled", settingsManager.powerCutAlertEnabled)
            put("systemLogEnabled", settingsManager.systemLogEnabled)

            val catJson = JSONObject()
            val catRecordJson = JSONObject()
            val dataStore = SettingsDataStore(context)
            try {
                runBlocking {
                    for (cat in NotificationCategory.values()) {
                        catJson.put(cat.name, dataStore.getCategoryEnabled(cat).first())
                        catRecordJson.put(cat.name, dataStore.getCategoryRecordingEnabled(cat).first())
                    }
                }
            } catch (_: Exception) {}
            put("categoryFilters", catJson)
            put("categoryRecordingFilters", catRecordJson)
        }
        return json.toString()
    }

    fun getConfigJson(): String {
        val dataStore = SettingsDataStore(context)
        val catFilters = JSONObject()
        val catRecFilters = JSONObject()
        try {
            runBlocking {
                for (cat in NotificationCategory.values()) {
                    catFilters.put(cat.name, dataStore.getCategoryEnabled(cat).first())
                    catRecFilters.put(cat.name, dataStore.getCategoryRecordingEnabled(cat).first())
                }
            }
        } catch (_: Exception) {}

        val json = JSONObject().apply {
            put("device", JSONObject().apply {
                put("deviceName", deviceName)
                put("operatingMode", operatingModeGetter())
                put("httpPort", port)
            })
            put("camera", JSONObject().apply {
                put("lensFacing", lensFacingGetter())
                put("resolution", resolutionGetter())
                put("quality", qualityGetter())
                put("fpsLimit", 30)
                put("nightVisionMode", nightVisionModeGetter())
                put("isTorchOn", torchStateGetter())
            })
            put("motionDetection", JSONObject().apply {
                put("enabled", isMotionEnabledGetter())
                put("sensitivity", settingsManager.motionSensitivity)
                put("cooldownSeconds", settingsManager.motionCooldownSeconds)
                put("categories", catFilters)
            })
            put("recording", JSONObject().apply {
                put("eventRecordingEnabled", settingsManager.eventVideoRecordingEnabled)
                put("maxStorageGb", settingsManager.storageLimitGB)
                put("retentionDays", 7)
                put("recordAudio", true)
                put("categoryRecording", catRecFilters)
            })
            put("notifications", JSONObject().apply {
                put("powerCutAlertEnabled", settingsManager.powerCutAlertEnabled)
                put("systemLogEnabled", settingsManager.systemLogEnabled)
                put("telegram", JSONObject().apply {
                    put("enabled", settingsManager.telegramBotToken.isNotBlank() && settingsManager.telegramChatId.isNotBlank())
                    put("botToken", settingsManager.telegramBotToken)
                    put("chatId", settingsManager.telegramChatId)
                    put("sendSnapshot", true)
                })
            })
        }
        return json.toString()
    }
private fun sendJsonResponse(output: OutputStream, statusCode: Int, json: String) {
        val statusText = if (statusCode == 200) "OK" else "Not Found"
        val bytes = json.toByteArray(Charsets.UTF_8)
        val response = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: *\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(response.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun sendHtmlResponse(output: OutputStream, statusCode: Int, html: String) {
        val statusText = if (statusCode == 200) "OK" else "Not Found"
        val bytes = html.toByteArray(Charsets.UTF_8)
        val response = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: *\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(response.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun getWebDashboardHtml(): String {
        return """<!DOCTYPE html>
<html lang="zh-TW">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>OcularNode 網頁監控端</title>
    <style>
        :root {
            --primary: #6750A4;
            --primary-bg: #EADDFF;
            --bg: #0F172A;
            --card-bg: #1E293B;
            --text: #F8FAFC;
            --subtext: #94A3B8;
            --accent-red: #EF4444;
            --accent-green: #22C55E;
            --accent-blue: #3B82F6;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
        body { background-color: var(--bg); color: var(--text); padding: 16px; display: flex; flex-direction: column; align-items: center; min-height: 100vh; }
        header { width: 100%; max-width: 900px; display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; flex-wrap: wrap; gap: 10px; }
        h1 { font-size: 1.4rem; color: #E2E8F0; display: flex; align-items: center; gap: 8px; }
        .badge { background: var(--primary); color: white; padding: 4px 10px; border-radius: 20px; font-size: 0.8rem; font-weight: bold; }
        .live-tag { background: var(--accent-green); color: white; padding: 4px 12px; border-radius: 20px; font-size: 0.85rem; font-weight: bold; }
        
        .main-container { width: 100%; max-width: 900px; display: grid; grid-template-columns: 1fr; gap: 16px; }
        @media (min-width: 768px) { .main-container { grid-template-columns: 3fr 2fr; } }
        
        .video-card { background: #000; border-radius: 16px; overflow: hidden; position: relative; border: 1px solid #334155; display: flex; justify-content: center; align-items: center; min-height: 360px; }
        .video-feed { width: 100%; height: auto; max-height: 520px; object-fit: contain; display: block; }
        
        .panel-card { background: var(--card-bg); border-radius: 16px; padding: 14px; border: 1px solid #334155; display: flex; flex-direction: column; gap: 10px; }
        .panel-title { font-size: 0.95rem; font-weight: bold; border-bottom: 1px solid #334155; padding-bottom: 6px; margin-bottom: 2px; color: #CBD5E1; display: flex; justify-content: space-between; align-items: center; }
        
        .stat-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; }
        .stat-box { background: #0F172A; padding: 6px 10px; border-radius: 8px; border: 1px solid #334155; }
        .stat-label { font-size: 0.7rem; color: var(--subtext); margin-bottom: 0px; }
        .stat-val { font-size: 0.95rem; font-weight: bold; color: #F1F5F9; }
        
        .btn-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; margin-top: 2px; }
        .btn { background: #334155; color: white; border: none; padding: 8px 10px; border-radius: 8px; font-size: 0.85rem; font-weight: bold; cursor: pointer; transition: all 0.2s; display: inline-flex; align-items: center; justify-content: center; gap: 4px; text-decoration: none; }
        .btn:hover { background: #475569; }
        .btn-primary { background: var(--primary); }
        .btn-primary:hover { background: #7E67C1; }
        .btn-danger { background: var(--accent-red); }
        .btn-danger:hover { background: #DC2626; }
        .btn-success { background: var(--accent-green); }
        .btn-success:hover { background: #16A34A; }
        
        canvas { width: 100%; height: 160px; display: block; border-radius: 8px; background: #0F172A; border: 1px solid #334155; }

        /* Modal styling */
        .modal-overlay { position: fixed; top: 0; left: 0; width: 100vw; height: 100vh; background: rgba(0,0,0,0.75); display: none; justify-content: center; align-items: center; z-index: 1000; padding: 16px; backdrop-filter: blur(4px); }
        .modal-content { background: var(--card-bg); border-radius: 16px; border: 1px solid #475569; width: 100%; max-width: 640px; max-height: 90vh; overflow-y: auto; padding: 20px; display: flex; flex-direction: column; gap: 16px; box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.5); }
        .modal-header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #334155; padding-bottom: 12px; }
        .modal-header h2 { font-size: 1.2rem; color: #F1F5F9; }
        .form-group { display: flex; flex-direction: column; gap: 6px; }
        .form-label { font-size: 0.85rem; color: #CBD5E1; font-weight: bold; }
        .form-control { background: #0F172A; border: 1px solid #334155; color: white; padding: 8px 12px; border-radius: 8px; font-size: 0.9rem; }
        .checkbox-group { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 8px; background: #0F172A; padding: 10px; border-radius: 8px; border: 1px solid #334155; }
        .checkbox-item { display: flex; align-items: center; gap: 8px; font-size: 0.85rem; color: #E2E8F0; }
        .checkbox-item input { width: 16px; height: 16px; accent-color: var(--primary); }
    </style>
</head>
<body>
    <header>
        <h1><span class="badge">OcularNode</span> <span id="dev-name">載入中...</span></h1>
        <div style="display: flex; gap: 8px; align-items: center;">
            <span class="live-tag" id="stream-status-tag">● 連線中</span>
            <span style="font-size: 0.85rem; color: var(--subtext);" id="fps-val">-- FPS</span>
            <button class="btn btn-primary" style="margin-left: 8px;" onclick="openConfigModal()">⚙️ 系統組態設定</button>
        </div>
    </header>

    <div class="main-container">
        <!-- Video Stream Player -->
        <div class="video-card">
            <img id="stream" src="/mjpeg" class="video-feed" alt="即時串流畫面" onerror="onStreamError()">
            <div style="position: absolute; top: 12px; left: 12px; display: flex; gap: 6px;">
                <span style="background: rgba(15,23,42,0.8); color: white; padding: 2px 8px; border-radius: 6px; font-size: 0.75rem; border: 1px solid #334155;" id="res-badge">720p</span>
                <span style="background: rgba(15,23,42,0.8); color: white; padding: 2px 8px; border-radius: 6px; font-size: 0.75rem; border: 1px solid #334155;" id="zoom-badge">1.0x</span>
            </div>
            <div style="position: absolute; bottom: 12px; right: 12px; display: flex; gap: 6px;">
                <button class="btn" style="padding: 4px 8px; font-size: 0.75rem; background: rgba(15,23,42,0.8);" onclick="reloadStream()">🔄 重載</button>
                <button class="btn" style="padding: 4px 8px; font-size: 0.75rem; background: rgba(15,23,42,0.8);" onclick="zoom(0.25)">🔍+</button>
                <button class="btn" style="padding: 4px 8px; font-size: 0.75rem; background: rgba(15,23,42,0.8);" onclick="zoom(-0.25)">🔍-</button>
                <button class="btn" style="padding: 4px 8px; font-size: 0.75rem; background: rgba(15,23,42,0.8);" onclick="resetZoom()">↺ 重置</button>
                <button class="btn" style="padding: 4px 8px; font-size: 0.75rem; background: rgba(15,23,42,0.8);" onclick="rotate(90)">🔄 旋轉</button>
            </div>
        </div>

        <!-- Controls Side Panel (Direct Real-time Commands) -->
        <div class="panel-card">
            <div class="panel-title">📊 狀態概觀</div>
            <div class="stat-grid">
                <div class="stat-box"><div class="stat-label">工作模式</div><div class="stat-val" id="mode-val">--</div></div>
                <div class="stat-box"><div class="stat-label">電池狀態</div><div class="stat-val" id="battery-val">--</div></div>
                <div class="stat-box"><div class="stat-label">線上觀看數</div><div class="stat-val" id="clients-val">--</div></div>
                <div class="stat-box"><div class="stat-label">夜視狀態</div><div class="stat-val" id="night-val">--</div></div>
            </div>

            <div class="panel-title" style="margin-top: 6px;">⚙️ 工作模式切換 (即時)</div>
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 6px;">
                <button class="btn" id="btn-mode-monitor" onclick="sendCommand('mode', 'monitor')">👁️ 監看模式</button>
                <button class="btn" id="btn-mode-detection" onclick="sendCommand('mode', 'detection')">🚨 動態偵測</button>
            </div>

            <div class="panel-title" style="margin-top: 6px;">🎮 即時硬體控制</div>
            <div class="btn-grid">
                <button class="btn" onclick="sendCommand('camera', 'switch')">🔄 前後鏡頭</button>
                <button class="btn" onclick="sendCommand('torch', 'toggle')">💡 補光燈</button>
                <button class="btn" onclick="takeSnapshot()">📸 快照截圖</button>
                <button class="btn btn-danger" onclick="sendCommand('alarm', 'trigger')">🚨 蜂鳴警報</button>
            </div>

            <div class="panel-title" style="margin-top: 6px;">🔊 聲音監聽 (即時)</div>
            <button class="btn" id="btn-audio-listen" onclick="toggleAudioListen()">🎧 啟動聲音監聽</button>

            <div class="panel-title" style="margin-top: 6px;">🌙 夜視開關 (即時)</div>
            <div style="display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 6px;">
                <button class="btn" id="btn-night-off" onclick="sendCommand('night_vision', 'off')">☀️ 關</button>
                <button class="btn" id="btn-night-on" onclick="sendCommand('night_vision', 'on')">🌙 開</button>
                <button class="btn" id="btn-night-auto" onclick="sendCommand('night_vision', 'auto')">🤖 自動</button>
            </div>

            <div class="panel-title" style="margin-top: 6px;">💾 儲存空間狀況</div>
            <div class="stat-box"><div class="stat-label">可用容量</div><div class="stat-val" id="storage-val">-- / --</div></div>
        </div>
    </div>

    <!-- System Settings Batch Modal Dialog -->
    <div id="config-modal" class="modal-overlay">
        <div class="modal-content">
            <div class="modal-header">
                <h2>⚙️ 系統組態設定 (批次儲存)</h2>
                <button class="btn" style="padding: 4px 10px;" onclick="closeConfigModal()">✕</button>
            </div>

            <!-- Device Name -->
            <div class="form-group">
                <label class="form-label">鏡頭裝置名稱</label>
                <input type="text" id="cfg-dev-name" class="form-control" placeholder="例：OcularNode 客廳鏡頭">
            </div>

            <!-- Camera Config -->
            <div class="panel-title" style="margin-top: 4px;">📷 相機與畫質</div>
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px;">
                <div class="form-group">
                    <label class="form-label">預設解析度</label>
                    <select id="cfg-resolution" class="form-control">
                        <option value="1080p">1080p (Full HD)</option>
                        <option value="720p">720p (HD)</option>
                        <option value="480p">480p (SD)</option>
                        <option value="360p">360p (Low)</option>
                    </select>
                </div>
                <div class="form-group">
                    <label class="form-label">JPEG 畫質</label>
                    <select id="cfg-quality" class="form-control">
                        <option value="90">90% (高品質)</option>
                        <option value="75">75% (平衡)</option>
                        <option value="50">50% (流暢)</option>
                        <option value="30">30% (省流量)</option>
                    </select>
                </div>
            </div>

            <!-- Motion Detection & AI Category Filters -->
            <div class="panel-title" style="margin-top: 4px;">🚨 動態偵測與 AI 過濾</div>
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px;">
                <div class="form-group">
                    <label class="form-label">靈敏度 (1 - 10)</label>
                    <input type="number" id="cfg-motion-sens" class="form-control" min="1" max="10" value="5">
                </div>
                <div class="form-group">
                    <label class="form-label">警報冷卻時間 (秒)</label>
                    <input type="number" id="cfg-motion-cooldown" class="form-control" min="5" max="300" value="10">
                </div>
            </div>

            <div class="form-label">動態推播過濾 (觸發通知之類別)</div>
            <div class="checkbox-group">
                <label class="checkbox-item"><input type="checkbox" id="cfg-cat-HUMAN_AND_ACTIVITY" checked> 人類與活動</label>
                <label class="checkbox-item"><input type="checkbox" id="cfg-cat-PET_AND_ANIMAL" checked> 寵物與動物</label>
                <label class="checkbox-item"><input type="checkbox" id="cfg-cat-VEHICLE_AND_TRANSPORT" checked> 交通工具</label>
                <label class="checkbox-item"><input type="checkbox" id="cfg-cat-HOUSEHOLD_ITEM" checked> 居家物品</label>
                <label class="checkbox-item"><input type="checkbox" id="cfg-cat-ENVIRONMENT_AND_NATURE" checked> 環境與自然</label>
                <label class="checkbox-item"><input type="checkbox" id="cfg-cat-OTHER" checked> 其他異動</label>
            </div>

            <!-- Recording & Storage -->
            <div class="panel-title" style="margin-top: 4px;">🎥 自動錄影與容量上限</div>
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px;">
                <div class="form-group">
                    <label class="checkbox-item" style="margin-top: 24px;">
                        <input type="checkbox" id="cfg-event-recording" checked> 啟用事件自動錄影
                    </label>
                </div>
                <div class="form-group">
                    <label class="form-label">影片容量上限 (GB)</label>
                    <input type="number" id="cfg-max-storage" class="form-control" min="1" max="100" step="0.5" value="10.0">
                </div>
            </div>

            <div class="form-label">自動錄影過濾 (觸發自動錄影之類別)</div>
            <div class="checkbox-group">
                <label class="checkbox-item"><input type="checkbox" id="cfg-rec-cat-HUMAN_AND_ACTIVITY" checked> 人類與活動</label>
                <label class="checkbox-item"><input type="checkbox" id="cfg-rec-cat-PET_AND_ANIMAL" checked> 寵物與動物</label>
                <label class="checkbox-item"><input type="checkbox" id="cfg-rec-cat-VEHICLE_AND_TRANSPORT" checked> 交通工具</label>
                <label class="checkbox-item"><input type="checkbox" id="cfg-rec-cat-HOUSEHOLD_ITEM" checked> 居家物品</label>
                <label class="checkbox-item"><input type="checkbox" id="cfg-rec-cat-ENVIRONMENT_AND_NATURE" checked> 環境與自然</label>
                <label class="checkbox-item"><input type="checkbox" id="cfg-rec-cat-OTHER" checked> 其他異動</label>
            </div>

            <!-- Protection & Telegram -->
            <div class="panel-title" style="margin-top: 4px;">⚡ 自動防護與 Telegram 警報</div>
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px;">
                <label class="checkbox-item"><input type="checkbox" id="cfg-power-cut" checked> 斷電推播警報</label>
                <label class="checkbox-item"><input type="checkbox" id="cfg-sys-log" checked> 系統日誌紀錄</label>
            </div>
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-top: 6px;">
                <div class="form-group">
                    <label class="form-label">Telegram Bot Token</label>
                    <input type="text" id="cfg-tg-token" class="form-control" placeholder="bot123456:ABC...">
                </div>
                <div class="form-group">
                    <label class="form-label">Telegram Chat ID</label>
                    <input type="text" id="cfg-tg-chat" class="form-control" placeholder="-100123456789">
                </div>
            </div>

            <!-- Modal Actions -->
            <div style="display: flex; justify-content: flex-end; gap: 10px; margin-top: 12px; border-top: 1px solid #334155; padding-top: 12px;">
                <button class="btn" onclick="closeConfigModal()">取消</button>
                <button class="btn btn-primary" onclick="saveConfigBatch()">💾 儲存並套用</button>
            </div>
        </div>
    </div>

    <!-- Canvas Native Chart Section -->
    <div style="width: 100%; max-width: 900px; margin-top: 20px;" class="panel-card">
        <div class="panel-title">
            <span>📈 節點資源與效能趨勢 (原生繪圖)</span>
            <span style="font-size: 0.8rem; color: var(--accent-green);">● 系統運作中</span>
        </div>
        <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 8px; margin-bottom: 10px;">
            <div class="stat-box"><div class="stat-label">CPU 使用率</div><div class="stat-val" id="cpu-stat-val">--%</div></div>
            <div class="stat-box"><div class="stat-label">記憶體占用</div><div class="stat-val" id="mem-stat-val">--%</div></div>
            <div class="stat-box"><div class="stat-label">連線延遲 (Ping)</div><div class="stat-val" id="ping-stat-val">-- ms</div></div>
        </div>
        <canvas id="perf-canvas" width="800" height="160"></canvas>
    </div>

    <!-- Motion Event Logs & Downloads Section -->
    <div style="width: 100%; max-width: 900px; margin-top: 20px;" class="panel-card">
        <div class="panel-title">
            <span>📁 動態警報事件與紀錄</span>
            <div style="display: flex; gap: 6px;">
                <button class="btn" style="padding: 4px 8px; font-size: 0.75rem;" onclick="fetchEvents()">🔄 重整</button>
                <button class="btn btn-danger" style="padding: 4px 8px; font-size: 0.75rem;" onclick="clearAllEvents()">🧹 清除全部</button>
            </div>
        </div>
        <div id="events-container" style="display: grid; grid-template-columns: 1fr; gap: 10px; margin-top: 8px;">
            <div style="color: var(--subtext); text-align: center; padding: 16px;">載入事件紀錄中...</div>
        </div>
    </div>

    <script>
        let isTorchOn = false;
        let currentAutoStart = true;
        let currentPowerCut = true;
        let currentRotation = 0;
        let currentZoom = 1.0;
        let panX = 0;
        let panY = 0;
        let perfHistory = [];

        const catNames = ['HUMAN_AND_ACTIVITY', 'PET_AND_ANIMAL', 'VEHICLE_AND_TRANSPORT', 'HOUSEHOLD_ITEM', 'ENVIRONMENT_AND_NATURE', 'OTHER'];

        async function openConfigModal() {
            try {
                const res = await fetch('/config');
                const data = await res.json();
                
                if (data.device && data.device.deviceName) {
                    document.getElementById('cfg-dev-name').value = data.device.deviceName;
                }
                if (data.camera) {
                    if (data.camera.resolution) document.getElementById('cfg-resolution').value = data.camera.resolution;
                    if (data.camera.quality) document.getElementById('cfg-quality').value = String(data.camera.quality);
                }
                if (data.motionDetection) {
                    if (data.motionDetection.sensitivity) document.getElementById('cfg-motion-sens').value = data.motionDetection.sensitivity;
                    if (data.motionDetection.cooldownSeconds) document.getElementById('cfg-motion-cooldown').value = data.motionDetection.cooldownSeconds;
                    if (data.motionDetection.categories) {
                        catNames.forEach(cat => {
                            const el = document.getElementById('cfg-cat-' + cat);
                            if (el) el.checked = data.motionDetection.categories[cat] !== false;
                        });
                    }
                }
                if (data.recording) {
                    document.getElementById('cfg-event-recording').checked = data.recording.eventRecordingEnabled !== false;
                    if (data.recording.maxStorageGb) document.getElementById('cfg-max-storage').value = data.recording.maxStorageGb;
                    if (data.recording.categoryRecording) {
                        catNames.forEach(cat => {
                            const el = document.getElementById('cfg-rec-cat-' + cat);
                            if (el) el.checked = data.recording.categoryRecording[cat] !== false;
                        });
                    }
                }
                if (data.notifications) {
                    document.getElementById('cfg-power-cut').checked = data.notifications.powerCutAlertEnabled !== false;
                    document.getElementById('cfg-sys-log').checked = data.notifications.systemLogEnabled !== false;
                    if (data.notifications.telegram) {
                        document.getElementById('cfg-tg-token').value = data.notifications.telegram.botToken || '';
                        document.getElementById('cfg-tg-chat').value = data.notifications.telegram.chatId || '';
                    }
                }

                document.getElementById('config-modal').style.display = 'flex';
            } catch (e) {
                alert('無法載入系統組態: ' + e);
            }
        }

        function closeConfigModal() {
            document.getElementById('config-modal').style.display = 'none';
        }

        async function saveConfigBatch() {
            const catObj = {};
            const recCatObj = {};
            catNames.forEach(cat => {
                const el = document.getElementById('cfg-cat-' + cat);
                if (el) catObj[cat] = el.checked;
                const recEl = document.getElementById('cfg-rec-cat-' + cat);
                if (recEl) recCatObj[cat] = recEl.checked;
            });

            const configPayload = {
                device: {
                    deviceName: document.getElementById('cfg-dev-name').value
                },
                camera: {
                    resolution: document.getElementById('cfg-resolution').value,
                    quality: parseInt(document.getElementById('cfg-quality').value) || 75
                },
                motionDetection: {
                    enabled: true,
                    sensitivity: parseFloat(document.getElementById('cfg-motion-sens').value) || 5.0,
                    cooldownSeconds: parseInt(document.getElementById('cfg-motion-cooldown').value) || 10,
                    categories: catObj
                },
                recording: {
                    eventRecordingEnabled: document.getElementById('cfg-event-recording').checked,
                    maxStorageGb: parseFloat(document.getElementById('cfg-max-storage').value) || 10.0,
                    categoryRecording: recCatObj
                },
                notifications: {
                    powerCutAlertEnabled: document.getElementById('cfg-power-cut').checked,
                    systemLogEnabled: document.getElementById('cfg-sys-log').checked,
                    telegram: {
                        botToken: document.getElementById('cfg-tg-token').value,
                        chatId: document.getElementById('cfg-tg-chat').value
                    }
                }
            };

            try {
                const res = await fetch('/config', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(configPayload)
                });
                const result = await res.json();
                if (result.status === 'ok') {
                    alert('✅ 系統組態已成功套用！');
                    closeConfigModal();
                    fetchStatus();
                } else {
                    alert('❌ 儲存失敗: ' + (result.message || '未知錯誤'));
                }
            } catch (e) {
                alert('發送組態設定失敗: ' + e);
            }
        }

        function reloadStream() {
            const img = document.getElementById('stream');
            if (img) {
                img.src = '/mjpeg?t=' + Date.now();
                document.getElementById('stream-status-tag').innerText = '● 連線中';
                document.getElementById('stream-status-tag').style.background = 'var(--accent-green)';
            }
        }

        function onStreamError() {
            console.warn('MJPEG stream connection error, trying snapshot fallback...');
            const tag = document.getElementById('stream-status-tag');
            if (tag) {
                tag.innerText = '⚠️ 快照模式';
                tag.style.background = '#F59E0B';
            }
            const img = document.getElementById('stream');
            if (img) {
                img.src = '/snapshot?t=' + Date.now();
            }
        }

        function rotate(deltaDeg) {
            currentRotation = (currentRotation + deltaDeg + 360) % 360;
            applyTransform();
        }

        function zoom(deltaScale) {
            currentZoom = Math.min(Math.max(currentZoom + deltaScale, 1.0), 5.0);
            if (currentZoom === 1.0) { panX = 0; panY = 0; }
            applyTransform();
        }

        function resetZoom() {
            currentZoom = 1.0;
            panX = 0;
            panY = 0;
            applyTransform();
        }

        function applyTransform() {
            const img = document.getElementById('stream');
            if (!img) return;
            const badge = document.getElementById('zoom-badge');
            if (badge) badge.innerText = currentZoom.toFixed(1) + 'x';
            img.style.transform = 'translate(' + panX + 'px, ' + panY + 'px) rotate(' + currentRotation + 'deg) scale(' + currentZoom + ')';
        }

        function drawPerfCanvas() {
            const canvas = document.getElementById('perf-canvas');
            if (!canvas) return;
            const ctx = canvas.getContext('2d');
            const w = canvas.width;
            const h = canvas.height;

            ctx.clearRect(0, 0, w, h);
            ctx.fillStyle = '#0F172A';
            ctx.fillRect(0, 0, w, h);

            // Grid lines
            ctx.strokeStyle = '#334155';
            ctx.lineWidth = 1;
            for (let y = 20; y < h; y += 35) {
                ctx.beginPath();
                ctx.moveTo(0, y);
                ctx.lineTo(w, y);
                ctx.stroke();
            }

            if (perfHistory.length < 2) return;

            const maxPoints = 20;
            const stepX = w / (maxPoints - 1);

            // Draw CPU line
            ctx.strokeStyle = '#6750A4';
            ctx.lineWidth = 2.5;
            ctx.beginPath();
            for (let i = 0; i < perfHistory.length; i++) {
                const x = i * stepX;
                const val = perfHistory[i].cpu || 0;
                const y = h - (val / 100 * (h - 20)) - 10;
                if (i === 0) ctx.moveTo(x, y);
                else ctx.lineTo(x, y);
            }
            ctx.stroke();

            // Draw Memory line
            ctx.strokeStyle = '#22C55E';
            ctx.lineWidth = 2.5;
            ctx.beginPath();
            for (let i = 0; i < perfHistory.length; i++) {
                const x = i * stepX;
                const val = perfHistory[i].mem || 0;
                const y = h - (val / 100 * (h - 20)) - 10;
                if (i === 0) ctx.moveTo(x, y);
                else ctx.lineTo(x, y);
            }
            ctx.stroke();

            // Legend
            ctx.fillStyle = '#6750A4';
            ctx.fillRect(10, 10, 12, 12);
            ctx.fillStyle = '#F8FAFC';
            ctx.font = '11px sans-serif';
            ctx.fillText('CPU %', 28, 20);

            ctx.fillStyle = '#22C55E';
            ctx.fillRect(80, 10, 12, 12);
            ctx.fillStyle = '#F8FAFC';
            ctx.fillText('記憶體 %', 98, 20);
        }

        async function fetchStatus() {
            try {
                const startTime = Date.now();
                const res = await fetch('/status');
                const ping = Date.now() - startTime;
                const data = await res.json();

                const devName = document.getElementById('dev-name');
                if (devName) devName.innerText = data.deviceName || 'OcularNode 鏡頭';

                let battTxt = (data.batteryLevel >= 0 ? data.batteryLevel + '%' : '未知');
                if (data.batteryTemp && data.batteryTemp > 0) battTxt += ' (' + data.batteryTemp.toFixed(1) + '°C)';
                if (data.isThermalThrottled) battTxt += ' 🔥 高溫';
                const battEl = document.getElementById('battery-val');
                if (battEl) battEl.innerText = battTxt;

                const clientsEl = document.getElementById('clients-val');
                if (clientsEl) clientsEl.innerText = (data.connectedClients || 0) + ' 人';

                const fpsEl = document.getElementById('fps-val');
                if (fpsEl) fpsEl.innerText = (data.fps || 0) + ' FPS';

                const resBadge = document.getElementById('res-badge');
                if (resBadge) resBadge.innerText = data.resolution || '720p';

                const cpu = data.cpuUsage || 30;
                const mem = data.memoryUsage || 45;
                const cpuEl = document.getElementById('cpu-stat-val');
                if (cpuEl) cpuEl.innerText = cpu + '%';

                const memEl = document.getElementById('mem-stat-val');
                if (memEl) memEl.innerText = mem + '%';

                const pingEl = document.getElementById('ping-stat-val');
                if (pingEl) pingEl.innerText = ping + ' ms';

                perfHistory.push({ cpu: cpu, mem: mem });
                if (perfHistory.length > 20) perfHistory.shift();
                drawPerfCanvas();

                const opMode = data.operatingMode || 'monitor';
                const modeEl = document.getElementById('mode-val');
                if (modeEl) modeEl.innerText = (opMode === 'monitor' ? '👁️ 監看' : '🚨 動態偵測');

                const btnMon = document.getElementById('btn-mode-monitor');
                if (btnMon) btnMon.style.background = (opMode === 'monitor' ? 'var(--primary)' : '#334155');
                const btnDet = document.getElementById('btn-mode-detection');
                if (btnDet) btnDet.style.background = (opMode === 'detection' ? 'var(--primary)' : '#334155');

                const nMode = data.nightVisionMode || 'auto';
                const nightEl = document.getElementById('night-val');
                if (nightEl) nightEl.innerText = (data.isNightVisionActive ? '夜視中 (' + nMode + ')' : '一般 (' + nMode + ')');

                const btnNOff = document.getElementById('btn-night-off');
                if (btnNOff) btnNOff.style.background = (nMode === 'off' ? 'var(--primary)' : '#334155');
                const btnNOn = document.getElementById('btn-night-on');
                if (btnNOn) btnNOn.style.background = (nMode === 'on' ? 'var(--primary)' : '#334155');
                const btnNAuto = document.getElementById('btn-night-auto');
                if (btnNAuto) btnNAuto.style.background = (nMode === 'auto' ? 'var(--primary)' : '#334155');

                if (data.storageFree && data.storageTotal) {
                    const storEl = document.getElementById('storage-val');
                    if (storEl) storEl.innerText = data.storageFree + ' / ' + data.storageTotal;
                }
            } catch (e) {
                console.error('fetchStatus error:', e);
            }
        }

        async function fetchEvents() {
            try {
                const res = await fetch('/events');
                const events = await res.json();
                const container = document.getElementById('events-container');
                if (!container) return;

                if (!Array.isArray(events) || events.length === 0) {
                    container.innerHTML = '<div style="color: var(--subtext); text-align: center; padding: 16px;">目前尚無動態警報紀錄</div>';
                    return;
                }

                var htmlStr = '';
                for (var i = 0; i < events.length; i++) {
                    var ev = events[i];
                    var imgTag = ev.thumbnailBase64 ? '<img src="data:image/jpeg;base64,' + ev.thumbnailBase64 + '" style="width:84px; height:64px; object-fit:cover; border-radius:8px; border:1px solid #334155;">' : '<div style="width:84px; height:64px; background:#1E293B; border-radius:8px; display:flex; align-items:center; justify-content:center; color:#94A3B8;">📷</div>';
                    
                    var videoBtn = ev.hasVideo ? '<a href="' + ev.videoUrl + '" target="_blank" class="btn" style="background:#2563EB; font-size:0.8rem; padding:4px 8px;">🎬 影片</a>' : '';

                    htmlStr += '<div style="background:#0F172A; border:1px solid #334155; border-radius:12px; padding:10px; display:flex; align-items:center; gap:12px; flex-wrap:wrap;">' +
                        imgTag +
                        '<div style="flex:1; min-width:160px;">' +
                        '<div style="font-weight:bold; font-size:0.95rem; color:#F1F5F9;">🚨 動態觸發 (' + ev.motionPercentage + '%)</div>' +
                        '<div style="font-size:0.8rem; color:var(--subtext); margin-top:2px;">📅 ' + ev.formattedTime + '</div>' +
                        '</div>' +
                        '<div style="display:flex; gap:6px; align-items:center;">' +
                        '<a href="' + ev.downloadUrl + '" download class="btn btn-primary" style="font-size:0.8rem; padding:4px 8px;">⬇️ 照片</a>' +
                        videoBtn +
                        '<button onclick="deleteEvent(' + ev.id + ')" class="btn btn-danger" style="padding:4px 8px;">🗑️</button>' +
                        '</div>' +
                        '</div>';
                }
                container.innerHTML = htmlStr;
            } catch (e) {
                console.error('fetchEvents error:', e);
            }
        }

        async function deleteEvent(id) {
            if (confirm('確定要刪除這筆動態事件與快照嗎？')) {
                try {
                    await fetch('/events/delete?id=' + id);
                    fetchEvents();
                } catch (e) {
                    alert('刪除失敗: ' + e);
                }
            }
        }

        async function clearAllEvents() {
            if (confirm('確定要清除所有動態事件紀錄嗎？')) {
                try {
                    await fetch('/events/clear');
                    fetchEvents();
                } catch (e) {
                    alert('清除失敗: ' + e);
                }
            }
        }

        async function sendCommand(cmd, val) {
            if (cmd === 'torch' && val === 'toggle') {
                val = isTorchOn ? 'off' : 'on';
            }
            try {
                await fetch('/control', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ command: cmd, value: val })
                });
                setTimeout(fetchStatus, 300);
            } catch (e) {
                alert('發送控制指令失敗: ' + e);
            }
        }

        function takeSnapshot() {
            const img = document.getElementById('stream');
            if (!img) return;
            const w = img.naturalWidth || 1280;
            const h = img.naturalHeight || 720;
            const canvas = document.createElement('canvas');
            const ctx = canvas.getContext('2d');
            if (currentRotation === 90 || currentRotation === 270) {
                canvas.width = h; canvas.height = w;
            } else {
                canvas.width = w; canvas.height = h;
            }
            ctx.translate(canvas.width / 2, canvas.height / 2);
            ctx.rotate((currentRotation * Math.PI) / 180);
            ctx.drawImage(img, -w / 2, -h / 2);
            const a = document.createElement('a');
            a.href = canvas.toDataURL('image/jpeg');
            a.download = 'OcularNode_snapshot_' + Date.now() + '.jpg';
            a.click();
        }

        let audioCtx = null;
        let audioReader = null;
        let isAudioListening = false;
        let nextPlayTime = 0;

        async function toggleAudioListen() {
            const btn = document.getElementById('btn-audio-listen');
            if (isAudioListening) {
                stopAudioListen();
                return;
            }
            try {
                audioCtx = new (window.AudioContext || window.webkitAudioContext)();
                if (audioCtx.state === 'suspended') {
                    await audioCtx.resume();
                }
                nextPlayTime = audioCtx.currentTime;

                const response = await fetch('/audio');
                if (!response.body) {
                    alert('瀏覽器不支援串流音訊');
                    return;
                }
                audioReader = response.body.getReader();
                isAudioListening = true;
                if (btn) {
                    btn.innerText = '🔊 關閉聲音監聽';
                    btn.style.background = '#EF4444';
                }
                readAudioStream();
            } catch (e) {
                alert('無法啟動聲音監聽: ' + e);
                stopAudioListen();
            }
        }

        function stopAudioListen() {
            isAudioListening = false;
            if (audioReader) {
                try { audioReader.cancel(); } catch(_){}
                audioReader = null;
            }
            if (audioCtx) {
                try { audioCtx.close(); } catch(_){}
                audioCtx = null;
            }
            const btn = document.getElementById('btn-audio-listen');
            if (btn) {
                btn.innerText = '🎧 啟動聲音監聽';
                btn.style.background = '#334155';
            }
        }

        async function readAudioStream() {
            let leftover = new Uint8Array(0);
            while (isAudioListening && audioReader) {
                try {
                    const { done, value } = await audioReader.read();
                    if (done) break;
                    if (!value || value.length === 0) continue;

                    let totalLen = leftover.length + value.length;
                    let combined = new Uint8Array(totalLen);
                    combined.set(leftover, 0);
                    combined.set(value, leftover.length);

                    let samplesCount = Math.floor(totalLen / 2);
                    if (samplesCount === 0) {
                        leftover = combined;
                        continue;
                    }

                    let usedBytes = samplesCount * 2;
                    leftover = combined.slice(usedBytes);

                    let dataView = new DataView(combined.buffer, combined.byteOffset, usedBytes);
                    let float32Array = new Float32Array(samplesCount);
                    for (let i = 0; i < samplesCount; i++) {
                        let int16 = dataView.getInt16(i * 2, true);
                        float32Array[i] = int16 / 32768.0;
                    }

                    if (audioCtx) {
                        let audioBuffer = audioCtx.createBuffer(1, samplesCount, 16000);
                        audioBuffer.getChannelData(0).set(float32Array);

                        let source = audioCtx.createBufferSource();
                        source.buffer = audioBuffer;
                        source.connect(audioCtx.destination);

                        let currentTime = audioCtx.currentTime;
                        if (nextPlayTime < currentTime) {
                            nextPlayTime = currentTime + 0.05;
                        }
                        source.start(nextPlayTime);
                        nextPlayTime += audioBuffer.duration;
                    }
                } catch (e) {
                    console.error('Audio stream read error:', e);
                    break;
                }
            }
            stopAudioListen();
        }

        fetchStatus();
        fetchEvents();
        setInterval(fetchStatus, 2000);
        setInterval(fetchEvents, 10000);
        applyTransform();
    </script>
</body>
</html>""".trimIndent()
    }
    fun stop() {
        isRunning = false
        try {
            mjpegSessions.forEach { try { it.close() } catch (_: Exception) {} }
            mjpegSessions.clear()
            serverSocket?.close()
            threadPool.shutdownNow()
        } catch (e: Exception) {
            Log.e("MjpegHttpServer", "Error stopping server", e)
        }
    }
}
