package io.github.iokkai.ocularnode.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

object TelegramNotifier {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private suspend fun <T> executeWithRetry(maxRetries: Int = 2, delayMs: Long = 1000L, block: () -> T): T {
        var lastException: Exception? = null
        for (attempt in 0..maxRetries) {
            try {
                return block()
            } catch (e: java.io.IOException) {
                lastException = e
                if (attempt < maxRetries) {
                    kotlinx.coroutines.delay(delayMs * (attempt + 1))
                }
            } catch (e: Exception) {
                throw e
            }
        }
        throw lastException ?: java.io.IOException("Max retries exceeded")
    }

    suspend fun sendMotionAlert(
        botToken: String,
        chatId: String,
        deviceName: String,
        motionPercentage: Float,
        photoBytes: ByteArray?,
        aiSummary: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        if (botToken.isBlank() || chatId.isBlank()) {
            return@withContext false
        }

        try {
            val aiLine = if (aiSummary.isNotBlank()) "🤖 *ML Kit AI:* $aiSummary\n" else ""
            val caption = "🚨 *【動態偵測】*\n" +
                    "📱 裝置: *$deviceName*\n" +
                    "⚠️ 動態差異: *${"%.1f".format(motionPercentage)}%*\n" +
                    aiLine +
                    "⏰ 時間: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}"

            executeWithRetry {
                if (photoBytes != null && photoBytes.isNotEmpty()) {
                    // Send photo with caption
                    val url = "https://api.telegram.org/bot$botToken/sendPhoto"
                    val requestBody = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("chat_id", chatId)
                        .addFormDataPart("caption", caption)
                        .addFormDataPart("parse_mode", "Markdown")
                        .addFormDataPart(
                            "photo",
                            "motion_alert.jpg",
                            photoBytes.toRequestBody("image/jpeg".toMediaType())
                        )
                        .build()

                    val request = Request.Builder().url(url).post(requestBody).build()
                    val response = client.newCall(request).execute()
                    val success = response.isSuccessful
                    response.close()
                    success
                } else {
                    // Send text message
                    val url = "https://api.telegram.org/bot$botToken/sendMessage"
                    val jsonBody = """
                        {
                            "chat_id": "$chatId",
                            "text": "$caption",
                            "parse_mode": "Markdown"
                        }
                    """.trimIndent()

                    val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
                    val request = Request.Builder().url(url).post(requestBody).build()
                    val response = client.newCall(request).execute()
                    val success = response.isSuccessful
                    response.close()
                    success
                }
            }
        } catch (e: Exception) {
            Log.e("TelegramNotifier", "Error sending Telegram notification", e)
            return@withContext false
        }
    }

    suspend fun sendVideoAlert(
        botToken: String,
        chatId: String,
        deviceName: String,
        motionPercentage: Float,
        videoFile: File,
        aiSummary: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        if (botToken.isBlank() || chatId.isBlank() || !videoFile.exists() || videoFile.length() == 0L) {
            return@withContext false
        }

        try {
            val aiLine = if (aiSummary.isNotBlank()) "🤖 *ML Kit AI:* $aiSummary\n" else ""
            val caption = "📹 *【動態錄影告警】*\n" +
                    "📱 裝置: *$deviceName*\n" +
                    "⚠️ 動態差異: *${"%.1f".format(motionPercentage)}%*\n" +
                    aiLine +
                    "⏰ 時間: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}"

            val url = "https://api.telegram.org/bot$botToken/sendVideo"
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId)
                .addFormDataPart("caption", caption)
                .addFormDataPart("parse_mode", "Markdown")
                .addFormDataPart(
                    "video",
                    videoFile.name,
                    videoFile.asRequestBody("video/mp4".toMediaType())
                )
                .build()

            executeWithRetry {
                val request = Request.Builder().url(url).post(requestBody).build()
                val response = client.newCall(request).execute()
                val success = response.isSuccessful
                response.close()
                success
            }
        } catch (e: Exception) {
            Log.e("TelegramNotifier", "Error sending Telegram video notification", e)
            return@withContext false
        }
    }

    suspend fun testBotConnection(botToken: String, chatId: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (botToken.isBlank() || chatId.isBlank()) {
            return@withContext Pair(false, "請輸入 Bot Token 與 Chat ID")
        }
        try {
            val url = "https://api.telegram.org/bot$botToken/sendMessage"
            val text = "✅ *OcularNode 測試成功！*\n連線設定正確，隨時準備接收動態監控警報通知。"
            val jsonBody = """
                {
                    "chat_id": "$chatId",
                    "text": "$text",
                    "parse_mode": "Markdown"
                }
            """.trimIndent()

            val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(requestBody).build()
            val response = client.newCall(request).execute()
            val isSuccess = response.isSuccessful
            val msg = if (isSuccess) "Telegram 推播測試成功！" else "Telegram API 失敗: HTTP ${response.code}"
            response.close()
            return@withContext Pair(isSuccess, msg)
        } catch (e: Exception) {
            return@withContext Pair(false, "網路連線失敗: ${e.localizedMessage}")
        }
    }

    suspend fun sendSystemAlert(
        botToken: String,
        chatId: String,
        deviceName: String,
        alertTitle: String,
        alertDetails: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (botToken.isBlank() || chatId.isBlank()) {
            return@withContext false
        }
        try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            val timeStr = sdf.format(java.util.Date())
            val messageText = "$alertTitle\n📱 裝置: *$deviceName*\n⚠️ 狀況: *$alertDetails*\n⏰ 時間: $timeStr"

            val url = "https://api.telegram.org/bot$botToken/sendMessage"
            val jsonObj = org.json.JSONObject().apply {
                put("chat_id", chatId)
                put("text", messageText)
                put("parse_mode", "Markdown")
            }

            val requestBody = jsonObj.toString().toRequestBody("application/json".toMediaType())
            executeWithRetry {
                val request = Request.Builder().url(url).post(requestBody).build()
                val response = client.newCall(request).execute()
                val success = response.isSuccessful
                response.close()
                success
            }
        } catch (e: Exception) {
            Log.e("TelegramNotifier", "Error sending Telegram system alert", e)
            return@withContext false
        }
    }
}
