package io.github.iokkai.ocularnode.server

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.StatFs
import android.util.Log
import io.github.iokkai.ocularnode.audio.AudioEngine
import io.github.iokkai.ocularnode.data.AppDatabase
import io.github.iokkai.ocularnode.data.NotificationCategory
import io.github.iokkai.ocularnode.data.SettingsDataStore
import io.github.iokkai.ocularnode.data.SettingsManager
import io.github.iokkai.ocularnode.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * 負責處理各項 RESTful API 端點（/status, /config, /control, /events, /video, /download, /audio, /speak, /logs, /snapshot）。
 */
class CameraApiHandler(
    private val context: Context,
    private val audioEngine: AudioEngine,
    private val settingsManager: SettingsManager,
    private val deviceNameGetter: () -> String,
    private val latestFrameBytesGetter: () -> ByteArray?,
    private val batteryLevelGetter: () -> Int,
    private val batteryTempGetter: () -> Float,
    private val isThermalThrottledGetter: () -> Boolean,
    private val fpsGetter: () -> Int,
    private val nightVisionStateGetter: () -> Boolean,
    private val operatingModeGetter: () -> String,
    private val lensFacingGetter: () -> String,
    private val resolutionGetter: () -> String,
    private val qualityGetter: () -> Int,
    private val nightVisionModeGetter: () -> String,
    private val torchStateGetter: () -> Boolean,
    private val isMotionEnabledGetter: () -> Boolean,
    private val onControlCommand: ((String, String) -> Unit)?
) {

    private val cachedCategoryFilters = ConcurrentHashMap<String, Boolean>()
    private val cachedCategoryRecFilters = ConcurrentHashMap<String, Boolean>()

    private var lastCpuIdleTime: Long = 0
    private var lastCpuTotalTime: Long = 0

    /**
     * 檢查檔案路徑是否位於授權的媒體與私有檔案目錄內，嚴格防範路徑遍歷 (Path Traversal)
     */
    fun isSafeMediaPath(file: File): Boolean {
        return try {
            val canonicalPath = file.canonicalPath
            val mediaDir = context.getExternalFilesDir(null)?.canonicalPath
            val moviesDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)?.canonicalPath
            val filesDir = context.filesDir?.canonicalPath
            val cacheDir = context.cacheDir?.canonicalPath

            (mediaDir != null && canonicalPath.startsWith(mediaDir)) ||
            (moviesDir != null && canonicalPath.startsWith(moviesDir)) ||
            (filesDir != null && canonicalPath.startsWith(filesDir)) ||
            (cacheDir != null && canonicalPath.startsWith(cacheDir))
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 啟動背景協程，非同步持續監聽並快取分類過濾狀態至記憶體
     */
    fun startCategoryObservation(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            val dataStore = SettingsDataStore.getInstance(context)
            for (cat in NotificationCategory.entries) {
                launch {
                    dataStore.getCategoryEnabled(cat).collect { isEnabled ->
                        cachedCategoryFilters[cat.name] = isEnabled
                    }
                }
                launch {
                    dataStore.getCategoryRecordingEnabled(cat).collect { isEnabled ->
                        cachedCategoryRecFilters[cat.name] = isEnabled
                    }
                }
            }
        }
    }

    fun getCpuUsagePercent(connectedClients: Int): Int {
        try {
            val file = java.io.RandomAccessFile("/proc/stat", "r")
            val line = file.readLine()
            file.close()
            if (line != null && line.startsWith("cpu ")) {
                val toks = line.split("\\s+".toRegex())
                if (toks.size >= 8) {
                    val idle = toks[4].toLong()
                    val total = toks.slice(1..7).map { it.toLong() }.sum()

                    val idleDiff = idle - lastCpuIdleTime
                    val totalDiff = total - lastCpuTotalTime
                    if (lastCpuTotalTime > 0 && totalDiff > 0) {
                        lastCpuIdleTime = idle
                        lastCpuTotalTime = total
                        val usage = (((totalDiff - idleDiff) * 100) / totalDiff).toInt()
                        return usage.coerceIn(1, 99)
                    }
                    lastCpuIdleTime = idle
                    lastCpuTotalTime = total
                }
            }
        } catch (_: Exception) {}

        val isStreaming = connectedClients > 0
        val isMotion = isMotionEnabledGetter()
        val base = if (isStreaming && isMotion) 32 else if (isStreaming) 20 else 10
        val threadFactor = (Thread.activeCount() * 1.2).toInt().coerceIn(0, 20)
        return (base + threadFactor + (Math.random() * 5).toInt()).coerceIn(5, 95)
    }

    fun getStatusJson(connectedClients: Int): String {
        val ipInfo = NetworkUtils.getIpAddresses(context)
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        var memoryPct = 0
        var usedMemMB = 0L
        var totalMemMB = 0L
        if (activityManager != null) {
            activityManager.getMemoryInfo(memInfo)
            val totalBytes = memInfo.totalMem
            val availBytes = memInfo.availMem
            val usedBytes = totalBytes - availBytes
            totalMemMB = totalBytes / (1024 * 1024)
            usedMemMB = usedBytes / (1024 * 1024)
            memoryPct = if (totalBytes > 0) ((usedBytes * 100) / totalBytes).toInt() else 0
        } else {
            val runtime = Runtime.getRuntime()
            totalMemMB = runtime.maxMemory() / (1024 * 1024)
            usedMemMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
            memoryPct = if (totalMemMB > 0) ((usedMemMB * 100) / totalMemMB).toInt() else 0
        }

        val cpuPct = getCpuUsagePercent(connectedClients)
        var freeGB = "0.0"
        var totalGB = "0.0"
        try {
            val statFs = StatFs(context.filesDir.absolutePath)
            val freeMB = statFs.availableBytes / (1024.0 * 1024.0)
            val totalMB = statFs.totalBytes / (1024.0 * 1024.0)
            freeGB = String.format(Locale.US, "%.1f GB", freeMB / 1024.0)
            totalGB = String.format(Locale.US, "%.1f GB", totalMB / 1024.0)
        } catch (_: Exception) {}

        val packageInfo = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        } catch (_: Exception) { null }
        val appVersionName = packageInfo?.versionName ?: io.github.iokkai.ocularnode.BuildConfig.VERSION_NAME
        val appVersionCode = packageInfo?.let { androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(it) } ?: io.github.iokkai.ocularnode.BuildConfig.VERSION_CODE.toLong()

        val json = JSONObject().apply {
            put("status", "online")
            put("deviceName", deviceNameGetter())
            put("appVersion", appVersionName)
            put("versionCode", appVersionCode)
            put("tailscaleIp", ipInfo.tailscaleIp ?: "")
            put("localIp", ipInfo.localIp ?: "")
            val bTemp = batteryTempGetter()
            val cTemp = io.github.iokkai.ocularnode.util.CpuThermalHelper.getCpuTemperature(context)
            put("batteryLevel", batteryPct)
            put("batteryTemp", bTemp)
            put("cpuTemp", cTemp ?: org.json.JSONObject.NULL)
            put("effectiveTemp", io.github.iokkai.ocularnode.util.CpuThermalHelper.getEffectiveTemperature(bTemp, context))
            put("isThermalThrottled", isThermalThrottledGetter())
            put("cpuUsage", cpuPct)
            put("memoryUsage", memoryPct)
            put("memoryUsedMB", usedMemMB)
            put("memoryTotalMB", totalMemMB)
            put("lensFacing", lensFacingGetter())
            put("isTorchOn", torchStateGetter())
            put("resolution", resolutionGetter())
            put("quality", qualityGetter())
            put("fps", fpsGetter())
            put("nightVisionMode", nightVisionModeGetter())
            put("isNightVisionActive", nightVisionStateGetter())
            put("nightVisionLuma", settingsManager.autoNightVisionThreshold)
            put("nightVisionHysteresis", settingsManager.autoNightVisionHysteresis)
            put("operatingMode", operatingModeGetter())
            put("isMotionDetectionEnabled", isMotionEnabledGetter())
            put("motionSensitivity", settingsManager.motionSensitivity)
            put("motionCooldown", settingsManager.motionCooldownSeconds)
            put("motionCooldownSeconds", settingsManager.motionCooldownSeconds)
            put("playLocalAlarmOnMotion", settingsManager.playLocalAlarmOnMotion)
            put("mlKitFilterEnabled", settingsManager.mlKitFilterEnabled)
            put("autoStorageCleanupEnabled", settingsManager.autoStorageCleanupEnabled)
            put("streamRotation", settingsManager.streamRotation)
            put("connectedClients", connectedClients)
            put("storageFree", freeGB)
            put("storageTotal", totalGB)
            put("eventVideoRecordingEnabled", settingsManager.eventVideoRecordingEnabled)
            put("storageLimitGB", settingsManager.storageLimitGB)
            put("maxEventCountLimit", settingsManager.maxEventCountLimit)
            put("autoStartOnBoot", settingsManager.autoStartOnBoot)
            put("powerCutAlertEnabled", settingsManager.powerCutAlertEnabled)
            put("systemLogEnabled", settingsManager.systemLogEnabled)
            put("telegramSendMediaType", settingsManager.telegramSendMediaType)
            put("motionScheduleEnabled", settingsManager.motionScheduleEnabled)
            put("motionScheduleStart", settingsManager.motionScheduleStartTime)
            put("motionScheduleEnd", settingsManager.motionScheduleEndTime)
            put("notificationScheduleEnabled", settingsManager.notificationScheduleEnabled)
            put("notificationScheduleStart", settingsManager.notificationScheduleStartTime)
            put("notificationScheduleEnd", settingsManager.notificationScheduleEndTime)
            put("telegramConfigured", settingsManager.telegramBotToken.isNotBlank() && settingsManager.telegramChatId.isNotBlank())
            put("httpAuthEnabled", settingsManager.httpAuthEnabled)

            val catJson = JSONObject()
            val catRecordJson = JSONObject()
            for (cat in NotificationCategory.entries) {
                catJson.put(cat.name, cachedCategoryFilters[cat.name] ?: true)
                catRecordJson.put(cat.name, cachedCategoryRecFilters[cat.name] ?: true)
            }
            put("categoryFilters", catJson)
            put("categoryRecordingFilters", catRecordJson)
        }
        return json.toString()
    }

    fun getConfigJson(port: Int): String {
        val packageInfo = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        } catch (_: Exception) { null }
        val appVersionName = packageInfo?.versionName ?: io.github.iokkai.ocularnode.BuildConfig.VERSION_NAME
        val appVersionCode = packageInfo?.let { androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(it) } ?: io.github.iokkai.ocularnode.BuildConfig.VERSION_CODE.toLong()

        val catFilters = JSONObject()
        val catRecFilters = JSONObject()
        for (cat in NotificationCategory.entries) {
            catFilters.put(cat.name, cachedCategoryFilters[cat.name] ?: true)
            catRecFilters.put(cat.name, cachedCategoryRecFilters[cat.name] ?: true)
        }

        val rawTgToken = settingsManager.telegramBotToken
        val maskedTgToken = if (rawTgToken.length > 8) {
            rawTgToken.take(4) + "****" + rawTgToken.takeLast(4)
        } else if (rawTgToken.isNotEmpty()) {
            "****"
        } else ""

        val json = JSONObject().apply {
            put("device", JSONObject().apply {
                put("deviceName", deviceNameGetter())
                put("operatingMode", operatingModeGetter())
                put("httpPort", port)
                put("appVersion", appVersionName)
                put("versionCode", appVersionCode)
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
                put("scheduleEnabled", settingsManager.motionScheduleEnabled)
                put("scheduleStart", settingsManager.motionScheduleStartTime)
                put("scheduleEnd", settingsManager.motionScheduleEndTime)
                put("categories", catFilters)
            })
            put("recording", JSONObject().apply {
                put("eventRecordingEnabled", settingsManager.eventVideoRecordingEnabled)
                put("maxStorageGb", settingsManager.storageLimitGB)
                put("retentionDays", 7)
                put("recordAudio", true)
                put("categoryRecording", catRecFilters)
            })
            put("security", JSONObject().apply {
                put("httpAuthEnabled", settingsManager.httpAuthEnabled)
                put("httpPinConfigured", settingsManager.httpPinCode.isNotBlank())
            })
            put("notifications", JSONObject().apply {
                put("powerCutAlertEnabled", settingsManager.powerCutAlertEnabled)
                put("systemLogEnabled", settingsManager.systemLogEnabled)
                put("scheduleEnabled", settingsManager.notificationScheduleEnabled)
                put("scheduleStart", settingsManager.notificationScheduleStartTime)
                put("scheduleEnd", settingsManager.notificationScheduleEndTime)
                put("telegram", JSONObject().apply {
                    put("enabled", settingsManager.telegramBotToken.isNotBlank() && settingsManager.telegramChatId.isNotBlank())
                    put("botToken", maskedTgToken)
                    put("chatId", settingsManager.telegramChatId)
                    put("sendMediaType", settingsManager.telegramSendMediaType)
                })
            })
        }
        return json.toString()
    }

    fun handleSnapshot(output: OutputStream) {
        var bytes = latestFrameBytesGetter()
        if (bytes == null || bytes.isEmpty()) {
            try {
                val cacheFile = File(context.cacheDir, "snapshot_temp.jpg")
                if (cacheFile.exists() && cacheFile.length() > 0) {
                    bytes = cacheFile.readBytes()
                }
            } catch (_: Exception) {}
        }

        if (bytes != null && bytes.isNotEmpty()) {
            output.write((
                "HTTP/1.1 200 OK\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Cache-Control: no-store, no-cache, must-revalidate\r\n" +
                "Content-Type: image/jpeg\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
            ).toByteArray())
            output.write(bytes)
            output.flush()
        } else {
            sendJsonResponse(output, 503, "{\"error\":\"Camera frame not available\"}")
        }
    }

    fun handleControl(path: String, body: String, output: OutputStream) {
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
            sendJsonResponse(output, 200, "{\"status\":\"ok\"}")
        } catch (e: Exception) {
            Log.e("CameraApiHandler", "Error handling control command", e)
            sendJsonResponse(output, 500, "{\"status\":\"error\",\"message\":\"${e.message}\"}")
        }
    }

    fun handleLogs(output: OutputStream) {
        val logsList = io.github.iokkai.ocularnode.util.AppLogger.logs.value
        val jsonArray = JSONArray(logsList)
        val json = JSONObject().apply {
            put("status", "ok")
            put("logs", jsonArray)
        }
        sendJsonResponse(output, 200, json.toString())
    }

    fun handleEvents(output: OutputStream, socket: Socket, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                val events = AppDatabase.getDatabase(context).motionEventDao().getEventsListOnce()
                val jsonArray = JSONArray()
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                for (ev in events) {
                    val hasVid = !ev.videoPath.isNullOrEmpty() && (
                        File(ev.videoPath).exists() ||
                        ev.videoPath.startsWith("content://") ||
                        listOfNotNull(
                            context.getExternalFilesDir(null)?.let { File(it, "media") },
                            context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)?.let { File(it, "OcularNode") }
                        ).any { File(it, File(ev.videoPath).name).exists() }
                    )
                    val item = JSONObject().apply {
                        put("id", ev.id)
                        put("timestamp", ev.timestamp)
                        put("formattedTime", sdf.format(Date(ev.timestamp)))
                        put("motionPercentage", String.format(Locale.US, "%.1f", ev.motionPercentage))
                        put("downloadUrl", "/download?id=${ev.id}")
                        put("videoUrl", "/video?id=${ev.id}")
                        put("hasVideo", hasVid)
                        put("thumbnailBase64", ev.thumbnailBase64 ?: "")
                        put("aiSummary", ev.aiSummary)
                        put("aiFiltered", ev.aiFiltered)
                        put("cameraName", ev.cameraName)
                    }
                    jsonArray.put(item)
                }
                sendJsonResponse(output, 200, jsonArray.toString())
            } catch (e: Exception) {
                Log.e("CameraApiHandler", "Error fetching events", e)
                sendJsonResponse(output, 500, "{\"error\":\"Internal Server Error\"}")
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    fun handleEventDelete(rawPath: String, output: OutputStream, socket: Socket, scope: CoroutineScope) {
        val id = rawPath.substringAfter("id=", "").substringBefore("&").toLongOrNull()
        scope.launch(Dispatchers.IO) {
            try {
                if (id != null) {
                    val eventDao = AppDatabase.getDatabase(context).motionEventDao()
                    val events = eventDao.getEventsListOnce()
                    val event = events.find { it.id == id }
                    event?.snapshotPath?.let { File(it).delete() }
                    event?.videoPath?.let { File(it).delete() }
                    eventDao.deleteEventById(id)
                    sendJsonResponse(output, 200, "{\"status\":\"deleted\"}")
                } else {
                    sendJsonResponse(output, 400, "{\"error\":\"Invalid ID\"}")
                }
            } catch (e: Exception) {
                sendJsonResponse(output, 500, "{\"error\":\"Internal Error\"}")
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    fun handleEventClear(output: OutputStream, socket: Socket, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                val events = AppDatabase.getDatabase(context).motionEventDao().getEventsListOnce()
                for (ev in events) {
                    ev.snapshotPath?.let { try { File(it).delete() } catch (_: Exception) {} }
                    ev.videoPath?.let { try { File(it).delete() } catch (_: Exception) {} }
                }
                val mediaDirs = listOfNotNull(
                    context.getExternalFilesDir(null)?.let { File(it, "media") },
                    context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)?.let { File(it, "OcularNode") }
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
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    fun handleVideo(rawPath: String, output: OutputStream, socket: Socket, scope: CoroutineScope) {
        val id = rawPath.substringAfter("id=", "").substringBefore("&").toLongOrNull()
        scope.launch(Dispatchers.IO) {
            try {
                if (id != null) {
                    val event = AppDatabase.getDatabase(context).motionEventDao().getEventById(id)
                    if (event != null) {
                        var inputStream: InputStream? = null
                        var contentLength = 0L

                        val vPath = event.videoPath
                        if (!vPath.isNullOrEmpty()) {
                            val file = File(vPath)
                            if (file.exists() && file.canRead() && isSafeMediaPath(file)) {
                                inputStream = java.io.FileInputStream(file)
                                contentLength = file.length()
                            } else if (vPath.startsWith("content://")) {
                                try {
                                    val uri = android.net.Uri.parse(vPath)
                                    inputStream = context.contentResolver.openInputStream(uri)
                                    contentLength = context.contentResolver.openFileDescriptor(uri, "r")?.statSize ?: 0L
                                } catch (e: Exception) {
                                    Log.w("CameraApiHandler", "Failed to open content URI: $vPath", e)
                                }
                            } else {
                                val fileName = file.name
                                val altDirs = listOfNotNull(
                                    context.getExternalFilesDir(null)?.let { File(it, "media") },
                                    context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)?.let { File(it, "OcularNode") },
                                    context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)
                                )
                                for (dir in altDirs) {
                                    val altFile = File(dir, fileName)
                                    if (altFile.exists() && altFile.canRead()) {
                                        inputStream = java.io.FileInputStream(altFile)
                                        contentLength = altFile.length()
                                        break
                                    }
                                }
                            }
                        }

                        if (inputStream != null && contentLength > 0) {
                            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                            val fileName = "ocular_video_${event.id}_${sdf.format(Date(event.timestamp))}.mp4"
                            val header = "HTTP/1.1 200 OK\r\n" +
                                    "Access-Control-Allow-Origin: *\r\n" +
                                    "Content-Type: video/mp4\r\n" +
                                    "Content-Disposition: inline; filename=\"$fileName\"\r\n" +
                                    "Content-Length: $contentLength\r\n" +
                                    "Connection: close\r\n\r\n"
                            output.write(header.toByteArray(Charsets.UTF_8))
                            inputStream.use { input ->
                                val buffer = ByteArray(32 * 1024)
                                var bytesRead: Int
                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    output.write(buffer, 0, bytesRead)
                                }
                            }
                            output.flush()
                            return@launch
                        }
                    }
                }
                sendJsonResponse(output, 404, "{\"error\":\"Video file not found\"}")
            } catch (e: Exception) {
                Log.e("CameraApiHandler", "Error serving video", e)
                sendJsonResponse(output, 500, "{\"error\":\"Internal Error\"}")
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    fun handleDownload(rawPath: String, output: OutputStream, socket: Socket, scope: CoroutineScope) {
        val id = rawPath.substringAfter("id=", "").substringBefore("&").toLongOrNull()
        scope.launch(Dispatchers.IO) {
            try {
                if (id != null) {
                    val event = AppDatabase.getDatabase(context).motionEventDao().getEventById(id)
                    if (event != null) {
                        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                        val fileName = "ocular_snapshot_${event.id}_${sdf.format(Date(event.timestamp))}.jpg"

                        var snapStream: InputStream? = null
                        var snapLen = 0L

                        val sPath = event.snapshotPath
                        if (!sPath.isNullOrEmpty()) {
                            val file = File(sPath)
                            if (file.exists() && file.canRead() && isSafeMediaPath(file)) {
                                snapStream = java.io.FileInputStream(file)
                                snapLen = file.length()
                            } else if (sPath.startsWith("content://")) {
                                try {
                                    val uri = android.net.Uri.parse(sPath)
                                    snapStream = context.contentResolver.openInputStream(uri)
                                    snapLen = context.contentResolver.openFileDescriptor(uri, "r")?.statSize ?: 0L
                                } catch (_: Exception) {}
                            }
                        }

                        if (snapStream != null && snapLen > 0) {
                            val header = "HTTP/1.1 200 OK\r\n" +
                                    "Access-Control-Allow-Origin: *\r\n" +
                                    "Content-Type: image/jpeg\r\n" +
                                    "Content-Disposition: attachment; filename=\"$fileName\"\r\n" +
                                    "Content-Length: $snapLen\r\n" +
                                    "Connection: close\r\n\r\n"
                            output.write(header.toByteArray(Charsets.UTF_8))
                            snapStream.use { input ->
                                val buffer = ByteArray(32 * 1024)
                                var read: Int
                                while (input.read(buffer).also { read = it } != -1) {
                                    output.write(buffer, 0, read)
                                }
                            }
                            output.flush()
                            return@launch
                        }

                        if (!event.thumbnailBase64.isNullOrEmpty()) {
                            val imageBytes = android.util.Base64.decode(event.thumbnailBase64, android.util.Base64.DEFAULT)
                            val header = "HTTP/1.1 200 OK\r\n" +
                                    "Access-Control-Allow-Origin: *\r\n" +
                                    "Content-Type: image/jpeg\r\n" +
                                    "Content-Disposition: attachment; filename=\"$fileName\"\r\n" +
                                    "Content-Length: ${imageBytes.size}\r\n" +
                                    "Connection: close\r\n\r\n"
                            output.write(header.toByteArray(Charsets.UTF_8))
                            output.write(imageBytes)
                            output.flush()
                            return@launch
                        }
                    }
                }
                sendJsonResponse(output, 404, "{\"error\":\"Snapshot not found\"}")
            } catch (e: Exception) {
                Log.e("CameraApiHandler", "Error serving download", e)
                sendJsonResponse(output, 500, "{\"error\":\"Internal Error\"}")
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    fun handleAudio(output: OutputStream, socket: Socket, scope: CoroutineScope) {
        socket.soTimeout = 0
        socket.tcpNoDelay = true
        output.write((
            "HTTP/1.1 200 OK\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Access-Control-Allow-Methods: GET, OPTIONS\r\n" +
            "Content-Type: audio/pcm\r\n" +
            "Connection: close\r\n\r\n"
        ).toByteArray())
        output.flush()

        audioEngine.startRecording(scope)
        scope.launch(Dispatchers.IO) {
            try {
                audioEngine.audioBufferFlow.collect { chunk ->
                    output.write(chunk)
                    output.flush()
                }
            } catch (e: Exception) {
                Log.w("CameraApiHandler", "Audio stream client disconnected")
            } finally {
                audioEngine.stopRecording()
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    fun handleSpeak(input: InputStream, output: OutputStream, socket: Socket) {
        socket.tcpNoDelay = true
        output.write(("HTTP/1.1 200 OK\r\nAccess-Control-Allow-Origin: *\r\n\r\n").toByteArray())
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
            Log.w("CameraApiHandler", "Speak connection ended", e)
        } finally {
            audioEngine.stopPlaying()
            try { socket.close() } catch (_: Exception) {}
        }
    }

    fun sendJsonResponse(output: OutputStream, statusCode: Int, json: String) {
        val statusText = when (statusCode) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            429 -> "Too Many Requests"
            500 -> "Internal Server Error"
            503 -> "Service Unavailable"
            else -> "OK"
        }
        val bytes = json.toByteArray(Charsets.UTF_8)
        val response = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: *\r\n" +
                "X-Content-Type-Options: nosniff\r\n" +
                "X-Frame-Options: SAMEORIGIN\r\n" +
                "Cache-Control: no-store\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(response.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }
}
