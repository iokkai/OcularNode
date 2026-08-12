package com.example.ui.settings

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MainActivity
import com.example.data.SettingsManager
import com.example.util.TelegramCheckResult
import com.example.util.TelegramConfig
import com.example.util.TelegramNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed class TelegramSetupUiState {
    object Step1_InputToken : TelegramSetupUiState()
    data class Step2_Listening(
        val token: String,
        val pin: String,
        val botName: String = "",
        val botUsername: String = "",
        val remainingSeconds: Int = 120,
        val isLoadingBotInfo: Boolean = false
    ) : TelegramSetupUiState()

    data class Success(
        val token: String,
        val chatId: String
    ) : TelegramSetupUiState()

    object Timeout : TelegramSetupUiState()
    data class Error(val message: String) : TelegramSetupUiState()
}

class TelegramSetupViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val CHANNEL_ID = "telegram_setup_channel_high"
        private const val NOTIFICATION_ID = 9001
    }

    private val settingsManager = SettingsManager(application)

    private val _uiState = MutableStateFlow<TelegramSetupUiState>(TelegramSetupUiState.Step1_InputToken)
    val uiState: StateFlow<TelegramSetupUiState> = _uiState.asStateFlow()

    val savedToken: String
        get() = settingsManager.telegramBotToken

    val savedChatId: String
        get() = settingsManager.telegramChatId

    private var pollingJob: Job? = null

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Telegram 自動綁定通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "在背景執行 Telegram 配對時發送進度與結果通知"
            }
            val manager = getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun showNotification(title: String, content: String, isOngoing: Boolean = false) {
        val context = getApplication<Application>()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_TELEGRAM_SETUP", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .setOngoing(isOngoing)
            .setAutoCancel(!isOngoing)
            .setContentIntent(pendingIntent)

        val notificationManager = NotificationManagerCompat.from(context)
        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            Log.e("TelegramSetupVM", "Notification permission not granted for local notification", e)
        } catch (e: Exception) {
            Log.e("TelegramSetupVM", "Error posting notification", e)
        }
    }

    private fun cancelNotification() {
        val context = getApplication<Application>()
        val notificationManager = NotificationManagerCompat.from(context)
        try {
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e("TelegramSetupVM", "Error cancelling notification", e)
        }
    }

    private fun startForegroundService() {
        val context = getApplication<Application>()
        val intent = Intent(context, com.example.service.TelegramPairingService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e("TelegramSetupVM", "Failed to start foreground service", e)
        }
    }

    private fun stopForegroundService() {
        val context = getApplication<Application>()
        val intent = Intent(context, com.example.service.TelegramPairingService::class.java)
        try {
            context.stopService(intent)
        } catch (e: Exception) {
            Log.e("TelegramSetupVM", "Failed to stop foreground service", e)
        }
    }

    fun startPairing(tokenInput: String) {
        val token = tokenInput.trim()
        if (token.isBlank()) {
            _uiState.value = TelegramSetupUiState.Error("請輸入有效的 Telegram Bot Token")
            return
        }

        // 停止之前的輪詢任務
        pollingJob?.cancel()
        stopForegroundService()

        pollingJob = viewModelScope.launch(Dispatchers.IO) {
            startForegroundService()
            
            // 產生 4 位數驗證 PIN 碼 (1000 ~ 9999)
            val pin = String.format(java.util.Locale.US, "%04d", (1000..9999).random())

            _uiState.value = TelegramSetupUiState.Step2_Listening(
                token = token,
                pin = pin,
                isLoadingBotInfo = true
            )

            val botInfo = TelegramConfig.getBotInfo(token)
            if (botInfo == null) {
                _uiState.value = TelegramSetupUiState.Error("無法取得機器人資訊，請確認 Token 是否正確")
                return@launch
            }

            _uiState.value = TelegramSetupUiState.Step2_Listening(
                token = token,
                pin = pin,
                botName = botInfo.firstName,
                botUsername = botInfo.username,
                remainingSeconds = 120,
                isLoadingBotInfo = false
            )

            // 先自動清除殘留的 Webhook
            TelegramConfig.clearWebhook(token)

            val totalTimeoutMs = 120_000L
            var secondsLeft = 120
            var fatalErrorMsg: String? = null
            var currentLastUpdateId: Long? = null

            val matchedChatId = withTimeoutOrNull(totalTimeoutMs) {

                while (secondsLeft > 0) {
                    // 每 3 秒 (也就是 loopCount % 3 == 0) 才去查一次 API
                    if (secondsLeft % 3 == 0) {
                        val updateResponse = TelegramConfig.checkUpdatesResult(token, pin, currentLastUpdateId)
                        currentLastUpdateId = updateResponse.newLastUpdateId

                        when (val checkRes = updateResponse.result) {
                            is TelegramCheckResult.Success -> {
                                return@withTimeoutOrNull checkRes.chatId
                            }
                            is TelegramCheckResult.Error -> {
                                fatalErrorMsg = checkRes.message
                                return@withTimeoutOrNull null
                            }
                            is TelegramCheckResult.NotFound -> {
                                // 沒找到，繼續等
                            }
                        }
                    }

                    delay(1000L) // 還是只等 1 秒
                    secondsLeft -= 1

                    // 這樣 UI 畫面依舊能每秒平滑倒數
                    val currentState = _uiState.value
                    if (currentState is TelegramSetupUiState.Step2_Listening) {
                        _uiState.value = currentState.copy(remainingSeconds = secondsLeft.coerceAtLeast(0))
                    }
                }
                null
            }   

            stopForegroundService()

            if (matchedChatId != null) {
                val chatIdStr = matchedChatId.toString()
                // 自動儲存至 DataStore / SettingsManager
                settingsManager.telegramBotToken = token
                settingsManager.telegramChatId = chatIdStr

                _uiState.value = TelegramSetupUiState.Success(
                    token = token,
                    chatId = chatIdStr
                )

                // 更新本地通知為成功
                showNotification(
                    title = "✅ 綁定成功！",
                    content = "已成功擷取 Chat ID ($chatIdStr)，點擊返回 App 完成設定。",
                    isOngoing = false
                )

                // 發送測試歡迎訊息給 Telegram 機器人
                TelegramNotifier.testBotConnection(token, chatIdStr)
            } else if (fatalErrorMsg != null) {
                cancelNotification()
                _uiState.value = TelegramSetupUiState.Error(fatalErrorMsg!!)
            } else {
                val currentState = _uiState.value
                if (currentState is TelegramSetupUiState.Step2_Listening) {
                    _uiState.value = TelegramSetupUiState.Timeout

                    // 更新本地通知為逾時
                    showNotification(
                        title = "⏳ 配對逾時",
                        content = "未在 2 分鐘內收到配對碼，點擊返回 App 重新嘗試。",
                        isOngoing = false
                    )
                }
            }
        }
    }

    fun resetToStep1() {
        pollingJob?.cancel()
        pollingJob = null
        stopForegroundService()
        cancelNotification()
        _uiState.value = TelegramSetupUiState.Step1_InputToken
    }

    fun retryPairing(token: String) {
        resetToStep1()
        startPairing(token)
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        pollingJob = null
        stopForegroundService()
        cancelNotification()
    }
}
