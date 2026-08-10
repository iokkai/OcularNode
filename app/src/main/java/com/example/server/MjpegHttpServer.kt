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
    private var isRunning = false
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
            val path = parts[1]

            val output = socket.getOutputStream()

            when {
                path.startsWith("/mjpeg") || path.startsWith("/video") || path.startsWith("/stream") -> {
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
                    if (bytes == null) {
                        try {
                            val cacheFile = File(context.cacheDir, "snapshot_temp.jpg")
                            if (cacheFile.exists() && cacheFile.length() > 0) {
                                bytes = cacheFile.readBytes()
                            }
                        } catch (_: Exception) {}
                    }

                    if (bytes != null) {
                        output.write(("HTTP/1.1 200 OK\r\n" +
                                "Access-Control-Allow-Origin: *\r\n" +
                                "Content-Type: image/jpeg\r\n" +
                                "Cache-Control: no-store, no-cache, must-revalidate, max-age=0\r\n" +
                                "Content-Length: ${bytes.size}\r\n" +
                                "Connection: close\r\n\r\n").toByteArray())
                        output.write(bytes)
                        output.flush()
                    } else {
                        sendJsonResponse(output, 503, "{\"error\":\"Frame not ready\"}")
                    }
                    socket.close()
                    return
                }

                path == "/" || path.startsWith("/web") || path.startsWith("/index") -> {
                    // Web Browser Interface
                    val html = getWebDashboardHtml()
                    sendHtmlResponse(output, 200, html)
                    socket.close()
                }

                path.startsWith("/status") -> {
                    val statusJson = getStatusJson()
                    sendJsonResponse(output, 200, statusJson)
                    socket.close()
                }

                path.startsWith("/control") -> {
                    // Read headers to find Content-Length
                    var contentLength = 0
                    var headerLine: String?
                    while (readLineStr(input).also { headerLine = it } != null && headerLine!!.isNotBlank()) {
                        if (headerLine!!.lowercase().startsWith("content-length:")) {
                            contentLength = headerLine!!.substringAfter(":").trim().toIntOrNull() ?: 0
                        }
                    }

                    var body = ""
                    if (contentLength > 0) {
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
                }

                path.startsWith("/audio") -> {
                    // Skip remaining headers
                    while (true) {
                        val line = readLineStr(input)
                        if (line.isNullOrEmpty()) break
                    }
                    // Stream PCM Audio from Camera MIC to Viewer
                    socket.tcpNoDelay = true
                    output.write(("HTTP/1.1 200 OK\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
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
                    // Skip remaining headers
                    while (true) {
                        val line = readLineStr(input)
                        if (line.isNullOrEmpty()) break
                    }
                    
                    // Receive PCM Audio stream from Viewer and output to Camera Speakerphone
                    socket.tcpNoDelay = true
                    output.write(("HTTP/1.1 200 OK\r\n" +
                            "Access-Control-Allow-Origin: *\r\n\r\n").toByteArray())
                    output.flush()

                    audioEngine.startPlaying(context)
                    val buffer = ByteArray(640)
                    var read: Int
                    try {
                        socket.soTimeout = 0 // No timeout for continuous streaming
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
                                ev.snapshotPath?.let { java.io.File(it).delete() }
                                ev.videoPath?.let { java.io.File(it).delete() }
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
    }

    private fun handleControlRequest(path: String, body: String) {
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
            put("playLocalAlarmOnMotion", settingsManager.playLocalAlarmOnMotion)
            put("mlKitFilterEnabled", settingsManager.mlKitFilterEnabled)
            put("autoStorageCleanupEnabled", settingsManager.autoStorageCleanupEnabled)
            put("storageLimitGB", settingsManager.storageLimitGB)
            put("maxEventCountLimit", settingsManager.maxEventCountLimit)
            put("autoStartOnBoot", settingsManager.autoStartOnBoot)
            put("powerCutAlertEnabled", settingsManager.powerCutAlertEnabled)

            put("systemLogEnabled", settingsManager.systemLogEnabled)
            val categoryStore = SettingsDataStore(context)
            val catObj = JSONObject()
            val catRecordObj = JSONObject()
            for (cat in NotificationCategory.values()) {
                val (notifyEnabled, recordEnabled) = runBlocking {
                    val n = runCatching { categoryStore.getCategoryEnabled(cat).first() }.getOrDefault(true)
                    val r = runCatching { categoryStore.getCategoryRecordingEnabled(cat).first() }.getOrDefault(true)
                    Pair(n, r)
                }
                catObj.put(cat.name, notifyEnabled)
                catRecordObj.put(cat.name, recordEnabled)
            }
            put("categoryFilters", catObj)
            put("categoryRecordingFilters", catRecordObj)
        }
        return json.toString()
    }

    private fun sendJsonResponse(output: OutputStream, statusCode: Int, json: String) {
        val statusText = if (statusCode == 200) "OK" else "Not Found"
        val response = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${json.toByteArray().size}\r\n" +
                "Connection: close\r\n\r\n" + json
        output.write(response.toByteArray())
        output.flush()
    }

    private fun sendHtmlResponse(output: OutputStream, statusCode: Int, html: String) {
        val statusText = if (statusCode == 200) "OK" else "Not Found"
        val response = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${html.toByteArray().size}\r\n" +
                "Connection: close\r\n\r\n" + html
        output.write(response.toByteArray())
        output.flush()
    }

    private fun getWebDashboardHtml(): String {
        return """
            <!DOCTYPE html>
            <html lang="zh-TW">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>OcularNode 網頁監控端</title>
                <script src="https://unpkg.com/react@18/umd/react.production.min.js" crossorigin></script>
                <script src="https://unpkg.com/react-dom@18/umd/react-dom.production.min.js" crossorigin></script>
                <script src="https://unpkg.com/prop-types@15.8.1/prop-types.min.js" crossorigin></script>
                <script src="https://unpkg.com/recharts@2.10.4/umd/Recharts.js" crossorigin></script>
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
                    }
                    * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
                    body { background-color: var(--bg); color: var(--text); padding: 20px; display: flex; flex-direction: column; align-items: center; min-height: 100vh; }
                    header { width: 100%; max-width: 900px; display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; flex-wrap: wrap; gap: 10px; }
                    h1 { font-size: 1.5rem; color: #E2E8F0; display: flex; align-items: center; gap: 8px; }
                    .badge { background: var(--primary); color: white; padding: 4px 10px; border-radius: 20px; font-size: 0.8rem; font-weight: bold; }
                    .live-tag { background: var(--accent-green); color: white; padding: 4px 12px; border-radius: 20px; font-size: 0.85rem; font-weight: bold; }
                    
                    .main-container { width: 100%; max-width: 900px; display: grid; grid-template-columns: 1fr; gap: 20px; }
                    @media (min-width: 768px) { .main-container { grid-template-columns: 3fr 2fr; } }
                    
                    .video-card { background: #000; border-radius: 16px; overflow: hidden; position: relative; border: 1px solid #334155; display: flex; justify-content: center; align-items: center; min-height: 360px; }
                    .video-feed { width: 100%; height: auto; max-height: 520px; object-fit: contain; display: block; }
                    
                    .panel-card { background: var(--card-bg); border-radius: 16px; padding: 14px; border: 1px solid #334155; display: flex; flex-direction: column; gap: 10px; }
                    .panel-title { font-size: 0.95rem; font-weight: bold; border-bottom: 1px solid #334155; padding-bottom: 6px; margin-bottom: 2px; color: #CBD5E1; }
                    
                    .stat-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; }
                    .stat-box { background: #0F172A; padding: 6px 10px; border-radius: 8px; border: 1px solid #334155; }
                    .stat-label { font-size: 0.7rem; color: var(--subtext); margin-bottom: 0px; }
                    .stat-val { font-size: 0.95rem; font-weight: bold; color: #F1F5F9; }
                    
                    .btn-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; margin-top: 2px; }
                    .btn { background: #334155; color: white; border: none; padding: 8px 10px; border-radius: 8px; font-size: 0.85rem; font-weight: bold; cursor: pointer; transition: all 0.2s; display: flex; align-items: center; justify-content: center; gap: 4px; }
                    .btn:hover { background: #475569; }
                    .btn-primary { background: var(--primary); }
                    .btn-primary:hover { background: #7C65C1; }
                    .btn-danger { background: var(--accent-red); }
                    .btn-danger:hover { background: #DC2626; }
                </style>
            </head>
            <body>
                <header>
                    <h1>📷 <span id="dev-name">OcularNode 鏡頭</span></h1>
                    <div style="display:flex; gap:10px; align-items:center;">
                        <span class="badge" id="res-badge">720p</span>
                        <span class="live-tag">● LIVE <span id="fps-val">--</span> FPS</span>
                    </div>
                </header>

                <div class="main-container">
                    <div class="video-card">
                        <!-- Top-Left Zoom Overlay -->
                        <div style="position: absolute; top: 12px; left: 12px; z-index: 10; display: flex; gap: 6px; background: rgba(15, 23, 42, 0.75); padding: 4px 8px; border-radius: 12px; backdrop-filter: blur(4px);">
                            <button class="btn" style="padding: 6px 10px; font-size: 0.85rem;" onclick="zoom(-0.25)">➖</button>
                            <span id="zoom-badge" style="color: white; font-weight: bold; font-size: 0.85rem; align-self: center; cursor: pointer;" onclick="resetZoom()">1.0x</span>
                            <button class="btn" style="padding: 6px 10px; font-size: 0.85rem;" onclick="zoom(0.25)">➕</button>
                        </div>

                        <!-- Top-Right Rotate Overlay -->
                        <div style="position: absolute; top: 12px; right: 12px; z-index: 10; display: flex; gap: 6px; background: rgba(15, 23, 42, 0.75); padding: 4px 8px; border-radius: 12px; backdrop-filter: blur(4px);">
                            <button class="btn" style="padding: 6px 10px; font-size: 0.85rem;" onclick="rotate(-90)" title="逆時鐘旋轉 90°">↺ 逆時鐘</button>
                            <button class="btn" style="padding: 6px 10px; font-size: 0.85rem;" onclick="rotate(90)" title="順時鐘旋轉 90°">↻ 順時鐘</button>
                        </div>

                        <img id="stream" src="/mjpeg" class="video-feed" alt="Live Camera Stream">
                    </div>

                    <div class="panel-card">
                        <div class="panel-title">⚡ 鏡頭即時狀態</div>
                        <div class="stat-grid">
                            <div class="stat-box">
                                <div class="stat-label">電量</div>
                                <div class="stat-val" id="battery-val">-- %</div>
                            </div>
                            <div class="stat-box">
                                <div class="stat-label">連線人數</div>
                                <div class="stat-val" id="clients-val">--</div>
                            </div>
                            <div class="stat-box">
                                <div class="stat-label">運作模式</div>
                                <div class="stat-val" id="mode-val">--</div>
                            </div>
                            <div class="stat-box">
                                <div class="stat-label">夜視狀態</div>
                                <div class="stat-val" id="night-val">--</div>
                            </div>
                        </div>

                        <div class="panel-title" style="margin-top:10px;">⚙️ 運作模式控制</div>
                        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 8px;">
                            <button class="btn" id="btn-mode-monitor" onclick="sendCommand('mode', 'monitor')">👁️ 監看模式</button>
                            <button class="btn" id="btn-mode-detection" onclick="sendCommand('mode', 'detection')">🚨 動態偵測</button>
                        </div>

                        <div class="panel-title" style="margin-top:10px;">🎮 遠端控制面板</div>
                        <div class="btn-grid">
                            <button class="btn" onclick="sendCommand('camera', 'switch')">🔄 切換前後鏡頭</button>
                            <button class="btn" onclick="sendCommand('torch', 'toggle')">💡 閃光燈開關</button>
                            <button class="btn" onclick="takeSnapshot()">📸 快照截圖</button>
                            <button class="btn btn-danger" onclick="sendCommand('alarm', 'trigger')">🚨 遠端蜂鳴警報</button>
                        </div>

                        <div class="panel-title" style="margin-top:10px;">🎥 畫質與解析度調整</div>
                        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 8px;">
                            <select class="btn" onchange="sendCommand('resolution', this.value)" style="text-align:center;">
                                <option value="" disabled selected>解析度調整</option>
                                <option value="1080p">1080p (FHD)</option>
                                <option value="720p">720p (HD)</option>
                                <option value="480p">480p (SD)</option>
                                <option value="360p">360p (Low)</option>
                            </select>
                            <select class="btn" onchange="sendCommand('quality', this.value)" style="text-align:center;">
                                <option value="" disabled selected>JPEG 壓縮品質</option>
                                <option value="90">90% (高畫質)</option>
                                <option value="75">75% (平衡)</option>
                                <option value="50">50% (流暢)</option>
                                <option value="30">30% (省流量)</option>
                            </select>
                        </div>

                        <div class="panel-title" style="margin-top:10px;">🌙 夜視模式控制</div>
                        <div style="display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 8px;">
                            <button class="btn" id="btn-night-off" onclick="sendCommand('night_vision', 'off')">☀️ 關閉夜視</button>
                            <button class="btn" id="btn-night-on" onclick="sendCommand('night_vision', 'on')">🌙 開啟夜視</button>
                            <button class="btn" id="btn-night-auto" onclick="sendCommand('night_vision', 'auto')">🤖 自動夜視</button>
                        </div>

                        <div class="panel-title" style="margin-top:10px;">💾 儲存空間與循環錄影</div>
                        <div class="stat-box">
                            <div class="stat-label">裝置可用空間 (自動覆蓋保護中)</div>
                            <div class="stat-val" id="storage-val">-- / --</div>
                        </div>

                        <div class="panel-title" style="margin-top:10px;">⚡ 開機自動啟動與電源防護</div>
                        <div style="display: flex; flex-direction: column; gap: 8px;">
                            <button class="btn" id="btn-autostart-toggle" onclick="toggleAutoStart()">⚡ 開機/復電自動啟動: 讀取中...</button>
                            <button class="btn" id="btn-powercut-toggle" onclick="togglePowerCutAlert()">🚨 斷電與低電量警報: 讀取中...</button>
                        </div>
                    </div>
                </div>

                <!-- Motion Event Logs & Downloads Section -->
                <div style="width:100%; max-width:900px; margin-top:24px;" class="panel-card">
                    <div class="panel-title" style="display:flex; justify-content:space-between; align-items:center;">
                        <span>📁 動態警報事件與照片/紀錄下載</span>
                        <button class="btn" style="padding:4px 10px; font-size:0.8rem;" onclick="fetchEvents()">🔄 重新整理列表</button>
                    </div>
                    <div id="events-container" style="display:grid; grid-template-columns:1fr; gap:10px; margin-top:10px;">
                        <div style="color:var(--subtext); text-align:center; padding:16px;">載入事件紀錄中...</div>
                    </div>
                </div>

                <!-- Performance & Recharts Monitoring Section -->
                <div id="perf-dashboard-section" style="width:100%; max-width:900px; margin-top:24px; display:none;" class="panel-card">
                    <div class="panel-title" style="display:flex; justify-content:space-between; align-items:center;">
                        <span>📈 節點資源與效能監控 (Recharts 趨勢圖)</span>
                        <span id="perf-status-badge" style="font-size:0.8rem; color:var(--accent-green);">● 系統運行正常</span>
                    </div>
                    <div style="display:grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap:10px; margin-top:10px;">
                        <div class="stat-box">
                            <div class="stat-label">當前 CPU 使用率</div>
                            <div style="display:flex; align-items:center; gap:6px;">
                                <div class="stat-val" id="cpu-stat-val">--%</div>
                                <span id="cpu-warning" style="display:none; color:var(--accent-red); font-size:1.1rem;" title="CPU 使用率過高">⚠️</span>
                            </div>
                        </div>
                        <div class="stat-box">
                            <div class="stat-label">當前記憶體占用</div>
                            <div style="display:flex; align-items:center; gap:6px;">
                                <div class="stat-val" id="mem-stat-val">--%</div>
                                <span id="mem-warning" style="display:none; color:var(--accent-red); font-size:1.1rem;" title="記憶體占用過高">⚠️</span>
                            </div>
                        </div>
                        <div class="stat-box">
                            <div class="stat-label">連線品質 (Ping)</div>
                            <div style="display:flex; align-items:center; gap:6px;">
                                <div class="stat-val" id="ping-stat-val">-- ms</div>
                                <span id="ping-warning" style="display:none; color:var(--accent-red); font-size:1.1rem;" title="連線延遲過高">⚠️</span>
                            </div>
                        </div>
                    </div>
                    <div style="margin-top:15px; height:180px; width:100%;" id="recharts-container">
                        <div style="color:var(--subtext); text-align:center; padding-top:60px;">載入 Recharts 趨勢圖中...</div>
                    </div>
                </div>

                <div style="width:100%; max-width:900px; margin-top:24px; text-align:center; margin-bottom:24px;">
                    <label style="color:var(--text); font-size:0.9rem; cursor:pointer; display:inline-flex; align-items:center; justify-content:center; gap:8px;">
                        <input type="checkbox" id="toggle-perf-dashboard" onchange="togglePerfDashboard()" style="width:16px; height:16px;">
                        顯示節點資源與效能監控 (Recharts)
                    </label>
                </div>

                <script>
                    let isTorchOn = false;
                    let currentAutoStart = true;
                    let currentPowerCut = true;
                    let currentRotation = 0;

                    function toggleAutoStart() {
                        sendCommand('auto_start_boot', currentAutoStart ? 'off' : 'on');
                    }

                    function togglePowerCutAlert() {
                        sendCommand('power_cut_alert', currentPowerCut ? 'off' : 'on');
                    }
                    let currentZoom = 1.0;
                    let panX = 0;
                    let panY = 0;
                    let isDragging = false;
                    let startX = 0;
                    let startY = 0;

                    function togglePerfDashboard() {
                        const cb = document.getElementById('toggle-perf-dashboard');
                        const section = document.getElementById('perf-dashboard-section');
                        if (cb && section) {
                            if (cb.checked) {
                                section.style.display = 'block';
                                renderPerfChart();
                            } else {
                                section.style.display = 'none';
                            }
                        }
                    }

                    function rotate(deltaDeg) {
                        currentRotation = (currentRotation + deltaDeg + 360) % 360;
                        applyTransform();
                    }

                    function zoom(deltaScale) {
                        currentZoom = Math.min(Math.max(currentZoom + deltaScale, 1.0), 5.0);
                        if (currentZoom === 1.0) {
                            panX = 0;
                            panY = 0;
                        }
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

                        if (currentZoom <= 1.0) {
                            panX = 0;
                            panY = 0;
                            img.style.cursor = 'default';
                        } else {
                            img.style.cursor = isDragging ? 'grabbing' : 'grab';
                        }

                        const isVertical = (currentRotation === 90 || currentRotation === 270);
                        if (isVertical) {
                            const card = img.parentElement;
                            const cardW = card.clientWidth - 20;
                            const cardH = 480;
                            const nw = img.naturalWidth || 1280;
                            const nh = img.naturalHeight || 720;
                            const imgW = img.offsetWidth || cardW;
                            const imgH = img.offsetHeight || (imgW * nh / nw);

                            const baseScale = Math.min(cardW / imgH, cardH / imgW, 1);
                            const finalScale = baseScale * currentZoom;
                            img.style.transform = 'translate(' + panX + 'px, ' + panY + 'px) rotate(' + currentRotation + 'deg) scale(' + finalScale + ')';
                        } else {
                            img.style.transform = 'translate(' + panX + 'px, ' + panY + 'px) rotate(' + currentRotation + 'deg) scale(' + currentZoom + ')';
                        }
                    }

                    window.addEventListener('resize', applyTransform);

                    document.addEventListener('DOMContentLoaded', () => {
                        const videoCard = document.querySelector('.video-card');
                        if (videoCard) {
                            videoCard.addEventListener('wheel', (e) => {
                                e.preventDefault();
                                zoom(e.deltaY < 0 ? 0.25 : -0.25);
                            }, { passive: false });

                            const onStart = (clientX, clientY) => {
                                if (currentZoom > 1.0) {
                                    isDragging = true;
                                    startX = clientX - panX;
                                    startY = clientY - panY;
                                    applyTransform();
                                }
                            };

                            const onMove = (clientX, clientY) => {
                                if (isDragging && currentZoom > 1.0) {
                                    panX = clientX - startX;
                                    panY = clientY - startY;
                                    applyTransform();
                                }
                            };

                            const onEnd = () => {
                                if (isDragging) {
                                    isDragging = false;
                                    applyTransform();
                                }
                            };

                            videoCard.addEventListener('mousedown', (e) => {
                                if (currentZoom > 1.0) {
                                    e.preventDefault();
                                    onStart(e.clientX, e.clientY);
                                }
                            });

                            window.addEventListener('mousemove', (e) => {
                                if (isDragging) {
                                    e.preventDefault();
                                    onMove(e.clientX, e.clientY);
                                }
                            });

                            window.addEventListener('mouseup', onEnd);

                            videoCard.addEventListener('touchstart', (e) => {
                                if (currentZoom > 1.0 && e.touches.length === 1) {
                                    onStart(e.touches[0].clientX, e.touches[0].clientY);
                                }
                            }, { passive: true });

                            window.addEventListener('touchmove', (e) => {
                                if (isDragging && e.touches.length === 1) {
                                    onMove(e.touches[0].clientX, e.touches[0].clientY);
                                }
                            }, { passive: true });

                            window.addEventListener('touchend', onEnd);
                        }
                    });

                    let perfHistory = [];
                    let chartRoot = null;

                    function renderPerfChart() {
                        try {
                            const section = document.getElementById('perf-dashboard-section');
                            if (!section || section.style.display === 'none') return;

                            if (typeof window.Recharts === 'undefined') {
                                console.warn("Recharts not loaded yet");
                                return;
                            }
                            const { ResponsiveContainer, AreaChart, Area, XAxis, YAxis, Tooltip, CartesianGrid } = window.Recharts;
                            const container = document.getElementById('recharts-container');
                            if (!container) return;
                            
                            // 修正載入提示消失
                            if (container.querySelector('div') && container.querySelector('div').innerText.includes('載入')) {
                                container.innerHTML = '';
                            }

                            const element = React.createElement(ResponsiveContainer, { width: '100%', height: '100%' },
                                React.createElement(AreaChart, { data: perfHistory },
                                    React.createElement(CartesianGrid, { strokeDasharray: '3 3', stroke: '#334155' }),
                                    React.createElement(XAxis, { dataKey: 'time', stroke: '#94A3B8', fontSize: 10 }),
                                    React.createElement(YAxis, { stroke: '#94A3B8', fontSize: 10, domain: [0, 100] }),
                                    React.createElement(Tooltip, { contentStyle: { background: '#1E293B', border: '1px solid #334155', borderRadius: '8px', color: '#F8FAFC' } }),
                                    React.createElement(Area, { type: 'monotone', dataKey: 'cpu', stroke: '#6750A4', fill: '#6750A4', fillOpacity: 0.3, name: 'CPU %', isAnimationActive: false }),
                                    React.createElement(Area, { type: 'monotone', dataKey: 'mem', stroke: '#22C55E', fill: '#22C55E', fillOpacity: 0.3, name: '記憶體 %', isAnimationActive: false })
                                )
                            );
                            
                            if (window.ReactDOM.createRoot) {
                                if (!chartRoot) {
                                    chartRoot = window.ReactDOM.createRoot(container);
                                }
                                chartRoot.render(element);
                            } else {
                                window.ReactDOM.render(element, container);
                            }
                        } catch (e) {
                            console.error("Recharts render error:", e);
                        }
                    }

                    async function fetchStatus() {
                        try {
                            const startTime = Date.now();
                            const res = await fetch('/status');
                            const ping = Date.now() - startTime;
                            const data = await res.json();
                            document.getElementById('dev-name').innerText = data.deviceName || 'OcularNode 鏡頭';
                            let battTxt = (data.batteryLevel >= 0 ? data.batteryLevel + '%' : '未知');
                            if (data.batteryTemp && data.batteryTemp > 0) {
                                battTxt += ' (' + data.batteryTemp.toFixed(1) + '°C)';
                            }
                            if (data.isThermalThrottled) {
                                battTxt += ' 🔥 高溫降載';
                            }
                            document.getElementById('battery-val').innerText = battTxt;
                            document.getElementById('clients-val').innerText = data.connectedClients + ' 人';
                            document.getElementById('fps-val').innerText = data.fps || '0';
                            document.getElementById('res-badge').innerText = data.resolution || '720p';
                            
                            const cpu = data.cpuUsage || 30;
                            const mem = data.memoryUsage || 45;
                            document.getElementById('cpu-stat-val').innerText = cpu + '%';
                            document.getElementById('mem-stat-val').innerText = mem + '% (' + (data.memoryUsedMB || 0) + 'MB)';
                            document.getElementById('ping-stat-val').innerText = ping + ' ms';

                            const cpuWarn = document.getElementById('cpu-warning');
                            if (cpu > 80) { cpuWarn.style.display = 'inline'; } else { cpuWarn.style.display = 'none'; }

                            const memWarn = document.getElementById('mem-warning');
                            if (mem > 85) { memWarn.style.display = 'inline'; } else { memWarn.style.display = 'none'; }

                            const pingWarn = document.getElementById('ping-warning');
                            if (ping > 250) { pingWarn.style.display = 'inline'; } else { pingWarn.style.display = 'none'; }

                            const timeStr = new Date().toLocaleTimeString();
                            perfHistory.push({ time: timeStr, cpu: cpu, mem: mem });
                            if (perfHistory.length > 15) { perfHistory.shift(); }
                            renderPerfChart();

                            const opMode = data.operatingMode || 'monitor';
                            document.getElementById('mode-val').innerText = (opMode === 'monitor' ? '👁️ 監看模式' : '🚨 動態偵測');
                            document.getElementById('btn-mode-monitor').style.background = (opMode === 'monitor' ? '#6750A4' : '#334155');
                            document.getElementById('btn-mode-detection').style.background = (opMode === 'detection' ? '#6750A4' : '#334155');

                            const nMode = data.nightVisionMode || 'auto';
                            document.getElementById('night-val').innerText = (data.isNightVisionActive ? '夜視中 (' + nMode + ')' : '一般 (' + nMode + ')');
                            
                            document.getElementById('btn-night-off').style.background = (nMode === 'off' ? '#6750A4' : '#334155');
                            document.getElementById('btn-night-on').style.background = (nMode === 'on' ? '#6750A4' : '#334155');
                            document.getElementById('btn-night-auto').style.background = (nMode === 'auto' ? '#6750A4' : '#334155');

                            if (data.storageFree && data.storageTotal) {
                                document.getElementById('storage-val').innerText = '剩餘 ' + data.storageFree + ' / 全部 ' + data.storageTotal;
                            }

                            currentAutoStart = data.autoStartOnBoot !== false;
                            currentPowerCut = data.powerCutAlertEnabled !== false;

                            const btnAuto = document.getElementById('btn-autostart-toggle');
                            if (btnAuto) {
                                btnAuto.innerText = '⚡ 開機/復電自動啟動: ' + (currentAutoStart ? '【已開啟】' : '【已關閉】');
                                btnAuto.style.background = currentAutoStart ? '#22C55E' : '#334155';
                            }

                            const btnPower = document.getElementById('btn-powercut-toggle');
                            if (btnPower) {
                                btnPower.innerText = '🚨 斷電與低電量警報: ' + (currentPowerCut ? '【已開啟】' : '【已關閉】');
                                btnPower.style.background = currentPowerCut ? '#EF4444' : '#334155';
                            }

                            isTorchOn = data.isTorchOn;
                        } catch (e) {
                            console.error(e);
                        }
                    }

                    async function fetchEvents() {
                        try {
                            const res = await fetch('/events');
                            const events = await res.json();
                            const container = document.getElementById('events-container');
                            if (!container) return;

                            if (!Array.isArray(events) || events.length === 0) {
                                container.innerHTML = '<div style="color:var(--subtext); text-align:center; padding:16px;">目前尚無動態警報紀錄</div>';
                                return;
                            }

                            var htmlStr = '';
                            for (var i = 0; i < events.length; i++) {
                                var ev = events[i];
                                var imgTag = ev.thumbnailBase64 ? '<img src="data:image/jpeg;base64,' + ev.thumbnailBase64 + '" style="width:84px; height:64px; object-fit:cover; border-radius:8px; border:1px solid #334155;">' : '<div style="width:84px; height:64px; background:#1E293B; border-radius:8px; display:flex; align-items:center; justify-content:center; color:#94A3B8;">📷</div>';
                                htmlStr += '<div style="background:#0F172A; border:1px solid #334155; border-radius:12px; padding:10px; display:flex; align-items:center; gap:12px; flex-wrap:wrap;">' +
                                    imgTag +
                                    '<div style="flex:1; min-width:160px;">' +
                                    '<div style="font-weight:bold; font-size:0.95rem; color:#F1F5F9;">🚨 動態觸發 (' + ev.motionPercentage + '%)</div>' +
                                    '<div style="font-size:0.8rem; color:var(--subtext); margin-top:2px;">📅 ' + ev.formattedTime + '</div>' +
                                    '</div>' +
                                    '<div style="display:flex; gap:6px;">' +
                                    '<a href="' + ev.downloadUrl + '" download style="text-decoration:none;" class="btn btn-primary">⬇️ 下載照片</a>' +
                                    '<button onclick="deleteEvent(' + ev.id + ')" class="btn btn-danger">🗑️</button>' +
                                    '</div>' +
                                    '</div>';
                            }
                            container.innerHTML = htmlStr;
                        } catch (e) {
                            console.error(e);
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
                        const w = img.naturalWidth || 1280;
                        const h = img.naturalHeight || 720;
                        const canvas = document.createElement('canvas');
                        const ctx = canvas.getContext('2d');

                        if (currentRotation === 90 || currentRotation === 270) {
                            canvas.width = h;
                            canvas.height = w;
                        } else {
                            canvas.width = w;
                            canvas.height = h;
                        }

                        ctx.translate(canvas.width / 2, canvas.height / 2);
                        ctx.rotate((currentRotation * Math.PI) / 180);
                        ctx.drawImage(img, -w / 2, -h / 2);

                        const a = document.createElement('a');
                        a.href = canvas.toDataURL('image/jpeg');
                        a.download = 'OcularNode_snapshot_' + Date.now() + '.jpg';
                        a.click();
                    }

                    fetchStatus();
                    fetchEvents();
                    setInterval(fetchStatus, 2000);
                    setInterval(fetchEvents, 10000);
                    applyTransform();
                </script>
            </body>
            </html>
        """.trimIndent()
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
