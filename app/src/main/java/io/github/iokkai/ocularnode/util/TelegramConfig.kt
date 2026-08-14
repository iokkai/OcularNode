package io.github.iokkai.ocularnode.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class TelegramCheckResult {
    data class Success(val chatId: Long) : TelegramCheckResult()
    object NotFound : TelegramCheckResult()
    data class Error(val message: String) : TelegramCheckResult()
}

data class CheckUpdatesResponse(
    val result: TelegramCheckResult,
    val newLastUpdateId: Long?
)

data class BotInfo(
    val id: Long,
    val firstName: String,
    val username: String
)

/**
 * Telegram 配置與 Update 輪詢工具類別
 */
object TelegramConfig {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun getBotInfo(token: String): BotInfo? = withContext(Dispatchers.IO) {
        val trimmedToken = token.trim()
        if (trimmedToken.isBlank()) return@withContext null
        try {
            val url = "https://api.telegram.org/bot$trimmedToken/getMe"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""
            response.close()

            if (response.isSuccessful && bodyString.isNotBlank()) {
                val json = JSONObject(bodyString)
                if (json.optBoolean("ok", false)) {
                    val result = json.optJSONObject("result")
                    if (result != null) {
                        return@withContext BotInfo(
                            id = result.optLong("id"),
                            firstName = result.optString("first_name", ""),
                            username = result.optString("username", "")
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TelegramConfig", "Error fetching getMe from Telegram API", e)
        }
        return@withContext null
    }

    /**
     * 清除可能殘留的 Webhook，避免 getUpdates 發生 HTTP 409 Conflict
     */
    suspend fun clearWebhook(token: String): Boolean = withContext(Dispatchers.IO) {
        val trimmedToken = token.trim()
        if (trimmedToken.isBlank()) return@withContext false
        try {
            val url = "https://api.telegram.org/bot$trimmedToken/deleteWebhook"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val success = response.isSuccessful
            response.close()
            return@withContext success
        } catch (e: Exception) {
            Log.e("TelegramConfig", "Error clearing webhook", e)
            return@withContext false
        }
    }

    /**
     * 對 https://api.telegram.org/bot<TOKEN>/getUpdates 發送 GET 請求
     * 使用 offset 參數精準獲取最新訊息，解決舊訊息佇列堆積導致無法讀取新 PIN 碼的問題。
     *
     * @param token Telegram Bot Token
     * @param targetPin 4 位數驗證 PIN 碼
     * @param lastUpdateId 上一次輪詢到的最大 update_id
     * @return CheckUpdatesResponse 包含結果與更新後的 update_id
     */
    suspend fun checkUpdatesResult(
        token: String,
        targetPin: String,
        lastUpdateId: Long? = null
    ): CheckUpdatesResponse = withContext(Dispatchers.IO) {
        val trimmedToken = token.trim()
        val trimmedPin = targetPin.trim()

        if (trimmedToken.isBlank() || trimmedPin.isBlank()) {
            return@withContext CheckUpdatesResponse(TelegramCheckResult.NotFound, lastUpdateId)
        }

        try {
            // 若 lastUpdateId 為 null，使用 offset=-50 抓取 Telegram 佇列最新 50 筆訊息；
            // 若已知 lastUpdateId，使用 offset = lastUpdateId + 1 僅抓取全新發送的訊息
            val offsetParam = if (lastUpdateId != null) (lastUpdateId + 1).toString() else "-50"
            val url = "https://api.telegram.org/bot$trimmedToken/getUpdates?offset=$offsetParam&timeout=0"

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val code = response.code
            val bodyString = response.body?.string() ?: ""
            response.close()

            if (code == 401) {
                return@withContext CheckUpdatesResponse(
                    TelegramCheckResult.Error("Bot Token is invalid (HTTP 401 Unauthorized), please check if the token was copied correctly."),
                    lastUpdateId
                )
            }

            if (code == 409) {
                // 自動清除 Webhook
                clearWebhook(trimmedToken)
                return@withContext CheckUpdatesResponse(TelegramCheckResult.NotFound, lastUpdateId)
            }

            if (bodyString.isBlank()) {
                return@withContext CheckUpdatesResponse(TelegramCheckResult.NotFound, lastUpdateId)
            }

            val json = JSONObject(bodyString)
            if (json.optBoolean("ok", false)) {
                val resultArray = json.optJSONArray("result")
                    ?: return@withContext CheckUpdatesResponse(TelegramCheckResult.NotFound, lastUpdateId)

                var maxUpdateId = lastUpdateId

                // 遍歷所有 Update 項目
                for (i in 0 until resultArray.length()) {
                    val updateObj = resultArray.optJSONObject(i) ?: continue
                    val updateId = updateObj.optLong("update_id", -1L)
                    if (updateId > (maxUpdateId ?: -1L)) {
                        maxUpdateId = updateId
                    }

                    val messageObj = updateObj.optJSONObject("message")
                        ?: updateObj.optJSONObject("edited_message")
                        ?: updateObj.optJSONObject("channel_post")
                        ?: updateObj.optJSONObject("my_chat_member")
                        ?: continue

                    val rawText = messageObj.optString("text", "")
                        .ifBlank { messageObj.optString("caption", "") }
                        .trim()

                    // 清理斜線、@機器人名稱等文字格式，加速文字比對
                    val cleanText = rawText.replace("/", " ").replace("@", " ").trim()

                    // 比對訊息內容：支援純數字 1234, /1234, /start 1234, /start@bot 1234 或包含 1234 的文字
                    val isMatch = rawText.contains(trimmedPin, ignoreCase = true) ||
                            cleanText.contains(trimmedPin, ignoreCase = true)

                    if (isMatch) {
                        val chatObj = messageObj.optJSONObject("chat")
                        if (chatObj != null && chatObj.has("id")) {
                            val chatId = chatObj.optLong("id")
                            Log.i("TelegramConfig", "Found matching PIN $trimmedPin for Chat ID: $chatId (update_id: $updateId)")
                            return@withContext CheckUpdatesResponse(
                                TelegramCheckResult.Success(chatId),
                                maxUpdateId
                            )
                        }
                    }
                }
                return@withContext CheckUpdatesResponse(TelegramCheckResult.NotFound, maxUpdateId)
            } else {
                val desc = json.optString("description", "")
                if (desc.contains("webhook", ignoreCase = true)) {
                    clearWebhook(trimmedToken)
                }
            }
        } catch (e: Exception) {
            Log.e("TelegramConfig", "Error fetching getUpdates from Telegram API", e)
        }

        return@withContext CheckUpdatesResponse(TelegramCheckResult.NotFound, lastUpdateId)
    }

    suspend fun checkUpdates(token: String, targetPin: String): Long? {
        val res = checkUpdatesResult(token, targetPin)
        return when (val r = res.result) {
            is TelegramCheckResult.Success -> r.chatId
            else -> null
        }
    }
}
