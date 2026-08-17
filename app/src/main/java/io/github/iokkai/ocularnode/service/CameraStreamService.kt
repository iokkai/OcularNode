package io.github.iokkai.ocularnode.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Binder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.github.iokkai.ocularnode.MainActivity
import io.github.iokkai.ocularnode.R
import io.github.iokkai.ocularnode.audio.AudioEngine
import io.github.iokkai.ocularnode.camera.CameraManagerHelper
import io.github.iokkai.ocularnode.camera.EventVideoRecorder
import io.github.iokkai.ocularnode.data.AppDatabase
import io.github.iokkai.ocularnode.data.SettingsManager
import io.github.iokkai.ocularnode.server.MjpegHttpServer
import io.github.iokkai.ocularnode.util.NetworkUtils
import io.github.iokkai.ocularnode.util.TelegramNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Foreground Service orchestrator for camera streaming, motion detection,
 * HTTP MJPEG server, and thermal/power monitoring.
 */
class CameraStreamService : Service(), LifecycleOwner {

    companion object {
        @Volatile
        var isServiceRunning: Boolean = false
            private set
    }

    private val binder = LocalBinder()
    private var wakeLock: PowerManager.WakeLock? = null

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val serviceJob = SupervisorJob()
    val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var eventVideoRecorder: EventVideoRecorder? = null
    private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    lateinit var cameraHelper: CameraManagerHelper
        private set
    lateinit var httpServer: MjpegHttpServer
        private set
    lateinit var audioEngine: AudioEngine
        private set
    lateinit var settingsManager: SettingsManager
        private set
    private lateinit var database: AppDatabase

    // Modular managers
    private lateinit var batteryMonitor: BatteryPowerMonitor
    private lateinit var scheduleManager: ScheduleManager
    private lateinit var motionPipelineManager: MotionPipelineManager
    private lateinit var remoteCommandHandler: RemoteCommandHandler

    private val _serviceStatus = MutableStateFlow("Initializing...")
    val serviceStatus: StateFlow<String> = _serviceStatus.asStateFlow()

    private val _measuredLuma = MutableStateFlow(100f)
    val measuredLuma: StateFlow<Float> = _measuredLuma.asStateFlow()

    val isThermalThrottled: StateFlow<Boolean> get() = batteryMonitor.isThermalThrottled
    val batteryTemp: StateFlow<Float> get() = batteryMonitor.batteryTemp

    inner class LocalBinder : Binder() {
        fun getService(): CameraStreamService = this@CameraStreamService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        settingsManager = SettingsManager.getInstance(this)
        if (settingsManager.deviceRoleMode == "VIEWER") {
            Log.i("CameraStreamService", "Device is set to VIEWER mode, stopping CameraStreamService.")
            stopSelf()
            return
        }

        database = AppDatabase.getDatabase(this)
        audioEngine = AudioEngine()
        cameraHelper = CameraManagerHelper(this)

        // Apply initial settings
        applyInitialCameraSettings()
        registerPreferenceChangeListener()

        // Initialize Managers
        batteryMonitor = BatteryPowerMonitor(
            context = this,
            scope = serviceScope,
            settingsManager = settingsManager,
            onThermalThrottleStart = {
                motionPipelineManager.cancelActiveRecordingTimer()
                if (cameraHelper.isRecordingVideo) {
                    cameraHelper.stopRecording()
                    Log.i("CameraStreamService", "Thermal Throttling: Active video recording stopped.")
                }
            }
        )

        scheduleManager = ScheduleManager(
            scope = serviceScope,
            settingsManager = settingsManager,
            cameraHelper = cameraHelper
        )

        motionPipelineManager = MotionPipelineManager(
            context = this,
            scope = serviceScope,
            settingsManager = settingsManager,
            database = database,
            cameraHelper = cameraHelper,
            eventVideoRecorderGetter = { eventVideoRecorder },
            isThermalThrottled = { isThermalThrottled.value }
        )

        remoteCommandHandler = RemoteCommandHandler(
            context = this,
            scope = serviceScope,
            settingsManager = settingsManager,
            cameraHelper = cameraHelper,
            httpServerGetter = { if (::httpServer.isInitialized) httpServer else null },
            updateOperatingMode = { mode -> updateOperatingMode(mode) },
            playAlarmSound = { motionPipelineManager.playAlarmSound() }
        )

        // Configure MJPEG HTTP Server
        setupHttpServer()

        // Configure EventVideoRecorder
        setupEventVideoRecorder()

        // Wire CameraHelper callbacks
        setupCameraHelperCallbacks()

        // Start background tasks & foreground service
        scheduleManager.start()
        acquireWakeLock()
        batteryMonitor.register()
        setupNetworkMonitoring()
        startForegroundServiceNotification()
    }

    private fun applyInitialCameraSettings() {
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
    }

    private fun registerPreferenceChangeListener() {
        prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "motion_sensitivity" -> cameraHelper.motionSensitivity = settingsManager.motionSensitivity
                "motion_cooldown" -> cameraHelper.motionCooldownSeconds = settingsManager.motionCooldownSeconds
                "motion_enabled" -> cameraHelper.isMotionDetectionEnabled = settingsManager.motionDetectionEnabled
                "dynamic_fps_enabled" -> cameraHelper.dynamicFpsAdjustmentEnabled = settingsManager.dynamicFpsAdjustmentEnabled
                "default_quality" -> {
                    val newQuality = settingsManager.defaultQuality
                    cameraHelper.defaultJpegQuality = newQuality
                    cameraHelper.jpegQuality = newQuality
                }
                "default_resolution" -> cameraHelper.setResolution(settingsManager.defaultResolution, this)
                "night_vision_hysteresis" -> cameraHelper.autoNightVisionHysteresis = settingsManager.autoNightVisionHysteresis
            }
        }
        getSharedPreferences("ocularnode_settings", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefsListener)
    }

    private fun setupHttpServer() {
        httpServer = MjpegHttpServer(this, settingsManager.serverPort, audioEngine).apply {
            deviceName = settingsManager.cameraDeviceName
            lensFacing = if (cameraHelper.lensFacing == CameraSelector.LENS_FACING_BACK) "back" else "front"
            isTorchOn = cameraHelper.isTorchOn
            resolution = cameraHelper.currentResolutionString
            quality = cameraHelper.jpegQuality
            nightVisionMode = cameraHelper.nightVisionMode
            isNightVisionActive = cameraHelper.isNightVisionActive
            isMotionEnabled = cameraHelper.isMotionDetectionEnabled
            operatingMode = settingsManager.operatingMode
            isThermalThrottled = this@CameraStreamService.isThermalThrottled.value
            batteryTemp = this@CameraStreamService.batteryTemp.value

            onActiveClientsChanged = { count ->
                onClientsChanged(count)
            }

            onControlCommand = { cmd, valStr ->
                remoteCommandHandler.handleRemoteControl(cmd, valStr)
            }

            onBatchConfigUpdated = { configJsonStr ->
                remoteCommandHandler.handleBatchConfigUpdate(configJsonStr)
            }
        }

        // Apply initial mode configuration
        updateOperatingMode(settingsManager.operatingMode)
    }

    private fun setupEventVideoRecorder() {
        val mediaDir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "OcularNode")
        if (!mediaDir.exists()) mediaDir.mkdirs()

        eventVideoRecorder = EventVideoRecorder(
            outputDir = mediaDir,
            width = 1280,
            height = 720,
            fps = 15,
            preRecordSeconds = 5,
            basePostRecordSeconds = 10,
            maxRecordSeconds = 180
        )
    }

    private fun setupCameraHelperCallbacks() {
        cameraHelper.onFrameReadyForRecording = { jpegBytes, timestampUs ->
            if (settingsManager.eventVideoRecordingEnabled && !isThermalThrottled.value) {
                eventVideoRecorder?.pushFrame(jpegBytes, timestampUs)
            }
        }

        cameraHelper.onFrameEncoded = { jpegBytes ->
            httpServer.pushFrame(jpegBytes)
        }

        cameraHelper.onLumaMeasured = { luma ->
            _measuredLuma.value = luma
        }

        cameraHelper.hasActiveConsumers = {
            (::httpServer.isInitialized && httpServer.isRunning) || settingsManager.eventVideoRecordingEnabled
        }

        cameraHelper.onMotionDetected = { percentage, thumbnailBytes, frameBitmap ->
            motionPipelineManager.processMotion(percentage, thumbnailBytes, frameBitmap)
        }
    }

    private var hasObservedTailscaleConnectedOnce = false
    private var tailscaleDisconnectAlertSent = false

    /**
     * 監聽網路狀態，當 Tailscale VPN 中斷（可能為 180 天金鑰過期）時發送 Telegram 警報
     */
    private fun setupNetworkMonitoring() {
        serviceScope.launch {
            NetworkUtils.observeNetworkStatus(this@CameraStreamService).collect { ipInfo ->
                if (ipInfo.isTailscaleConnected) {
                    hasObservedTailscaleConnectedOnce = true
                    tailscaleDisconnectAlertSent = false
                } else if (hasObservedTailscaleConnectedOnce && !tailscaleDisconnectAlertSent) {
                    // 本地 Wi-Fi 仍連線，但 Tailscale VPN 遺失 (可能為 180 天金鑰過期)
                    if (ipInfo.localIp != null) {
                        tailscaleDisconnectAlertSent = true
                        Log.w("CameraStreamService", "⚠️ Tailscale VPN disconnected while Wi-Fi is active. Key might have expired.")

                        if (settingsManager.telegramBotToken.isNotBlank() && settingsManager.telegramChatId.isNotBlank()) {
                            TelegramNotifier.sendSystemAlert(
                                botToken = settingsManager.telegramBotToken,
                                chatId = settingsManager.telegramChatId,
                                deviceName = settingsManager.cameraDeviceName,
                                alertTitle = getString(R.string.tg_alert_tailscale_disconnected_title),
                                alertDetails = getString(R.string.tg_alert_tailscale_disconnected_desc),
                                context = this@CameraStreamService
                            )
                        }
                    }
                }
            }
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
            if (::httpServer.isInitialized && httpServer.streamHandler.connectedClientsCount.get() == 0) {
                cameraHelper.setTorch(false)
            }
        } else {
            // Auto detection mode: Enable motion detection
            settingsManager.motionDetectionEnabled = true
            cameraHelper.isMotionDetectionEnabled = true
        }
    }

    private fun onClientsChanged(count: Int) {
        if (settingsManager.operatingMode == "monitor" && count == 0) {
            // Disconnected in Monitor mode: Turn off flash & pause camera capture
            cameraHelper.setTorch(false)
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OcularNode::CameraStreamWakeLock").apply {
                setReferenceCounted(false)
                acquire()
            }
            Log.i("CameraStreamService", "Acquired persistent partial WakeLock")
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
        val channelName = getString(R.string.camera_stream_service_title)

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
            .setContentTitle(getString(R.string.camera_stream_service_title))
            .setContentText(getString(R.string.camera_stream_service_desc))
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

                val hasCameraPermission = ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.CAMERA
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (hasCameraPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                }

                val hasMicPermission = ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (hasMicPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }

                ServiceCompat.startForeground(
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
        batteryMonitor.unregister()
        scheduleManager.stop()

        prefsListener?.let {
            getSharedPreferences("ocularnode_settings", Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(it)
        }
        isServiceRunning = false
        serviceJob.cancel()
        super.onDestroy()
    }

    fun stopServer() {
        stopSelf()
    }
}
