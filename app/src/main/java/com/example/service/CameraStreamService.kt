package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.ToneGenerator
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
import com.example.data.SettingsManager
import com.example.server.MjpegHttpServer
import com.example.util.NetworkUtils
import com.example.util.TelegramNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CameraStreamService : Service(), LifecycleOwner {

    private val binder = LocalBinder()
    private var wakeLock: PowerManager.WakeLock? = null

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val serviceJob = SupervisorJob()
    val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    lateinit var cameraHelper: CameraManagerHelper
        private set
    lateinit var httpServer: MjpegHttpServer
        private set
    lateinit var audioEngine: AudioEngine
        private set
    lateinit var settingsManager: SettingsManager
        private set
    private lateinit var database: AppDatabase

    private val _serviceStatus = MutableStateFlow("Initializing...")
    val serviceStatus: StateFlow<String> = _serviceStatus.asStateFlow()

    private val _measuredLuma = MutableStateFlow(100f)
    val measuredLuma: StateFlow<Float> = _measuredLuma.asStateFlow()

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
        database = AppDatabase.getDatabase(this)
        audioEngine = AudioEngine()
        cameraHelper = CameraManagerHelper(this)

        // Apply settings
        cameraHelper.jpegQuality = settingsManager.defaultQuality
        cameraHelper.currentResolutionString = settingsManager.defaultResolution
        cameraHelper.nightVisionMode = settingsManager.nightVisionMode
        cameraHelper.autoNightVisionThreshold = settingsManager.autoNightVisionThreshold
        cameraHelper.isMotionDetectionEnabled = settingsManager.motionDetectionEnabled
        cameraHelper.motionSensitivity = settingsManager.motionSensitivity
        cameraHelper.motionCooldownSeconds = settingsManager.motionCooldownSeconds

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

            onActiveClientsChanged = { count ->
                onClientsChanged(count)
            }

            onControlCommand = { cmd, valStr ->
                handleRemoteControl(cmd, valStr)
            }
        }

        // Apply mode configuration
        updateOperatingMode(settingsManager.operatingMode)

        cameraHelper.onFrameEncoded = { jpegBytes ->
            httpServer.updateFrame(jpegBytes)
        }

        cameraHelper.onLumaMeasured = { luma ->
            _measuredLuma.value = luma
        }

        cameraHelper.onMotionDetected = { percentage, thumbnailBytes, frameBitmap ->
            onMotionTriggered(percentage, thumbnailBytes, frameBitmap)
        }

        acquireWakeLock()
        startForegroundServiceNotification()
    }

    fun startServer() {
        cameraHelper.startCamera(this)
        httpServer.start(serviceScope)
        val ipInfo = NetworkUtils.getIpAddresses()
        val ipDisplay = ipInfo.tailscaleIp ?: ipInfo.localIp ?: "127.0.0.1"
        _serviceStatus.value = "Streaming on http://$ipDisplay:${settingsManager.serverPort}"
    }

    fun updateOperatingMode(mode: String) {
        settingsManager.operatingMode = mode
        if (mode == "monitor") {
            // Monitor mode: Disable motion detection alarm feature
            cameraHelper.isMotionDetectionEnabled = false
            if (::httpServer.isInitialized && httpServer.connectedClientsCount.get() == 0) {
                cameraHelper.setTorch(false)
            }
        } else {
            // Auto detection mode: Enable motion detection based on setting
            cameraHelper.isMotionDetectionEnabled = settingsManager.motionDetectionEnabled
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
                "telegram_config" -> {
                    try {
                        val json = org.json.JSONObject(value)
                        val token = json.optString("token", "")
                        val chatId = json.optString("chatId", "")
                        if (token.isNotBlank()) settingsManager.telegramBotToken = token
                        if (chatId.isNotBlank()) settingsManager.telegramChatId = chatId
                        Log.i("CameraStreamService", "Remote updated Telegram config: token length=${token.length}, chatId=$chatId")
                    } catch (e: Exception) {
                        Log.e("CameraStreamService", "Error parsing telegram_config JSON", e)
                    }
                }
            }
        }
    }

    private fun onMotionTriggered(percentage: Float, thumbnailBytes: ByteArray, frameBitmap: Bitmap?) {
        serviceScope.launch(Dispatchers.IO) {
            // Stage 2: Google ML Kit Object Detection / Image Labeling Analysis
            var aiSummary = ""
            var shouldSuppressNotification = false

            if (settingsManager.mlKitFilterEnabled && frameBitmap != null) {
                val mlResult = com.example.util.MlKitFilterHelper.analyzeFrame(frameBitmap)
                aiSummary = mlResult.summaryText
                shouldSuppressNotification = mlResult.shouldSuppressNotification
            }

            // Loop Storage Management: purge oldest events if storage limit exceeded or event count > limit
            if (settingsManager.autoStorageCleanupEnabled) {
                try {
                    val currentCount = database.motionEventDao().getEventCount()
                    val maxCount = settingsManager.maxEventCountLimit
                    val statFs = android.os.StatFs(filesDir.absolutePath)
                    val freeMB = statFs.availableBytes / (1024 * 1024)
                    val limitMB = (settingsManager.storageLimitGB * 1024).toLong()

                    // Estimate database size
                    val dbFile = getDatabasePath("pet_monitor_db")
                    val dbSizeMB = if (dbFile.exists()) dbFile.length() / (1024 * 1024) else 0L

                    if (currentCount >= maxCount || dbSizeMB > limitMB || freeMB < 150) {
                        val purgeCount = (currentCount * 0.15).toInt().coerceAtLeast(10)
                        Log.i("CameraStreamService", "Loop storage active (count=$currentCount/$maxCount, dbSizeMB=$dbSizeMB/$limitMB, freeMB=$freeMB). Purging oldest $purgeCount events.")
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
                timestamp = System.currentTimeMillis(),
                cameraName = settingsManager.cameraDeviceName,
                cameraIp = NetworkUtils.getIpAddresses().tailscaleIp ?: NetworkUtils.getIpAddresses().localIp ?: "Unknown",
                motionPercentage = percentage,
                thumbnailBase64 = thumbBase64,
                isRead = false,
                telegramSentSuccess = false,
                aiSummary = aiSummary,
                aiFiltered = shouldSuppressNotification
            )
            val eventId = database.motionEventDao().insertEvent(event)

            if (shouldSuppressNotification) {
                Log.i("CameraStreamService", "ML Kit Filter: Human detected (owner at home). Notification & Alarm suppressed.")
                return@launch
            }

            if (settingsManager.playLocalAlarmOnMotion) {
                playAlarmSound()
            }

            val botToken = settingsManager.telegramBotToken
            val chatId = settingsManager.telegramChatId
            if (botToken.isNotBlank() && chatId.isNotBlank()) {
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
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TailCamGuard::CameraStreamWakeLock").apply {
                acquire(10 * 60 * 60 * 1000L)
            }
        } catch (e: Exception) {
            Log.e("CameraStreamService", "Error acquiring WakeLock", e)
        }
    }

    private fun startForegroundServiceNotification() {
        val channelId = "tailcam_stream_channel"
        val channelName = "TailCam 串流服務"

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
            .setContentTitle("TailCam 監控服務中")
            .setContentText("MJPEG 伺服器與動態偵測運作中 (內網 Tailscale 連線)")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
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
        cameraHelper.release()
        audioEngine.release()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        serviceJob.cancel()
        super.onDestroy()
    }

    fun stopServer() {
        stopSelf()
    }
}
