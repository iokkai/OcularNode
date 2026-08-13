package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.BatteryManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.example.MainActivity
import com.example.audio.AudioEngine
import com.example.camera.CameraManagerHelper
import com.example.data.AppDatabase
import com.example.data.MotionEvent
import com.example.data.NotificationCategory
import com.example.data.SettingsDataStore
import com.example.data.SettingsManager
import com.example.server.MjpegHttpServer
import com.example.util.NetworkUtils
import com.example.util.TelegramNotifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CameraStreamService : Service(), LifecycleOwner {

    private val binder = LocalBinder()
    private var wakeLock: PowerManager.WakeLock? = null

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val serviceJob = SupervisorJob()
    val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var recordingTimerJob: Job? = null
    private var eventVideoRecorder: com.example.camera.EventVideoRecorder? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var lastIsCharging: Boolean? = null
    private var hasSentLowBatteryAlert: Boolean = false

    lateinit var cameraHelper: CameraManagerHelper
        private set
    lateinit var httpServer: MjpegHttpServer
        private set
    lateinit var audioEngine: AudioEngine
        private set
    
    private var prefsListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null

    lateinit var settingsManager: SettingsManager
        private set
    private lateinit var database: AppDatabase

    private val _serviceStatus = MutableStateFlow("Initializing...")
    val serviceStatus: StateFlow<String> = _serviceStatus.asStateFlow()

    private val _measuredLuma = MutableStateFlow(100f)
    val measuredLuma: StateFlow<Float> = _measuredLuma.asStateFlow()

    private val _isThermalThrottled = MutableStateFlow(false)
    val isThermalThrottled: StateFlow<Boolean> = _isThermalThrottled.asStateFlow()

    private val _batteryTemp = MutableStateFlow(0.0f)
    val batteryTemp: StateFlow<Float> = _batteryTemp.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): CameraStreamService = this@CameraStreamService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        settingsManager = SettingsManager(this)
        if (settingsManager.deviceRoleMode == "VIEWER") {
            Log.i("CameraStreamService", "Device is set to VIEWER mode, stopping CameraStreamService.")
            stopSelf()
            return
        }

        database = AppDatabase.getDatabase(this)
        audioEngine = AudioEngine()
        cameraHelper = CameraManagerHelper(this)

        // Apply settings
        cameraHelper.jpegQuality = settingsManager.defaultQuality
        cameraHelper.currentResolutionString = settingsManager.defaultResolution
        cameraHelper.nightVisionMode = settingsManager.nightVisionMode
        cameraHelper.autoNightVisionThreshold = settingsManager.autoNightVisionThreshold
        cameraHelper.autoNightVisionHysteresis = settingsManager.autoNightVisionHysteresis
        cameraHelper.isMotionDetectionEnabled = settingsManager.motionDetectionEnabled
        cameraHelper.motionSensitivity = settingsManager.motionSensitivity
        cameraHelper.motionCooldownSeconds = settingsManager.motionCooldownSeconds
        cameraHelper.dynamicFpsAdjustmentEnabled = settingsManager.dynamicFpsAdjustmentEnabled
        cameraHelper.defaultJpegQuality = settingsManager.defaultQuality

        prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "motion_sensitivity") {
                cameraHelper.motionSensitivity = settingsManager.motionSensitivity
            } else if (key == "motion_cooldown") {
                cameraHelper.motionCooldownSeconds = settingsManager.motionCooldownSeconds
            } else if (key == "motion_enabled") {
                cameraHelper.isMotionDetectionEnabled = settingsManager.motionDetectionEnabled
            } else if (key == "dynamic_fps_enabled") {
                cameraHelper.dynamicFpsAdjustmentEnabled = settingsManager.dynamicFpsAdjustmentEnabled
            } else if (key == "default_quality") {
                cameraHelper.defaultJpegQuality = settingsManager.defaultQuality
            } else if (key == "night_vision_hysteresis") {
                cameraHelper.autoNightVisionHysteresis = settingsManager.autoNightVisionHysteresis
            }
        }
        getSharedPreferences("ocularnode_settings", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefsListener)


        // Configure MJPEG HTTP Server
        httpServer = MjpegHttpServer(this, settingsManager.serverPort, audioEngine).apply {
            deviceName = settingsManager.cameraDeviceName
            lensFacingGetter = { if (cameraHelper.lensFacing == androidx.camera.core.CameraSelector.LENS_FACING_BACK) "back" else "front" }
            torchStateGetter = { cameraHelper.isTorchOn }
            resolutionGetter = { cameraHelper.currentResolutionString }
            qualityGetter = { cameraHelper.jpegQuality }
            nightVisionModeGetter = { cameraHelper.nightVisionMode }
            isNightVisionActiveGetter = { cameraHelper.isNightVisionActive }
            isMotionEnabledGetter = { cameraHelper.isMotionDetectionEnabled }
            operatingModeGetter = { settingsManager.operatingMode }
            isThermalThrottledGetter = { _isThermalThrottled.value }
            batteryTempGetter = { _batteryTemp.value }

            onActiveClientsChanged = { count ->
                onClientsChanged(count)
            }

            onControlCommand = { cmd, valStr ->
                handleRemoteControl(cmd, valStr)
            }

            onBatchConfigUpdated = { configJsonStr ->
                handleBatchConfigUpdate(configJsonStr)
            }
        }

        // Apply mode configuration
        updateOperatingMode(settingsManager.operatingMode)


        val mediaDir = java.io.File(getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES), "OcularNode")
        if (!mediaDir.exists()) mediaDir.mkdirs()
        
        eventVideoRecorder = com.example.camera.EventVideoRecorder(
            outputDir = mediaDir,
            width = 1280,
            height = 720,
            fps = 15,
            preRecordSeconds = 5,
            basePostRecordSeconds = 10,
            maxRecordSeconds = 180
        )
        
        cameraHelper.onFrameReadyForRecording = { jpegBytes, timestampUs ->
            if (settingsManager.eventVideoRecordingEnabled && !_isThermalThrottled.value) {
                eventVideoRecorder?.pushFrame(jpegBytes, timestampUs)
            }
        }
        
        cameraHelper.onFrameEncoded = { jpegBytes ->
            httpServer.updateFrame(jpegBytes)
        }

        cameraHelper.onLumaMeasured = { luma ->
            _measuredLuma.value = luma
        }
        
        cameraHelper.hasActiveConsumers = {
            (::httpServer.isInitialized && httpServer.isRunning) || settingsManager.eventVideoRecordingEnabled
        }

        cameraHelper.onMotionDetected = { percentage, thumbnailBytes, frameBitmap ->
            onMotionTriggered(percentage, thumbnailBytes, frameBitmap)
        }

        startScheduleChecker()
        acquireWakeLock()
        registerPowerBatteryMonitor()
        startForegroundServiceNotification()
    }

    private fun registerPowerBatteryMonitor() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }

        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action ?: return

                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else -1

                // Battery Temperature Monitoring & Thermal Throttling
                val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                if (tempTenths > 0) {
                    val tempCelsius = tempTenths / 10.0f
                    _batteryTemp.value = tempCelsius

                    val highTempThreshold = 45.0f
                    val recoveryTempThreshold = 42.0f

                    if (tempCelsius >= highTempThreshold && !_isThermalThrottled.value) {
                        _isThermalThrottled.value = true
                        Log.w("CameraStreamService", "🔥 Thermal Throttling ACTIVATED! Battery Temp: ${tempCelsius}°C >= ${highTempThreshold}°C")

                        // Immediately stop active video recording to cool down
                        synchronized(this@CameraStreamService) {
                            recordingTimerJob?.cancel()
                            if (cameraHelper.isRecordingVideo) {
                                cameraHelper.stopRecording()
                                Log.i("CameraStreamService", "Thermal Throttling: Active video recording stopped.")
                            }
                        }

                        // Send alert via Telegram
                        serviceScope.launch(Dispatchers.IO) {
                            TelegramNotifier.sendSystemAlert(
                                botToken = settingsManager.telegramBotToken,
                                chatId = settingsManager.telegramChatId,
                                deviceName = settingsManager.cameraDeviceName,
                                alertTitle = "🔥 *【高溫降載機制啟動】*",
                                alertDetails = "裝置電池溫度達到 ${String.format(java.util.Locale.US, "%.1f", tempCelsius)}°C (≥ ${highTempThreshold}°C)！\n為防範手機過熱當機與電池膨脹，系統已自動暫停高耗能【ML Kit AI 分析】與【短片錄影】。\n基礎 Web 串流與 Telegram 文字推播警報仍持續正常運作中。"
                            )
                        }
                    } else if (tempCelsius <= recoveryTempThreshold && _isThermalThrottled.value) {
                        _isThermalThrottled.value = false
                        Log.i("CameraStreamService", "🧊 Thermal Throttling DEACTIVATED! Battery Temp: ${tempCelsius}°C <= ${recoveryTempThreshold}°C")

                        // Send recovery alert via Telegram
                        serviceScope.launch(Dispatchers.IO) {
                            TelegramNotifier.sendSystemAlert(
                                botToken = settingsManager.telegramBotToken,
                                chatId = settingsManager.telegramChatId,
                                deviceName = settingsManager.cameraDeviceName,
                                alertTitle = "🧊 *【高溫降載機制解除】*",
                                alertDetails = "裝置電池溫度已回落至 ${String.format(java.util.Locale.US, "%.1f", tempCelsius)}°C (≤ ${recoveryTempThreshold}°C)。\nML Kit AI 分析與短片錄影功能已自動恢復正常運作！"
                            )
                        }
                    }
                }

                if (!settingsManager.powerCutAlertEnabled) return

                // Power disconnected (充電中 -> 放電中)
                if ((action == Intent.ACTION_POWER_DISCONNECTED || (lastIsCharging == true && !isCharging))) {
                    if (lastIsCharging != false) {
                        lastIsCharging = false
                        Log.w("CameraStreamService", "Power disconnected! Battery: $batteryPct%")
                        serviceScope.launch(Dispatchers.IO) {
                            TelegramNotifier.sendSystemAlert(
                                botToken = settingsManager.telegramBotToken,
                                chatId = settingsManager.telegramChatId,
                                deviceName = settingsManager.cameraDeviceName,
                                alertTitle = "🚨 *【外部電源異常斷電警報】*",
                                alertDetails = "偵測到由「充電中」轉為「放電中」！家中可能發生停電或變壓器鬆脫。當前剩餘電量: ${batteryPct}%"
                            )
                        }
                    }
                }

                // Power connected (放電中 -> 充電中)
                if (action == Intent.ACTION_POWER_CONNECTED || (lastIsCharging == false && isCharging)) {
                    if (lastIsCharging != true) {
                        lastIsCharging = true
                        hasSentLowBatteryAlert = false
                        Log.i("CameraStreamService", "Power connected! Battery: $batteryPct%")
                        serviceScope.launch(Dispatchers.IO) {
                            TelegramNotifier.sendSystemAlert(
                                botToken = settingsManager.telegramBotToken,
                                chatId = settingsManager.telegramChatId,
                                deviceName = settingsManager.cameraDeviceName,
                                alertTitle = "🔌 *【外部電源已恢復連接】*",
                                alertDetails = "設備已重新恢復外接電源供電，正在進行充電。當前電量: ${batteryPct}%"
                            )
                        }
                    }
                }

                // Low Battery check (< 60%)
                if (batteryPct in 1..settingsManager.lowBatteryAlertThreshold && !isCharging && !hasSentLowBatteryAlert) {
                    hasSentLowBatteryAlert = true
                    Log.w("CameraStreamService", "Low battery threshold hit! Battery: $batteryPct%")
                    serviceScope.launch(Dispatchers.IO) {
                        TelegramNotifier.sendSystemAlert(
                            botToken = settingsManager.telegramBotToken,
                            chatId = settingsManager.telegramChatId,
                            deviceName = settingsManager.cameraDeviceName,
                            alertTitle = "🪫 *【低電量預警通知】*",
                            alertDetails = "設備電量已降至 ${batteryPct}% (低於 ${settingsManager.lowBatteryAlertThreshold}%)！若持續未供電，設備可能即將關機。"
                        )
                    }
                }

                if (batteryPct > settingsManager.lowBatteryAlertThreshold + 5 || isCharging) {
                    hasSentLowBatteryAlert = false
                }

                lastIsCharging = isCharging
            }
        }

        try {
            registerReceiver(batteryReceiver, filter)
        } catch (e: Exception) {
            Log.e("CameraStreamService", "Failed to register battery receiver", e)
        }
    }

    private fun startScheduleChecker() {
        serviceScope.launch {
            while (true) {
                if (settingsManager.motionScheduleEnabled) {
                    val isTime = isCurrentTimeInSchedule(
                        settingsManager.motionScheduleStartTime,
                        settingsManager.motionScheduleEndTime
                    )
                    // Auto-enable/disable motion detection based on schedule
                    if (cameraHelper.isMotionDetectionEnabled != isTime) {
                        cameraHelper.isMotionDetectionEnabled = isTime
                        settingsManager.motionDetectionEnabled = isTime
                        Log.i("CameraStreamService", "Schedule changed motion detection to: $isTime")
                    }
                }
                kotlinx.coroutines.delay(60000L) // check every minute
            }
        }
    }

    private fun isCurrentTimeInSchedule(start: String, end: String): Boolean {
        try {
            val now = java.util.Calendar.getInstance()
            val currentH = now.get(java.util.Calendar.HOUR_OF_DAY)
            val currentM = now.get(java.util.Calendar.MINUTE)
            val currentTotalM = currentH * 60 + currentM

            val startParts = start.split(":")
            val startH = startParts.getOrNull(0)?.toIntOrNull() ?: 22
            val startM = startParts.getOrNull(1)?.toIntOrNull() ?: 0
            val startTotalM = startH * 60 + startM

            val endParts = end.split(":")
            val endH = endParts.getOrNull(0)?.toIntOrNull() ?: 6
            val endM = endParts.getOrNull(1)?.toIntOrNull() ?: 0
            val endTotalM = endH * 60 + endM

            return if (startTotalM < endTotalM) {
                currentTotalM in startTotalM..endTotalM
            } else {
                currentTotalM >= startTotalM || currentTotalM <= endTotalM
            }
        } catch (e: Exception) {
            return false
        }
    }

    fun startServer() {
        cameraHelper.startCamera(this)
        httpServer.start(serviceScope)
        val ipInfo = NetworkUtils.getIpAddresses(this)
        val ipDisplay = ipInfo.tailscaleIp ?: ipInfo.localIp ?: "127.0.0.1"
        _serviceStatus.value = "Streaming on http://$ipDisplay:${settingsManager.serverPort}"
    }

    fun updateOperatingMode(mode: String) {
        settingsManager.operatingMode = mode
        if (mode == "monitor") {
            // Monitor mode: Disable motion detection alarm feature
            settingsManager.motionDetectionEnabled = false
            cameraHelper.isMotionDetectionEnabled = false
            if (::httpServer.isInitialized && httpServer.connectedClientsCount.get() == 0) {
                cameraHelper.setTorch(false)
            }
        } else {
            // Auto detection mode: Enable motion detection
            settingsManager.motionDetectionEnabled = true
            cameraHelper.isMotionDetectionEnabled = true
        }
    }

    private fun onClientsChanged(count: Int) {
        if (settingsManager.operatingMode == "monitor") {
            if (count == 0) {
                // Disconnected in Monitor mode: Turn off flash & pause camera capture
                cameraHelper.setTorch(false)
            }
        }
    }

    private fun handleRemoteControl(command: String, value: String) {
        serviceScope.launch(Dispatchers.Main) {
            when (command.lowercase()) {
                "mode", "operating_mode" -> {
                    val targetMode = if (value.lowercase().contains("det")) "detection" else "monitor"
                    updateOperatingMode(targetMode)
                }
                "torch" -> {
                    val enable = value.lowercase() == "on" || value == "true"
                    cameraHelper.setTorch(enable)
                }
                "camera" -> {
                    cameraHelper.switchCamera(this@CameraStreamService)
                }
                "quality" -> {
                    val q = value.toIntOrNull() ?: 60
                    cameraHelper.jpegQuality = q
                    settingsManager.defaultQuality = q
                }
                "resolution" -> {
                    cameraHelper.setResolution(value, this@CameraStreamService)
                    settingsManager.defaultResolution = value
                }
                "fps", "target_fps", "fps_limit" -> {
                    val fpsVal = value.toIntOrNull() ?: 15
                    cameraHelper.targetFps = fpsVal
                    cameraHelper.dynamicFpsAdjustmentEnabled = true
                    Log.i("CameraStreamService", "Remote updated target FPS to $fpsVal")
                }
                "rotation", "stream_rotation" -> {
                    val trimmedVal = value.trim()
                    val currentRot = settingsManager.streamRotation
                    val newRot = when (trimmedVal) {
                        "1", "+1", "cw" -> (currentRot + 90) % 360
                        "-1", "ccw" -> (currentRot - 90 + 360) % 360
                        else -> {
                            val rot = trimmedVal.toIntOrNull() ?: 0
                            ((rot % 360) + 360) % 360
                        }
                    }
                    settingsManager.streamRotation = newRot
                }
                "night_vision" -> {
                    cameraHelper.nightVisionMode = value
                    settingsManager.nightVisionMode = value
                }
                "motion" -> {
                    val enable = value.lowercase() == "true" || value == "on"
                    cameraHelper.isMotionDetectionEnabled = enable
                    settingsManager.motionDetectionEnabled = enable
                }
                "sensitivity" -> {
                    val sens = value.toFloatOrNull() ?: 5.0f
                    cameraHelper.motionSensitivity = sens
                    settingsManager.motionSensitivity = sens
                }
                "cooldown" -> {
                    val cd = value.toIntOrNull() ?: 30
                    cameraHelper.motionCooldownSeconds = cd
                    settingsManager.motionCooldownSeconds = cd
                }
                "night_vision_luma" -> {
                    val luma = value.toFloatOrNull() ?: 45.0f
                    cameraHelper.autoNightVisionThreshold = luma
                    settingsManager.autoNightVisionThreshold = luma
                }
                "night_vision_hysteresis" -> {
                    val hyst = value.toFloatOrNull() ?: 8.0f
                    cameraHelper.autoNightVisionHysteresis = hyst
                    settingsManager.autoNightVisionHysteresis = hyst
                }
                "play_alarm_setting", "play_local_alarm" -> {
                    val enable = value.lowercase() == "true" || value == "on"
                    settingsManager.playLocalAlarmOnMotion = enable
                }
                "mlkit_filter" -> {
                    val enable = value.lowercase() == "true" || value == "on"
                    settingsManager.mlKitFilterEnabled = enable
                }
                "auto_cleanup" -> {
                    val enable = value.lowercase() == "true" || value == "on"
                    settingsManager.autoStorageCleanupEnabled = enable
                }
                "storage_limit_gb" -> {
                    val limit = value.toFloatOrNull() ?: 2.0f
                    settingsManager.storageLimitGB = limit
                }
                "max_event_count" -> {
                    val maxCount = value.toIntOrNull() ?: 200
                    settingsManager.maxEventCountLimit = maxCount
                }
                "device_name", "rename" -> {
                    if (value.isNotBlank()) {
                        settingsManager.cameraDeviceName = value.trim()
                        if (::httpServer.isInitialized) httpServer.deviceName = value.trim()
                    }
                }
                "alarm" -> {
                    playAlarmSound()
                }
                "telegram_token" -> {
                    settingsManager.telegramBotToken = value
                    Log.i("CameraStreamService", "Remote updated Telegram Bot Token")
                }
                "telegram_chatid" -> {
                    settingsManager.telegramChatId = value
                    Log.i("CameraStreamService", "Remote updated Telegram Chat ID")
                }
                "telegram_media_type" -> {
                    settingsManager.telegramSendMediaType = value
                    Log.i("CameraStreamService", "Remote updated Telegram Media Type: $value")
                }
                "telegram_config" -> {
                    try {
                        val json = org.json.JSONObject(value)
                        val token = json.optString("token", "")
                        val chatId = json.optString("chatId", "")
                        val mediaType = json.optString("mediaType", "")
                        if (token.isNotBlank()) settingsManager.telegramBotToken = token
                        if (chatId.isNotBlank()) settingsManager.telegramChatId = chatId
                        if (mediaType.isNotBlank()) settingsManager.telegramSendMediaType = mediaType
                        Log.i("CameraStreamService", "Remote updated Telegram config: token length=${token.length}, chatId=$chatId, mediaType=$mediaType")
                    } catch (e: Exception) {
                        Log.e("CameraStreamService", "Error parsing telegram_config JSON", e)
                    }
                }
                "motion_schedule", "motion_schedule_enabled" -> {
                    val enable = value.lowercase() == "true" || value == "on"
                    settingsManager.motionScheduleEnabled = enable
                }
                "motion_schedule_start" -> {
                    if (value.isNotBlank()) settingsManager.motionScheduleStartTime = value
                }
                "motion_schedule_end" -> {
                    if (value.isNotBlank()) settingsManager.motionScheduleEndTime = value
                }
                "notification_schedule", "notification_schedule_enabled" -> {
                    val enable = value.lowercase() == "true" || value == "on"
                    settingsManager.notificationScheduleEnabled = enable
                }
                "notification_schedule_start" -> {
                    if (value.isNotBlank()) settingsManager.notificationScheduleStartTime = value
                }
                "auto_start_boot" -> {
                    val enable = value.lowercase() == "true" || value == "on"
                    settingsManager.autoStartOnBoot = enable
                    Log.i("CameraStreamService", "Remote updated autoStartOnBoot to $enable")
                }
                "power_cut_alert" -> {
                    val enable = value.lowercase() == "true" || value == "on"
                    settingsManager.powerCutAlertEnabled = enable
                    Log.i("CameraStreamService", "Remote updated powerCutAlertEnabled to $enable")
                }
                "notification_schedule_end" -> {
                    if (value.isNotBlank()) settingsManager.notificationScheduleEndTime = value
                }
                "system_log_enabled" -> {
                    val enable = value.lowercase() == "true" || value == "on"
                    settingsManager.systemLogEnabled = enable
                    com.example.util.AppLogger.isEnabled = enable
                }
                "cat_record_toggle" -> {
                    try {
                        val json = org.json.JSONObject(value)
                        val catName = json.optString("category", "")
                        val enabled = json.optBoolean("enabled", true)
                        val category = NotificationCategory.values().find { it.name == catName }
                        if (category != null) {
                            serviceScope.launch(Dispatchers.IO) {
                                SettingsDataStore(this@CameraStreamService).setCategoryRecordingEnabled(category, enabled)
                            }
                        }
                    } catch (e: Exception) {}
                }
                "category_toggle", "cat_toggle" -> {
                    try {
                        val json = org.json.JSONObject(value)
                        val catName = json.optString("category", "")
                        val enabled = json.optBoolean("enabled", true)
                        val category = NotificationCategory.values().find { it.name == catName }
                        if (category != null) {
                            serviceScope.launch(Dispatchers.IO) {
                                SettingsDataStore(this@CameraStreamService).setCategoryEnabled(category, enabled)
                            }
                            Log.i("CameraStreamService", "Remote updated category ${category.name} to $enabled")
                        }
                    } catch (e: Exception) {
                        Log.e("CameraStreamService", "Error parsing category_toggle JSON", e)
                    }
                }
                else -> {
                    if (command.startsWith("cat_")) {
                        val catName = command.removePrefix("cat_")
                        val category = NotificationCategory.values().find { it.name == catName }
                        val enable = value.lowercase() == "true" || value == "on"
                        if (category != null) {
                            serviceScope.launch(Dispatchers.IO) {
                                SettingsDataStore(this@CameraStreamService).setCategoryEnabled(category, enable)
                            }
                            Log.i("CameraStreamService", "Remote updated category ${category.name} to $enable")
                        }
                    }
                }
            }
        }
    }

    private fun handleBatchConfigUpdate(jsonStr: String) {
        serviceScope.launch(Dispatchers.Main) {
            try {
                val json = org.json.JSONObject(jsonStr)

                // 1. device section
                if (json.has("device")) {
                    val deviceObj = json.optJSONObject("device")
                    if (deviceObj != null) {
                        val name = deviceObj.optString("deviceName", "")
                        if (name.isNotBlank()) {
                            settingsManager.cameraDeviceName = name.trim()
                            if (::httpServer.isInitialized) httpServer.deviceName = name.trim()
                        }
                        val opMode = deviceObj.optString("operatingMode", "")
                        if (opMode.isNotBlank()) {
                            updateOperatingMode(opMode)
                        }
                    }
                } else if (json.has("deviceName")) {
                    val name = json.optString("deviceName", "")
                    if (name.isNotBlank()) {
                        settingsManager.cameraDeviceName = name.trim()
                        if (::httpServer.isInitialized) httpServer.deviceName = name.trim()
                    }
                }

                // 2. camera section
                if (json.has("camera")) {
                    val camObj = json.optJSONObject("camera")
                    if (camObj != null) {
                        val res = camObj.optString("resolution", "")
                        if (res.isNotBlank() && res != cameraHelper.currentResolutionString) {
                            settingsManager.defaultResolution = res
                            cameraHelper.setResolution(res, this@CameraStreamService)
                        }
                        if (camObj.has("rotation") || camObj.has("streamRotation")) {
                            val rotOpt = if (camObj.has("rotation")) camObj.opt("rotation") else camObj.opt("streamRotation")
                            val rotStr = rotOpt?.toString()?.trim() ?: "0"
                            val currentRot = settingsManager.streamRotation
                            val newRot = when (rotStr) {
                                "1", "+1", "cw" -> (currentRot + 90) % 360
                                "-1", "ccw" -> (currentRot - 90 + 360) % 360
                                else -> {
                                    val rot = rotStr.toIntOrNull() ?: 0
                                    ((rot % 360) + 360) % 360
                                }
                            }
                            settingsManager.streamRotation = newRot
                        }
                        val quality = camObj.optInt("quality", -1)
                        if (quality in 10..100) {
                            settingsManager.defaultQuality = quality
                            cameraHelper.jpegQuality = quality
                        }
                        val nvMode = camObj.optString("nightVisionMode", "")
                        if (nvMode.isNotBlank()) {
                            settingsManager.nightVisionMode = nvMode
                            cameraHelper.nightVisionMode = nvMode
                        }
                        if (camObj.has("isTorchOn")) {
                            val torch = camObj.optBoolean("isTorchOn", false)
                            cameraHelper.setTorch(torch)
                        }
                        val lens = camObj.optString("lensFacing", "")
                        if (lens.isNotBlank()) {
                            val targetLens = if (lens.lowercase() == "front") androidx.camera.core.CameraSelector.LENS_FACING_FRONT else androidx.camera.core.CameraSelector.LENS_FACING_BACK
                            if (targetLens != cameraHelper.lensFacing) {
                                cameraHelper.switchCamera(this@CameraStreamService)
                            }
                        }
                    }
                }

                // 3. motionDetection section
                if (json.has("motionDetection")) {
                    val mdObj = json.optJSONObject("motionDetection")
                    if (mdObj != null) {
                        if (mdObj.has("enabled")) {
                            val enabled = mdObj.optBoolean("enabled", true)
                            settingsManager.motionDetectionEnabled = enabled
                            cameraHelper.isMotionDetectionEnabled = enabled
                        }
                        if (mdObj.has("scheduleEnabled") || mdObj.has("motionScheduleEnabled")) {
                            settingsManager.motionScheduleEnabled = mdObj.optBoolean("scheduleEnabled", mdObj.optBoolean("motionScheduleEnabled", false))
                        }
                        if (mdObj.has("scheduleStart") || mdObj.has("motionScheduleStart")) {
                            val start = mdObj.optString("scheduleStart", mdObj.optString("motionScheduleStart", "22:00"))
                            if (start.isNotBlank()) settingsManager.motionScheduleStartTime = start
                        }
                        if (mdObj.has("scheduleEnd") || mdObj.has("motionScheduleEnd")) {
                            val end = mdObj.optString("scheduleEnd", mdObj.optString("motionScheduleEnd", "06:00"))
                            if (end.isNotBlank()) settingsManager.motionScheduleEndTime = end
                        }
                        if (mdObj.has("sensitivity")) {
                            val sens = mdObj.optDouble("sensitivity", 5.0).toFloat()
                            settingsManager.motionSensitivity = sens
                            cameraHelper.motionSensitivity = sens
                        }
                        if (mdObj.has("cooldownSeconds")) {
                            val cd = mdObj.optInt("cooldownSeconds", 30)
                            settingsManager.motionCooldownSeconds = cd
                            cameraHelper.motionCooldownSeconds = cd
                        }
                        if (mdObj.has("categories")) {
                            val catObj = mdObj.optJSONObject("categories")
                            if (catObj != null) {
                                val dataStore = SettingsDataStore(this@CameraStreamService)
                                serviceScope.launch(Dispatchers.IO) {
                                    for (cat in NotificationCategory.values()) {
                                        if (catObj.has(cat.name)) {
                                            val catEnable = catObj.optBoolean(cat.name, true)
                                            dataStore.setCategoryEnabled(cat, catEnable)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. recording section
                if (json.has("recording")) {
                    val recObj = json.optJSONObject("recording")
                    if (recObj != null) {
                        if (recObj.has("eventRecordingEnabled")) {
                            val enabled = recObj.optBoolean("eventRecordingEnabled", true)
                            settingsManager.eventVideoRecordingEnabled = enabled
                        }
                        if (recObj.has("maxStorageGb")) {
                            val gb = recObj.optDouble("maxStorageGb", 2.0).toFloat()
                            settingsManager.storageLimitGB = gb
                        }
                        if (recObj.has("categoryRecording")) {
                            val catRecObj = recObj.optJSONObject("categoryRecording")
                            if (catRecObj != null) {
                                val dataStore = SettingsDataStore(this@CameraStreamService)
                                serviceScope.launch(Dispatchers.IO) {
                                    for (cat in NotificationCategory.values()) {
                                        if (catRecObj.has(cat.name)) {
                                            val catRecEnable = catRecObj.optBoolean(cat.name, true)
                                            dataStore.setCategoryRecordingEnabled(cat, catRecEnable)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. notifications section
                if (json.has("notifications")) {
                    val notifObj = json.optJSONObject("notifications")
                    if (notifObj != null) {
                        if (notifObj.has("powerCutAlertEnabled")) {
                            settingsManager.powerCutAlertEnabled = notifObj.optBoolean("powerCutAlertEnabled", true)
                        }
                        if (notifObj.has("systemLogEnabled")) {
                            settingsManager.systemLogEnabled = notifObj.optBoolean("systemLogEnabled", true)
                        }
                        if (notifObj.has("scheduleEnabled") || notifObj.has("notificationScheduleEnabled")) {
                            settingsManager.notificationScheduleEnabled = notifObj.optBoolean("scheduleEnabled", notifObj.optBoolean("notificationScheduleEnabled", false))
                        }
                        if (notifObj.has("scheduleStart") || notifObj.has("notificationScheduleStart")) {
                            val start = notifObj.optString("scheduleStart", notifObj.optString("notificationScheduleStart", "22:00"))
                            if (start.isNotBlank()) settingsManager.notificationScheduleStartTime = start
                        }
                        if (notifObj.has("scheduleEnd") || notifObj.has("notificationScheduleEnd")) {
                            val end = notifObj.optString("scheduleEnd", notifObj.optString("notificationScheduleEnd", "06:00"))
                            if (end.isNotBlank()) settingsManager.notificationScheduleEndTime = end
                        }
                        if (notifObj.has("telegram")) {
                            val tgObj = notifObj.optJSONObject("telegram")
                            if (tgObj != null) {
                                val token = tgObj.optString("botToken", "")
                                val chatId = tgObj.optString("chatId", "")
                                val mediaType = tgObj.optString("mediaType", "")
                                if (token.isNotBlank()) settingsManager.telegramBotToken = token
                                if (chatId.isNotBlank()) settingsManager.telegramChatId = chatId
                                if (mediaType.isNotBlank()) settingsManager.telegramSendMediaType = mediaType
                            }
                        }
                    }
                }

                Log.i("CameraStreamService", "Batch configuration applied successfully")
            } catch (e: Exception) {
                Log.e("CameraStreamService", "Error applying batch configuration", e)
            }
        }
    }

    private fun onMotionTriggered(percentage: Float, thumbnailBytes: ByteArray, frameBitmap: Bitmap?) {
        serviceScope.launch(Dispatchers.IO) {
            val timestamp = System.currentTimeMillis()
            val mediaDir = java.io.File(getExternalFilesDir(null), "media").apply { mkdirs() }

            val dataStore = com.example.data.SettingsDataStore(this@CameraStreamService)
            val enabledCategories = mutableSetOf<com.example.data.NotificationCategory>()
            val enabledRecordingCategories = mutableSetOf<com.example.data.NotificationCategory>()
            for (category in com.example.data.NotificationCategory.values()) {
                if (dataStore.getCategoryEnabled(category).first()) {
                    enabledCategories.add(category)
                }
                if (dataStore.getCategoryRecordingEnabled(category).first()) {
                    enabledRecordingCategories.add(category)
                }
            }

            // Stage 2: Google ML Kit Object Detection / Image Labeling Analysis
            var aiSummary = ""
            var shouldSuppressNotification = false
            var shouldTriggerRecording = true

            if (_isThermalThrottled.value) {
                aiSummary = "🔥 [高溫降載中] AI 分析已自動暫停"
                Log.w("CameraStreamService", "Thermal Throttling Active: Paused ML Kit AI analysis to prevent device overheating.")
            } else if (settingsManager.mlKitFilterEnabled && frameBitmap != null) {
                val mlResult = com.example.util.MlKitFilterHelper.analyzeFrame(this@CameraStreamService, frameBitmap, enabledCategories, enabledRecordingCategories)
                aiSummary = mlResult.summaryText
                shouldSuppressNotification = mlResult.shouldSuppressNotification
                shouldTriggerRecording = mlResult.shouldTriggerRecording
            }

            // Save Instant Snapshot File
            var snapshotPath: String? = null
            if (thumbnailBytes.isNotEmpty()) {
                try {
                    val snapshotFile = java.io.File(mediaDir, "snapshot_${timestamp}.jpg")
                    snapshotFile.writeBytes(thumbnailBytes)
                    snapshotPath = snapshotFile.absolutePath
                } catch (e: Exception) {
                    Log.e("CameraStreamService", "Error saving snapshot file", e)
                }
            }

            // Loop Storage Management & Quota Cleanup (FIFO)
            if (settingsManager.autoStorageCleanupEnabled) {
                try {
                    val currentCount = database.motionEventDao().getEventCount()
                    val maxCount = settingsManager.maxEventCountLimit
                    val mediaFiles = mediaDir.listFiles() ?: emptyArray()
                    val mediaTotalMB = mediaFiles.sumOf { it.length() } / (1024 * 1024)
                    val limitMB = (settingsManager.storageLimitGB * 1024).toLong()

                    val statFs = android.os.StatFs(filesDir.absolutePath)
                    val freeMB = statFs.availableBytes / (1024 * 1024)

                    if (currentCount >= maxCount || mediaTotalMB > limitMB || freeMB < 1000) { // Keep min 1GB free
                        val purgeCount = (currentCount * 0.2).toInt().coerceAtLeast(5)
                        Log.i("CameraStreamService", "Quota cleanup active (count=$currentCount, mediaMB=$mediaTotalMB, freeMB=$freeMB). Purging $purgeCount oldest events & media.")
                        val oldestEvents = database.motionEventDao().getOldestEvents(purgeCount)
                        for (oldEv in oldestEvents) {
                            oldEv.snapshotPath?.let { path -> java.io.File(path).delete() }
                            oldEv.videoPath?.let { path -> java.io.File(path).delete() }
                        }
                        database.motionEventDao().deleteOldestEvents(purgeCount)
                    }
                } catch (e: Exception) {
                    Log.e("CameraStreamService", "Error during loop storage cleanup", e)
                }
            }

            val thumbBase64 = if (thumbnailBytes.isNotEmpty()) {
                android.util.Base64.encodeToString(thumbnailBytes, android.util.Base64.NO_WRAP)
            } else null

            val event = MotionEvent(
                timestamp = timestamp,
                cameraName = settingsManager.cameraDeviceName,
                cameraIp = NetworkUtils.getIpAddresses(this@CameraStreamService).tailscaleIp ?: NetworkUtils.getIpAddresses(this@CameraStreamService).localIp ?: "Unknown",
                motionPercentage = percentage,
                thumbnailBase64 = thumbBase64,
                isRead = false,
                telegramSentSuccess = false,
                aiSummary = aiSummary,
                aiFiltered = shouldSuppressNotification,
                snapshotPath = snapshotPath,
                videoPath = null
            )
            val eventId = database.motionEventDao().insertEvent(event)

            val botToken = settingsManager.telegramBotToken
            val chatId = settingsManager.telegramChatId
            val mediaType = settingsManager.telegramSendMediaType // "photo", "video", or "both"

            val sendVideoAlertIfNeeded: suspend (java.io.File) -> Unit = { videoFile ->
                if ((mediaType == "video" || mediaType == "both") &&
                    botToken.isNotBlank() && chatId.isNotBlank() &&
                    !shouldSuppressNotification
                ) {
                    val sent = TelegramNotifier.sendVideoAlert(
                        botToken = botToken,
                        chatId = chatId,
                        deviceName = settingsManager.cameraDeviceName,
                        motionPercentage = percentage,
                        videoFile = videoFile,
                        aiSummary = aiSummary
                    )
                    if (sent) {
                        Log.i("CameraStreamService", "Telegram video alert sent successfully for event $eventId")
                    } else {
                        Log.e("CameraStreamService", "Telegram video alert failed for event $eventId")
                    }
                }
            }

            // Video Recording Debounce & Prolonging Logic
            if (!shouldTriggerRecording) {
                Log.i("CameraStreamService", "ML Kit Filter: Recording suppressed based on category settings or human-only rule.")
            } else if (_isThermalThrottled.value) {
                Log.w("CameraStreamService", "Thermal Throttling Active: Video recording paused to prevent device overheating.")
            } else if (settingsManager.eventVideoRecordingEnabled) {
                Log.i("CameraStreamService", "Triggering EventVideoRecorder dynamically")
                eventVideoRecorder?.triggerRecording { savedFile ->
                    val success = savedFile != null
                    serviceScope.launch(Dispatchers.IO) {
                        if (success && savedFile != null) {
                            Log.i("CameraStreamService", "Event video recording saved to ${savedFile.absolutePath} for event $eventId")
                            database.motionEventDao().updateVideoPath(eventId, savedFile.absolutePath)
                            sendVideoAlertIfNeeded(savedFile)
                        } else {
                            Log.e("CameraStreamService", "Event video recording failed for event $eventId")
                        }
                    }
                }
            } else {
                synchronized(this@CameraStreamService) {
                    if (cameraHelper.isRecordingVideo) {
                        // Re-motion detected during recording -> Prolong timer
                        Log.i("CameraStreamService", "Motion re-detected! Resetting 20s recording timer.")
                        recordingTimerJob?.cancel()
                        recordingTimerJob = serviceScope.launch(Dispatchers.IO) {
                            kotlinx.coroutines.delay(20000L) // 20s
                            Log.i("CameraStreamService", "20s motion inactivity reached. Stopping video recording.")
                            cameraHelper.stopRecording()
                        }
                    } else {
                        // Start new video recording
                        val videoFile = java.io.File(mediaDir, "video_${timestamp}.mp4")
                        cameraHelper.startRecording(videoFile) { success, videoPath ->
                            serviceScope.launch(Dispatchers.IO) {
                                if (success && videoPath != null) {
                                    Log.i("CameraStreamService", "Video recording saved to $videoPath for event $eventId")
                                    database.motionEventDao().updateVideoPath(eventId, videoPath)
                                    sendVideoAlertIfNeeded(java.io.File(videoPath))
                                } else {
                                    Log.e("CameraStreamService", "Video recording failed for event $eventId")
                                }
                            }
                        }

                        recordingTimerJob?.cancel()
                        recordingTimerJob = serviceScope.launch(Dispatchers.IO) {
                            kotlinx.coroutines.delay(20000L) // 20s
                            Log.i("CameraStreamService", "20s motion inactivity reached. Stopping video recording.")
                            cameraHelper.stopRecording()
                        }
                    }
                }
            }

            if (shouldSuppressNotification) {
                Log.i("CameraStreamService", "ML Kit Filter: Human detected (owner at home). Notification & Alarm suppressed.")
                return@launch
            }

            if (settingsManager.notificationScheduleEnabled) {
                val isTimeForNotification = isCurrentTimeInSchedule(
                    settingsManager.notificationScheduleStartTime,
                    settingsManager.notificationScheduleEndTime
                )
                if (!isTimeForNotification) {
                    Log.i("CameraStreamService", "Notification Schedule: Outside window (${settingsManager.notificationScheduleStartTime} ~ ${settingsManager.notificationScheduleEndTime}). Notification & Alarm suppressed.")
                    return@launch
                }
            }

            if (settingsManager.playLocalAlarmOnMotion) {
                playAlarmSound()
            }

            if (botToken.isNotBlank() && chatId.isNotBlank() && (mediaType == "photo" || mediaType == "both")) {
                val sent = TelegramNotifier.sendMotionAlert(
                    botToken = botToken,
                    chatId = chatId,
                    deviceName = settingsManager.cameraDeviceName,
                    motionPercentage = percentage,
                    photoBytes = thumbnailBytes,
                    aiSummary = aiSummary
                )
                if (sent) {
                    database.motionEventDao().insertEvent(event.copy(id = eventId, telegramSentSuccess = true))
                }
            }
        }
    }

    private fun playAlarmSound() {
        try {
            val toneG = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            toneG.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 800)
        } catch (e: Exception) {
            Log.e("CameraStreamService", "Error playing alarm sound", e)
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OcularNode::CameraStreamWakeLock").apply {
                acquire(10 * 60 * 60 * 1000L)
            }
        } catch (e: Exception) {
            Log.e("CameraStreamService", "Error acquiring WakeLock", e)
        }
    }

    private fun startForegroundServiceNotification() {
        if (settingsManager.deviceRoleMode == "VIEWER") {
            Log.i("CameraStreamService", "Role mode is VIEWER, skipping startForegroundServiceNotification.")
            stopSelf()
            return
        }

        val channelId = "OcularNode_stream_channel"
        val channelName = "OcularNode 串流服務"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("OcularNode 監控服務中")
            .setContentText("MJPEG 伺服器與動態偵測運作中 (內網 Tailscale 連線)")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var type = 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                }
                type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE

                val hasCameraPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.CAMERA
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (hasCameraPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                }

                val hasMicPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (hasMicPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }

                androidx.core.app.ServiceCompat.startForeground(
                    this,
                    1001,
                    notification,
                    type
                )
            } else {
                startForeground(1001, notification)
            }
        } catch (e: Exception) {
            Log.e("CameraStreamService", "Failed to start foreground notification with types", e)
            try {
                startForeground(1001, notification)
            } catch (e2: Exception) {
                Log.e("CameraStreamService", "Fallback startForeground failed", e2)
            }
        }
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(1001)

        httpServer.stop()
        eventVideoRecorder?.release()
        cameraHelper.release()
        audioEngine.release()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        batteryReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }
        
        prefsListener?.let {
            getSharedPreferences("ocularnode_settings", Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(it)
        }
        serviceJob.cancel()
        super.onDestroy()
    }

    fun stopServer() {
        stopSelf()
    }
}
