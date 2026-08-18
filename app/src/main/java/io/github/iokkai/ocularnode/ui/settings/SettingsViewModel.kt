package io.github.iokkai.ocularnode.ui.settings

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.iokkai.ocularnode.data.AppDatabase
import io.github.iokkai.ocularnode.data.SettingsManager
import io.github.iokkai.ocularnode.util.TelegramNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    val settingsManager = SettingsManager.getInstance(application)

    init {
        io.github.iokkai.ocularnode.util.AppLogger.isEnabled = settingsManager.systemLogEnabled
    }

    private val _roleMode = MutableStateFlow(settingsManager.deviceRoleMode)
    val roleMode: StateFlow<String> = _roleMode.asStateFlow()

    private val _eventVideoRecordingEnabled = MutableStateFlow(settingsManager.eventVideoRecordingEnabled)
    val eventVideoRecordingEnabled: StateFlow<Boolean> = _eventVideoRecordingEnabled.asStateFlow()
    private val _botToken = MutableStateFlow(settingsManager.telegramBotToken)
    val botToken: StateFlow<String> = _botToken.asStateFlow()

    private val _chatId = MutableStateFlow(settingsManager.telegramChatId)
    val chatId: StateFlow<String> = _chatId.asStateFlow()

    private val _telegramSendMediaType = MutableStateFlow(settingsManager.telegramSendMediaType)
    val telegramSendMediaType: StateFlow<String> = _telegramSendMediaType.asStateFlow()

    private val _deviceName = MutableStateFlow(settingsManager.cameraDeviceName)
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    private val _serverPort = MutableStateFlow(settingsManager.serverPort.toString())
    val serverPort: StateFlow<String> = _serverPort.asStateFlow()

    private val _sensitivity = MutableStateFlow(settingsManager.motionSensitivity)
    val sensitivity: StateFlow<Float> = _sensitivity.asStateFlow()

    private val _cooldown = MutableStateFlow(settingsManager.motionCooldownSeconds.toString())
    val cooldown: StateFlow<String> = _cooldown.asStateFlow()

    private val _motionScheduleEnabled = MutableStateFlow(settingsManager.motionScheduleEnabled)
    val motionScheduleEnabled: StateFlow<Boolean> = _motionScheduleEnabled.asStateFlow()

    private val _motionScheduleStartTime = MutableStateFlow(settingsManager.motionScheduleStartTime)
    val motionScheduleStartTime: StateFlow<String> = _motionScheduleStartTime.asStateFlow()

    private val _motionScheduleEndTime = MutableStateFlow(settingsManager.motionScheduleEndTime)
    val motionScheduleEndTime: StateFlow<String> = _motionScheduleEndTime.asStateFlow()

    private val _notificationScheduleEnabled = MutableStateFlow(settingsManager.notificationScheduleEnabled)
    val notificationScheduleEnabled: StateFlow<Boolean> = _notificationScheduleEnabled.asStateFlow()

    private val _notificationScheduleStartTime = MutableStateFlow(settingsManager.notificationScheduleStartTime)
    val notificationScheduleStartTime: StateFlow<String> = _notificationScheduleStartTime.asStateFlow()

    private val _notificationScheduleEndTime = MutableStateFlow(settingsManager.notificationScheduleEndTime)
    val notificationScheduleEndTime: StateFlow<String> = _notificationScheduleEndTime.asStateFlow()

    private val _playAlarm = MutableStateFlow(settingsManager.playLocalAlarmOnMotion)
    val playAlarm: StateFlow<Boolean> = _playAlarm.asStateFlow()

    private val _autoCleanupEnabled = MutableStateFlow(settingsManager.autoStorageCleanupEnabled)
    val autoCleanupEnabled: StateFlow<Boolean> = _autoCleanupEnabled.asStateFlow()

    private val _storageLimitGB = MutableStateFlow(settingsManager.storageLimitGB)
    val storageLimitGB: StateFlow<Float> = _storageLimitGB.asStateFlow()

    private val _maxEventCount = MutableStateFlow(settingsManager.maxEventCountLimit)
    val maxEventCount: StateFlow<Int> = _maxEventCount.asStateFlow()

    private val _livePreviewInListEnabled = MutableStateFlow(settingsManager.livePreviewInListEnabled)
    val livePreviewInListEnabled: StateFlow<Boolean> = _livePreviewInListEnabled.asStateFlow()

    private val _autoStartOnBoot = MutableStateFlow(settingsManager.autoStartOnBoot)
    val autoStartOnBoot: StateFlow<Boolean> = _autoStartOnBoot.asStateFlow()

    private val _systemLogEnabled = MutableStateFlow(settingsManager.systemLogEnabled)
    val systemLogEnabled: StateFlow<Boolean> = _systemLogEnabled.asStateFlow()

    private val _dynamicFpsAdjustmentEnabled = MutableStateFlow(settingsManager.dynamicFpsAdjustmentEnabled)
    val dynamicFpsAdjustmentEnabled: StateFlow<Boolean> = _dynamicFpsAdjustmentEnabled.asStateFlow()

    private val _powerCutAlertEnabled = MutableStateFlow(settingsManager.powerCutAlertEnabled)
    val powerCutAlertEnabled: StateFlow<Boolean> = _powerCutAlertEnabled.asStateFlow()

    private val _mlKitFilterEnabled = MutableStateFlow(settingsManager.mlKitFilterEnabled)
    val mlKitFilterEnabled: StateFlow<Boolean> = _mlKitFilterEnabled.asStateFlow()

    private val _httpAuthEnabled = MutableStateFlow(settingsManager.httpAuthEnabled)
    val httpAuthEnabled: StateFlow<Boolean> = _httpAuthEnabled.asStateFlow()

    private val _httpPinCode = MutableStateFlow(settingsManager.httpPinCode)
    val httpPinCode: StateFlow<String> = _httpPinCode.asStateFlow()

    private val _scheduledRebootEnabled = MutableStateFlow(settingsManager.scheduledRebootEnabled)
    val scheduledRebootEnabled: StateFlow<Boolean> = _scheduledRebootEnabled.asStateFlow()

    private val _scheduledRebootTime = MutableStateFlow(settingsManager.scheduledRebootTime)
    val scheduledRebootTime: StateFlow<String> = _scheduledRebootTime.asStateFlow()

    fun updateScheduledRebootEnabled(enabled: Boolean) {
        _scheduledRebootEnabled.value = enabled
        settingsManager.scheduledRebootEnabled = enabled
    }

    fun updateScheduledRebootTime(time: String) {
        val trimmed = time.trim()
        _scheduledRebootTime.value = trimmed
        if (trimmed.isNotBlank()) {
            settingsManager.scheduledRebootTime = trimmed
        }
    }

    fun updateHttpAuthEnabled(enabled: Boolean) {
        _httpAuthEnabled.value = enabled
        settingsManager.httpAuthEnabled = enabled
    }

    fun updateHttpPinCode(pin: String) {
        val trimmed = pin.trim()
        _httpPinCode.value = trimmed
        if (trimmed.isNotBlank()) {
            settingsManager.httpPinCode = trimmed
        }
    }

    private val _cleanupStatus = MutableStateFlow<String?>(null)
    val cleanupStatus: StateFlow<String?> = _cleanupStatus.asStateFlow()

    private val _testStatus = MutableStateFlow<String?>(null)
    val testStatus: StateFlow<String?> = _testStatus.asStateFlow()

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun clearStatus() {
        _cleanupStatus.value = null
        _syncStatus.value = null
        _testStatus.value = null
    }

    fun reloadSettings() {
        _eventVideoRecordingEnabled.value = settingsManager.eventVideoRecordingEnabled
        _botToken.value = settingsManager.telegramBotToken
        _chatId.value = settingsManager.telegramChatId
        _telegramSendMediaType.value = settingsManager.telegramSendMediaType
    }

    fun updateTelegramSendMediaType(type: String) {
        _telegramSendMediaType.value = type
        settingsManager.telegramSendMediaType = type
    }

    fun updateRoleMode(mode: String) {
        clearStatus()
        _roleMode.value = mode
        settingsManager.deviceRoleMode = mode
        if (mode == "VIEWER") {
            try {
                val app = getApplication<Application>()
                val intent = android.content.Intent(app, io.github.iokkai.ocularnode.service.CameraStreamService::class.java)
                app.stopService(intent)
                val notificationManager = app.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                notificationManager?.cancel(1001)
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Error stopping service on role change", e)
            }
        }
    }

    fun updateAutoStartOnBoot(enabled: Boolean) {
        _autoStartOnBoot.value = enabled
        settingsManager.autoStartOnBoot = enabled
    }

    fun updatePowerCutAlertEnabled(enabled: Boolean) {
        _powerCutAlertEnabled.value = enabled
        settingsManager.powerCutAlertEnabled = enabled
    }

    fun updateMlKitFilterEnabled(enabled: Boolean) {
        _mlKitFilterEnabled.value = enabled
        settingsManager.mlKitFilterEnabled = enabled
    }

    fun updateBotToken(token: String) {
        _botToken.value = token
        settingsManager.telegramBotToken = token
    }

    fun updateChatId(id: String) {
        _chatId.value = id
        settingsManager.telegramChatId = id
    }

    fun syncTelegramToCameras() {
        if (_isSyncing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isSyncing.value = true
            val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            val startTime = timeFormat.format(java.util.Date())
            _syncStatus.value = "⏳ [$startTime] Syncing to cameras..."

            try {
                val token = _botToken.value
                val id = _chatId.value
                val cameras = AppDatabase.getDatabase(getApplication()).cameraDeviceDao().getCamerasListOnce()
                if (cameras.isEmpty()) {
                    _syncStatus.value = "⚠️ [$startTime] No camera devices linked"
                    return@launch
                }

                val client = OkHttpClient.Builder()
                    .connectTimeout(3, TimeUnit.SECONDS)
                    .readTimeout(3, TimeUnit.SECONDS)
                    .build()

                val configJson = JSONObject().apply {
                    put("token", token)
                    put("chatId", id)
                    put("mediaType", _telegramSendMediaType.value)
                }.toString()

                var syncedCount = 0
                val errors = mutableListOf<String>()
                for (cam in cameras) {
                    try {
                        val reqJson = JSONObject().apply {
                            put("command", "telegram_config")
                            put("value", configJson)
                        }
                        val body = reqJson.toString().toRequestBody("application/json".toMediaType())
                        val request = Request.Builder()
                            .url(cam.getControlUrl())
                            .post(body)
                            .build()

                        val response = client.newCall(request).execute()
                        if (response.isSuccessful) syncedCount++
                        response.close()
                    } catch (e: Exception) {
                        Log.e("SettingsViewModel", "Failed sync to ${cam.name} (${cam.ipAddress})", e)
                        errors.add("${cam.name}(${cam.ipAddress})")
                    }
                }
                val finishTime = timeFormat.format(java.util.Date())
                if (syncedCount > 0) {
                    _syncStatus.value = "⚡ [$finishTime] Successfully synced to $syncedCount connected cameras"
                } else {
                    val failedList = errors.joinToString(", ")
                    _syncStatus.value = "❌ [$finishTime] Cannot connect to camera: $failedList\nPlease ensure camera service is running on the same network"
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Error syncing to cameras", e)
                _syncStatus.value = "❌ Sync error: ${e.message}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun updateDeviceName(name: String) {
        _deviceName.value = name
        settingsManager.cameraDeviceName = name
    }

    fun updateServerPort(portStr: String) {
        _serverPort.value = portStr
        portStr.toIntOrNull()?.let { settingsManager.serverPort = it }
    }

    fun updateSensitivity(value: Float) {
        _sensitivity.value = value
        settingsManager.motionSensitivity = value
    }

    fun updateMotionScheduleEnabled(enabled: Boolean) {
        _motionScheduleEnabled.value = enabled
        settingsManager.motionScheduleEnabled = enabled
    }

    fun updateMotionScheduleStartTime(time: String) {
        _motionScheduleStartTime.value = time
        settingsManager.motionScheduleStartTime = time
    }

    fun updateMotionScheduleEndTime(time: String) {
        _motionScheduleEndTime.value = time
        settingsManager.motionScheduleEndTime = time
    }

    fun updateNotificationScheduleEnabled(enabled: Boolean) {
        _notificationScheduleEnabled.value = enabled
        settingsManager.notificationScheduleEnabled = enabled
    }

    fun updateNotificationScheduleStartTime(time: String) {
        _notificationScheduleStartTime.value = time
        settingsManager.notificationScheduleStartTime = time
    }

    fun updateNotificationScheduleEndTime(time: String) {
        _notificationScheduleEndTime.value = time
        settingsManager.notificationScheduleEndTime = time
    }

    fun updateCooldown(cooldownStr: String) {
        _cooldown.value = cooldownStr
        cooldownStr.toIntOrNull()?.let { settingsManager.motionCooldownSeconds = it }
    }

    fun updatePlayAlarm(play: Boolean) {
        _playAlarm.value = play
        settingsManager.playLocalAlarmOnMotion = play
    }

    fun updateAutoCleanupEnabled(enabled: Boolean) {
        _autoCleanupEnabled.value = enabled
        settingsManager.autoStorageCleanupEnabled = enabled
    }

    fun updateStorageLimitGB(limitGB: Float) {
        _storageLimitGB.value = limitGB
        settingsManager.storageLimitGB = limitGB
    }

    fun updateMaxEventCount(count: Int) {
        _maxEventCount.value = count
        settingsManager.maxEventCountLimit = count
    }


    fun updateEventVideoRecordingEnabled(enabled: Boolean) {
        settingsManager.eventVideoRecordingEnabled = enabled
        reloadSettings()
    }

    fun updateLivePreviewInListEnabled(enabled: Boolean) {
        _livePreviewInListEnabled.value = enabled
        settingsManager.livePreviewInListEnabled = enabled
    }

    fun performManualCleanup() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val eventDao = AppDatabase.getDatabase(context).motionEventDao()
                val beforeCount = eventDao.getEventCount()
                if (beforeCount == 0) {
                    cleanOrphanMediaFiles(context, emptyList())
                    _cleanupStatus.value = "No history records found, cleaned media folder"
                    return@launch
                }
                // Purge 80% oldest records
                val purgeCount = (beforeCount * 0.8).toInt().coerceAtLeast(1)
                val oldestEvents = eventDao.getOldestEvents(purgeCount)
                for (oldEv in oldestEvents) {
                    oldEv.snapshotPath?.let { path ->
                        try { java.io.File(path).delete() } catch (_: Exception) {}
                    }
                    oldEv.videoPath?.let { path ->
                        try { java.io.File(path).delete() } catch (_: Exception) {}
                    }
                }
                eventDao.deleteOldestEvents(purgeCount)

                // Sweep orphan files
                val remainingEvents = eventDao.getEventsListOnce()
                cleanOrphanMediaFiles(context, remainingEvents)

                val afterCount = eventDao.getEventCount()
                _cleanupStatus.value = "🧹 Successfully cleaned oldest $purgeCount snapshots and media files ($afterCount remaining)"
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Error during performManualCleanup", e)
                _cleanupStatus.value = "Cleanup failed: ${e.message}"
            }
        }
    }

    private fun cleanOrphanMediaFiles(context: android.content.Context, validEvents: List<io.github.iokkai.ocularnode.data.MotionEvent>) {
        val validPaths = HashSet<String>()
        for (ev in validEvents) {
            ev.snapshotPath?.let { validPaths.add(it) }
            ev.videoPath?.let { validPaths.add(it) }
        }

        val mediaDirs = listOfNotNull(
            context.getExternalFilesDir(null)?.let { java.io.File(it, "media") },
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)?.let { java.io.File(it, "OcularNode") }
        )

        for (dir in mediaDirs) {
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && !validPaths.contains(file.absolutePath)) {
                        try { file.delete() } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    fun updateSystemLogEnabled(enabled: Boolean) {
        settingsManager.systemLogEnabled = enabled
        _systemLogEnabled.value = enabled
        io.github.iokkai.ocularnode.util.AppLogger.isEnabled = enabled
    }

    fun updateDynamicFpsAdjustmentEnabled(enabled: Boolean) {
        settingsManager.dynamicFpsAdjustmentEnabled = enabled
        _dynamicFpsAdjustmentEnabled.value = enabled
    }

    fun testTelegram() {
        _isTesting.value = true
        _testStatus.value = "Sending test alert..."
        viewModelScope.launch {
            val testResult = TelegramNotifier.testBotConnection(_botToken.value, _chatId.value, getApplication())
            _testStatus.value = testResult.second
            _isTesting.value = false
        }
    }
}
