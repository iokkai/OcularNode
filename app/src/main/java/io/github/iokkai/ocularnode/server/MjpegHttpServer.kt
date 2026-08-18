package io.github.iokkai.ocularnode.server

import android.content.Context
import android.util.Log
import io.github.iokkai.ocularnode.audio.AudioEngine
import io.github.iokkai.ocularnode.data.SettingsManager
import io.github.iokkai.ocularnode.util.NetworkUtils
import io.github.iokkai.ocularnode.util.NodeDiscoveryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 負責 OcularNode 區域網路/Tailscale HTTP 伺服器主核心。
 * 協調 HttpAuthHandler (認證授權)、MjpegStreamHandler (串流推播)、
 * CameraApiHandler (REST API) 與 WebDashboardProvider (控制台 UI)。
 */
class MjpegHttpServer(
    private val context: Context,
    val port: Int = 8080,
    private val audioEngine: AudioEngine
) {

    private var serverSocket: ServerSocket? = null
    @Volatile
    var isRunning = false

    private val threadPool = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors().coerceIn(4, 16)
    )

    // ── S-4 Rate Limiting & Connection Control ──
    companion object {
        /** 同一 IP 在滑動視窗內允許的最大請求數 */
        private const val MAX_REQUESTS_PER_WINDOW = 30
        /** 滑動視窗大小 (ms) */
        private const val RATE_LIMIT_WINDOW_MS = 10_000L
        /** 最大同時連線數 */
        private const val MAX_CONCURRENT_CONNECTIONS = 50
        /** 清理過期記錄的間隔 (ms) */
        private const val CLEANUP_INTERVAL_MS = 60_000L
    }

    /** 追蹤每個 IP 的請求時間戳 (滑動視窗) */
    private val ipRequestLog = ConcurrentHashMap<String, ArrayDeque<Long>>()
    /** 當前活躍連線數 */
    private val activeConnections = AtomicInteger(0)

    private val settingsManager by lazy { SettingsManager.getInstance(context) }

    // 模組化職責處理器
    val authHandler = HttpAuthHandler()
    val streamHandler = MjpegStreamHandler()
    val dashboardProvider = WebDashboardProvider(context)

    var deviceName: String = "OcularNode Camera"
    @Volatile var latestFrameBytes: ByteArray? = null
    @Volatile var batteryLevel: Int = -1
    @Volatile var batteryTemp: Float = 0f
    @Volatile var isThermalThrottled: Boolean = false
    @Volatile var fps: Int = 0
    @Volatile var isNightVisionActive: Boolean = false
    @Volatile var operatingMode: String = "monitor"
    @Volatile var lensFacing: String = "Back"
    @Volatile var resolution: String = "720p"
    @Volatile var quality: Int = 60
    @Volatile var nightVisionMode: String = "auto"
    @Volatile var isTorchOn: Boolean = false
    @Volatile var isMotionEnabled: Boolean = true

    var deviceNameGetter: (() -> String) = { deviceName }
    var operatingModeGetter: (() -> String) = { operatingMode }
    var lensFacingGetter: (() -> String) = { lensFacing }
    var resolutionGetter: (() -> String) = { resolution }
    var qualityGetter: (() -> Int) = { quality }
    var nightVisionModeGetter: (() -> String) = { nightVisionMode }
    var nightVisionStateGetter: (() -> Boolean) = { isNightVisionActive }
    var torchStateGetter: (() -> Boolean) = { isTorchOn }
    var isMotionEnabledGetter: (() -> Boolean) = { isMotionEnabled }

    var onActiveClientsChanged: ((Int) -> Unit)? = null
    var onControlCommand: ((String, String) -> Unit)? = null
    var onBatchConfigUpdated: ((String) -> Unit)? = null

    val apiHandler = CameraApiHandler(
        context = context,
        audioEngine = audioEngine,
        settingsManager = settingsManager,
        deviceNameGetter = { deviceNameGetter() },
        latestFrameBytesGetter = { latestFrameBytes },
        batteryLevelGetter = { batteryLevel },
        batteryTempGetter = { batteryTemp },
        isThermalThrottledGetter = { isThermalThrottled },
        fpsGetter = { fps },
        nightVisionStateGetter = { nightVisionStateGetter() },
        operatingModeGetter = { operatingModeGetter() },
        lensFacingGetter = { lensFacingGetter() },
        resolutionGetter = { resolutionGetter() },
        qualityGetter = { qualityGetter() },
        nightVisionModeGetter = { nightVisionModeGetter() },
        torchStateGetter = { torchStateGetter() },
        isMotionEnabledGetter = { isMotionEnabledGetter() },
        onControlCommand = { cmd, value -> onControlCommand?.invoke(cmd, value) }
    )

    fun pushFrame(jpegBytes: ByteArray) {
        latestFrameBytes = jpegBytes
        streamHandler.pushFrame(jpegBytes)
    }

    fun isRequestAuthorized(headers: Map<String, String>, rawPath: String): Boolean {
        return authHandler.isRequestAuthorized(settingsManager, headers, rawPath)
    }

    fun getStatusJson(): String {
        return apiHandler.getStatusJson(streamHandler.connectedClientsCount.get())
    }

    fun getConfigJson(): String {
        return apiHandler.getConfigJson(port)
    }

    fun getWebDashboardHtml(): String {
        return dashboardProvider.getWebDashboardHtml()
    }

    fun start(scope: CoroutineScope) {
        if (isRunning) return
        isRunning = true

        apiHandler.startCategoryObservation(scope)
        startUdpDiscoveryResponder(scope)
        startRateLimitCleanup(scope)

        scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), port))
                }
                Log.i("MjpegHttpServer", "Server bound to 0.0.0.0:$port (Tailscale / Local Network)")

                while (isRunning && serverSocket?.isClosed == false) {
                    val socket = serverSocket?.accept() ?: break

                    // 最大同時連線數限制
                    if (activeConnections.get() >= MAX_CONCURRENT_CONNECTIONS) {
                        Log.w("MjpegHttpServer", "Max concurrent connections ($MAX_CONCURRENT_CONNECTIONS) reached, rejecting ${socket.inetAddress.hostAddress}")
                        try {
                            val out = socket.getOutputStream()
                            out.write("HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                            out.flush()
                            socket.close()
                        } catch (_: Exception) { try { socket.close() } catch (_: Exception) {} }
                        continue
                    }

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
            var udpSocket: DatagramSocket? = null
            var broadcastSocket: DatagramSocket? = null
            try {
                udpSocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(NodeDiscoveryManager.UDP_PORT))
                    soTimeout = 2000
                }
                broadcastSocket = DatagramSocket().apply { broadcast = true }

                // Announce loop coroutine (重用 broadcastSocket)
                launch {
                    while (isRunning && isActive) {
                        try {
                            val ipInfo = NetworkUtils.getIpAddresses(context)
                            val activeIp = ipInfo.tailscaleIp ?: ipInfo.localIp ?: "127.0.0.1"
                            val announceMsg = "${NodeDiscoveryManager.ANNOUNCE_PREFIX}$deviceName|$port|$activeIp"
                            val data = announceMsg.toByteArray()

                            val p1 = DatagramPacket(data, data.size, InetAddress.getByName("255.255.255.255"), NodeDiscoveryManager.UDP_PORT)
                            broadcastSocket.send(p1)

                            if (ipInfo.isTailscaleConnected && ipInfo.tailscaleIp != null) {
                                try {
                                    val p2 = DatagramPacket(data, data.size, InetAddress.getByName("100.127.255.255"), NodeDiscoveryManager.UDP_PORT)
                                    broadcastSocket.send(p2)
                                } catch (_: Exception) {}
                            }
                        } catch (_: Exception) {}
                        delay(3000)
                    }
                }

                // Listener loop
                val buffer = ByteArray(2048)
                while (isRunning && isActive) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        udpSocket.receive(packet)
                        val msg = String(packet.data, 0, packet.length).trim()

                        if (msg == NodeDiscoveryManager.DISCOVERY_REQUEST) {
                            val ipInfo = NetworkUtils.getIpAddresses(context)
                            val activeIp = ipInfo.tailscaleIp ?: ipInfo.localIp ?: "127.0.0.1"
                            val responseMsg = "${NodeDiscoveryManager.RESPONSE_PREFIX}$deviceName|$port|$activeIp"
                            val respData = responseMsg.toByteArray()

                            val respPacket = DatagramPacket(respData, respData.size, packet.address, packet.port)
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
                try { broadcastSocket?.close() } catch (_: Exception) {}
            }
        }
    }

    private fun handleClient(socket: Socket, scope: CoroutineScope) {
        activeConnections.incrementAndGet()
        try {
            socket.soTimeout = 10000
            val clientIp = socket.inetAddress.hostAddress ?: "unknown"

            // IP-based Rate Limiting (滑動視窗)
            if (!checkRateLimit(clientIp)) {
                Log.w("MjpegHttpServer", "Rate limit exceeded for $clientIp")
                val out = socket.getOutputStream()
                apiHandler.sendJsonResponse(out, 429, "{\"error\":\"Too Many Requests\",\"retryAfterSeconds\":10}")
                socket.close()
                return
            }
            val input = java.io.BufferedInputStream(socket.getInputStream(), 8192)
            val output = socket.getOutputStream()

            val requestLine = readLineStr(input) ?: run {
                socket.close()
                return
            }

            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                socket.close()
                return
            }

            val method = parts[0].uppercase()
            val rawPath = parts[1]
            val cleanPath = if (rawPath.contains("?")) rawPath.substringBefore("?") else rawPath
            val path = cleanPath.lowercase()

            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLineStr(input) ?: break
                if (line.isEmpty()) break
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    val key = line.substring(0, colonIdx).trim().lowercase()
                    val value = line.substring(colonIdx + 1).trim()
                    headers[key] = value
                }
            }

            // Read Body (限制 64KB 防 DoS)
            var body = ""
            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            if (contentLength in 1..65536 && method != "GET") {
                val bodyBytes = ByteArray(contentLength)
                var totalRead = 0
                while (totalRead < contentLength) {
                    val read = input.read(bodyBytes, totalRead, contentLength - totalRead)
                    if (read == -1) break
                    totalRead += read
                }
                body = String(bodyBytes, 0, totalRead, Charsets.UTF_8)
            }

            if (method == "OPTIONS") {
                output.write((
                    "HTTP/1.1 204 No Content\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS\r\n" +
                    "Access-Control-Allow-Headers: *\r\n" +
                    "Access-Control-Max-Age: 86400\r\n" +
                    "Content-Length: 0\r\n" +
                    "Connection: close\r\n\r\n"
                ).toByteArray())
                output.flush()
                socket.close()
                return
            }

            when {
                // 1. PIN 登入與授權狀態
                cleanPath == "/auth/login" -> {
                    when (val result = authHandler.handleLogin(body, rawPath, settingsManager, clientIp)) {
                        is LoginResult.Success -> {
                            apiHandler.sendJsonResponse(output, 200, result.responseJson)
                        }
                        is LoginResult.LockedOut -> {
                            apiHandler.sendJsonResponse(output, 429, "{\"status\":\"error\",\"message\":\"Too many failed login attempts. Please try again in ${result.retryAfterSeconds}s.\",\"retryAfter\":${result.retryAfterSeconds}}")
                        }
                        is LoginResult.InvalidPin -> {
                            apiHandler.sendJsonResponse(output, 401, "{\"status\":\"error\",\"message\":\"Invalid PIN code\"}")
                        }
                    }
                    socket.close()
                    return
                }

                cleanPath == "/auth/status" -> {
                    apiHandler.sendJsonResponse(output, 200, authHandler.handleAuthStatus(headers, rawPath, settingsManager))
                    socket.close()
                    return
                }

                // 2. MJPEG 即時影像串流 (需授權)
                path.startsWith("/mjpeg") || path.startsWith("/stream") || path.startsWith("/live") -> {
                    if (!isRequestAuthorized(headers, rawPath)) {
                        apiHandler.sendJsonResponse(output, 401, "{\"status\":\"error\",\"error\":\"Unauthorized\",\"authRequired\":true}")
                        socket.close()
                        return
                    }
                    streamHandler.handleStream(socket, output, scope, onActiveClientsChanged)
                    return
                }

                // 3. 快照 (需授權)
                path.startsWith("/snapshot") || path.startsWith("/frame") || path.startsWith("/image") || path.startsWith("/jpeg") -> {
                    if (!isRequestAuthorized(headers, rawPath)) {
                        apiHandler.sendJsonResponse(output, 401, "{\"status\":\"error\",\"error\":\"Unauthorized\",\"authRequired\":true}")
                        socket.close()
                        return
                    }
                    apiHandler.handleSnapshot(output)
                    socket.close()
                    return
                }

                // 4. 控制指令 (需授權)
                path.startsWith("/control") -> {
                    if (!isRequestAuthorized(headers, rawPath)) {
                        apiHandler.sendJsonResponse(output, 401, "{\"status\":\"error\",\"error\":\"Unauthorized\",\"authRequired\":true}")
                        socket.close()
                        return
                    }
                    apiHandler.handleControl(path, body, output)
                    socket.close()
                    return
                }

                // 5. 系統狀態與設定 (需授權)
                path == "/status" || path.startsWith("/status?") -> {
                    if (!isRequestAuthorized(headers, rawPath)) {
                        apiHandler.sendJsonResponse(output, 401, "{\"status\":\"error\",\"error\":\"Unauthorized\",\"authRequired\":true}")
                        socket.close()
                        return
                    }
                    apiHandler.sendJsonResponse(output, 200, getStatusJson())
                    socket.close()
                    return
                }

                path == "/config" || path.startsWith("/config?") -> {
                    if (!isRequestAuthorized(headers, rawPath)) {
                        apiHandler.sendJsonResponse(output, 401, "{\"status\":\"error\",\"error\":\"Unauthorized\",\"authRequired\":true}")
                        socket.close()
                        return
                    }
                    if (method == "POST") {
                        onBatchConfigUpdated?.invoke(body)
                        apiHandler.sendJsonResponse(output, 200, "{\"status\":\"ok\"}")
                        socket.close()
                        return
                    }
                    apiHandler.sendJsonResponse(output, 200, getConfigJson())
                    socket.close()
                    return
                }

                // 6. 系統日誌 (需授權)
                path.startsWith("/logs") -> {
                    if (!isRequestAuthorized(headers, rawPath)) {
                        apiHandler.sendJsonResponse(output, 401, "{\"status\":\"error\",\"error\":\"Unauthorized\",\"authRequired\":true}")
                        socket.close()
                        return
                    }
                    apiHandler.handleLogs(output)
                    socket.close()
                    return
                }

                // 7. 雙向音訊 (需授權)
                path.startsWith("/audio") -> {
                    if (!isRequestAuthorized(headers, rawPath)) {
                        apiHandler.sendJsonResponse(output, 401, "{\"status\":\"error\",\"error\":\"Unauthorized\",\"authRequired\":true}")
                        socket.close()
                        return
                    }
                    apiHandler.handleAudio(output, socket, scope)
                    return
                }

                path.startsWith("/speak") -> {
                    if (!isRequestAuthorized(headers, rawPath)) {
                        apiHandler.sendJsonResponse(output, 401, "{\"status\":\"error\",\"error\":\"Unauthorized\",\"authRequired\":true}")
                        socket.close()
                        return
                    }
                    apiHandler.handleSpeak(input, output, socket)
                    return
                }

                // 8. 警報事件管理 (需授權)
                path.startsWith("/events/delete") -> {
                    if (!isRequestAuthorized(headers, rawPath)) {
                        apiHandler.sendJsonResponse(output, 401, "{\"status\":\"error\",\"error\":\"Unauthorized\",\"authRequired\":true}")
                        socket.close()
                        return
                    }
                    apiHandler.handleEventDelete(rawPath, output, socket, scope)
                    return
                }

                path == "/events/clear" -> {
                    if (!isRequestAuthorized(headers, rawPath)) {
                        apiHandler.sendJsonResponse(output, 401, "{\"status\":\"error\",\"error\":\"Unauthorized\",\"authRequired\":true}")
                        socket.close()
                        return
                    }
                    apiHandler.handleEventClear(output, socket, scope)
                    return
                }

                path == "/events" || path.startsWith("/events?") -> {
                    if (!isRequestAuthorized(headers, rawPath)) {
                        apiHandler.sendJsonResponse(output, 401, "{\"status\":\"error\",\"error\":\"Unauthorized\",\"authRequired\":true}")
                        socket.close()
                        return
                    }
                    apiHandler.handleEvents(output, socket, scope)
                    return
                }

                // 9. 錄影與縮圖下載 (需授權)
                path.startsWith("/video") -> {
                    if (!isRequestAuthorized(headers, rawPath)) {
                        apiHandler.sendJsonResponse(output, 401, "{\"status\":\"error\",\"error\":\"Unauthorized\",\"authRequired\":true}")
                        socket.close()
                        return
                    }
                    apiHandler.handleVideo(rawPath, output, socket, scope)
                    return
                }

                path.startsWith("/download") -> {
                    if (!isRequestAuthorized(headers, rawPath)) {
                        apiHandler.sendJsonResponse(output, 401, "{\"status\":\"error\",\"error\":\"Unauthorized\",\"authRequired\":true}")
                        socket.close()
                        return
                    }
                    apiHandler.handleDownload(rawPath, output, socket, scope)
                    return
                }

                // 10. Web Dashboard (公開介面，內部 API 自行鑑權)
                path == "/" || path == "/index.html" || path == "/dashboard" -> {
                    sendHtmlResponse(output, 200, getWebDashboardHtml())
                    socket.close()
                    return
                }

                else -> {
                    apiHandler.sendJsonResponse(output, 404, "{\"error\":\"Not Found\"}")
                    socket.close()
                    return
                }
            }
        } catch (_: Exception) {
            try { socket.close() } catch (_: Exception) {}
        } finally {
            activeConnections.decrementAndGet()
        }
    }

    private fun readLineStr(input: InputStream): String? {
        val sb = StringBuilder()
        var c: Int
        while (input.read().also { c = it } != -1) {
            if (c == '\n'.code) break
            if (c != '\r'.code) sb.append(c.toChar())
        }
        return if (sb.isEmpty() && c == -1) null else sb.toString()
    }

    private fun sendHtmlResponse(output: OutputStream, statusCode: Int, html: String) {
        val statusText = if (statusCode == 200) "OK" else "Error"
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

    fun stop() {
        isRunning = false
        try {
            streamHandler.closeAll()
            serverSocket?.close()
            threadPool.shutdownNow()
            ipRequestLog.clear()
            activeConnections.set(0)
        } catch (e: Exception) {
            Log.e("MjpegHttpServer", "Error stopping server", e)
        }
    }

    // ── S-4 Rate Limiting Helpers ──

    /**
     * 檢查指定 IP 是否在速率限制視窗內。
     * 使用滑動視窗：移除超過 RATE_LIMIT_WINDOW_MS 的舊時間戳，
     * 若剩餘記錄數 < MAX_REQUESTS_PER_WINDOW 則允許並記錄當前時間。
     */
    private fun checkRateLimit(ip: String): Boolean {
        val now = System.currentTimeMillis()
        val timestamps = ipRequestLog.getOrPut(ip) { ArrayDeque() }
        synchronized(timestamps) {
            // 移除視窗外的過期記錄
            while (timestamps.isNotEmpty() && now - timestamps.first() > RATE_LIMIT_WINDOW_MS) {
                timestamps.removeFirst()
            }
            return if (timestamps.size < MAX_REQUESTS_PER_WINDOW) {
                timestamps.addLast(now)
                true
            } else {
                false
            }
        }
    }

    /**
     * 定期清理長時間無活動的 IP 記錄，防止 HashMap 無限膨脹。
     */
    private fun startRateLimitCleanup(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            while (isRunning && isActive) {
                delay(CLEANUP_INTERVAL_MS)
                val now = System.currentTimeMillis()
                val expiredIps = mutableListOf<String>()
                for ((ip, timestamps) in ipRequestLog) {
                    synchronized(timestamps) {
                        while (timestamps.isNotEmpty() && now - timestamps.first() > RATE_LIMIT_WINDOW_MS) {
                            timestamps.removeFirst()
                        }
                        if (timestamps.isEmpty()) expiredIps.add(ip)
                    }
                }
                expiredIps.forEach { ipRequestLog.remove(it) }
                if (expiredIps.isNotEmpty()) {
                    Log.d("MjpegHttpServer", "Rate limiter cleanup: removed ${expiredIps.size} stale IP entries")
                }
            }
        }
    }
}
