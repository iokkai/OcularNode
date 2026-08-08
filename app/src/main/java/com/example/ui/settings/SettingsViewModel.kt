package com.example.ui.settings

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.SettingsManager
import com.example.util.TelegramNotifier
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

    val settingsManager = SettingsManager(application)

    private val _roleMode = MutableStateFlow(settingsManager.deviceRoleMode)
    val roleMode: StateFlow<String> = _roleMode.asStateFlow()

    private val _botToken = MutableStateFlow(settingsManager.telegramBotToken)
    val botToken: StateFlow<String> = _botToken.asStateFlow()

    private val _chatId = MutableStateFlow(settingsManager.telegramChatId)
    val chatId: StateFlow<String> = _chatId.asStateFlow()

    private val _deviceName = MutableStateFlow(settingsManager.cameraDeviceName)
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    private val _serverPort = MutableStateFlow(settingsManager.serverPort.toString())
    val serverPort: StateFlow<String> = _serverPort.asStateFlow()

    private val _sensitivity = MutableStateFlow(settingsManager.motionSensitivity)
    val sensitivity: StateFlow<Float> = _sensitivity.asStateFlow()

    private val _cooldown = MutableStateFlow(settingsManager.motionCooldownSeconds.toString())
    val cooldown: StateFlow<String> = _cooldown.asStateFlow()

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

    private val _cleanupStatus = MutableStateFlow<String?>(null)
    val cleanupStatus: StateFlow<String?> = _cleanupStatus.asStateFlow()

    private val _testStatus = MutableStateFlow<String?>(null)
    val testStatus: StateFlow<String?> = _testStatus.asStateFlow()

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    fun updateRoleMode(mode: String) {
        _roleMode.value = mode
        settingsManager.deviceRoleMode = mode
    }

    fun updateBotToken(token: String) {
        _botToken.value = token
        settingsManager.telegramBotToken = token
        syncTelegramToCameras()
    }

    fun updateChatId(id: String) {
        _chatId.value = id
        settingsManager.telegramChatId = id
        syncTelegramToCameras()
    }

    fun syncTelegramToCameras() {
        viewModelScope.launch(Dispatchers.IO) {
            val token = _botToken.value
            val id = _chatId.value
            val cameras = AppDatabase.getDatabase(getApplication()).cameraDeviceDao().getCamerasListOnce()
            if (cameras.isEmpty()) return@launch

            val client = OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .build()

            val configJson = JSONObject().apply {
                put("token", token)
                put("chatId", id)
            }.toString()

            var syncedCount = 0
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
                    Log.e("SettingsViewModel", "Failed sync to ${cam.name}", e)
                }
            }
            if (syncedCount > 0) {
                _syncStatus.value = "⚡ 已自動同步 Telegram 設定至 $syncedCount 個連線鏡頭"
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

    fun updateLivePreviewInListEnabled(enabled: Boolean) {
        _livePreviewInListEnabled.value = enabled
        settingsManager.livePreviewInListEnabled = enabled
    }

    fun performManualCleanup() {
        viewModelScope.launch(Dispatchers.IO) {
            val eventDao = AppDatabase.getDatabase(getApplication()).motionEventDao()
            val beforeCount = eventDao.getEventCount()
            if (beforeCount == 0) {
                _cleanupStatus.value = "目前無任何歷史紀錄需要清理"
                return@launch
            }
            // Purge 20% oldest records
            val purgeCount = (beforeCount * 0.2).toInt().coerceAtLeast(1)
            eventDao.deleteOldestEvents(purgeCount)
            val afterCount = eventDao.getEventCount()
            _cleanupStatus.value = "🧹 已成功自動清理最舊的 $purgeCount 筆歷史快照 (剩餘 $afterCount 筆)"
        }
    }

    fun testTelegram() {
        _isTesting.value = true
        _testStatus.value = "發送測試推播中..."
        viewModelScope.launch {
            val (success, msg) = TelegramNotifier.testBotConnection(_botToken.value, _chatId.value)
            _testStatus.value = msg
            _isTesting.value = false
        }
    }
}
