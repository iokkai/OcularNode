package io.github.iokkai.ocularnode.webrtc.signaling

import android.util.Log
import io.github.iokkai.ocularnode.webrtc.crypto.AesGcmCipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Plan A: Telegram Bot Fallback Signaling Channel.
 * Serves as an unblockable, ultra-reliable fallback channel when public MQTT is inaccessible.
 * Supports active Long Polling on the camera side to receive signaling offers/requests.
 */
class TelegramSignalingChannel(
    private val botToken: String,
    private val chatId: String
) : SignalingChannel {

    companion object {
        private const val TAG = "TelegramSignaling"
        const val TELEGRAM_SIGNAL_PREFIX = "[OCULAR_RTC_SIG]:"
    }

    override val channelType: SignalingChannelType = SignalingChannelType.TELEGRAM

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS) // Long polling timeout
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private var pollingJob: Job? = null
    private var lastUpdateId: Long = 0L

    override suspend fun startListening(
        channelKey: String,
        secret: String,
        onMessage: (SignalingPayload) -> Unit
    ) {
        if (botToken.isBlank()) {
            Log.w(TAG, "Cannot start Telegram listening: bot token is blank")
            return
        }

        close() // cancel any existing polling job

        pollingJob = CoroutineScope(Dispatchers.IO).launch {
            Log.i(TAG, "Starting Telegram Bot Long Polling for signaling on chat $chatId")
            while (isActive) {
                try {
                    val url = "https://api.telegram.org/bot$botToken/getUpdates?offset=${lastUpdateId + 1}&timeout=20"
                    val request = Request.Builder().url(url).build()

                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val bodyStr = response.body?.string()
                            if (!bodyStr.isNullOrBlank()) {
                                val rootJson = JSONObject(bodyStr)
                                if (rootJson.optBoolean("ok", false)) {
                                    val results = rootJson.optJSONArray("result")
                                    if (results != null) {
                                        for (i in 0 until results.length()) {
                                            val updateObj = results.getJSONObject(i)
                                            val updateId = updateObj.getLong("update_id")
                                            if (updateId > lastUpdateId) {
                                                lastUpdateId = updateId
                                            }

                                            val messageObj = updateObj.optJSONObject("message")
                                            val text = messageObj?.optString("text", "") ?: ""
                                            val senderChatId = messageObj?.optJSONObject("chat")?.optLong("id")?.toString() ?: ""

                                            // Only accept messages from authorized chatId (if configured)
                                            if (chatId.isBlank() || senderChatId == chatId) {
                                                if (text.startsWith(TELEGRAM_SIGNAL_PREFIX)) {
                                                    val payload = parseIncomingTelegramText(text, secret)
                                                    if (payload != null) {
                                                        Log.d(TAG, "Received Telegram signal: ${payload.type} for session ${payload.sessionId}")
                                                        onMessage(payload)
                                                    }
                                                } else if (text.trim().equals("/stream_request", ignoreCase = true)) {
                                                    val reqPayload = SignalingPayload.createRequestStream(
                                                        senderId = "tg-$senderChatId",
                                                        sessionId = "tg-sess-$updateId"
                                                    )
                                                    Log.i(TAG, "Received Telegram /stream_request command")
                                                    onMessage(reqPayload)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        Log.w(TAG, "Error in Telegram polling loop: ${e.message}")
                        delay(3000) // Backoff delay before retry
                    }
                }
            }
        }
    }

    override suspend fun sendMessage(
        channelKey: String,
        secret: String,
        message: SignalingPayload
    ): Boolean = withContext(Dispatchers.IO) {
        if (botToken.isBlank() || chatId.isBlank()) {
            Log.w(TAG, "Cannot send Telegram signal: Bot token or chat ID is blank")
            return@withContext false
        }

        try {
            val rawJson = message.toJson()
            val encrypted = AesGcmCipher.encrypt(rawJson, secret)
            val textToSend = "$TELEGRAM_SIGNAL_PREFIX$encrypted"

            val url = "https://api.telegram.org/bot$botToken/sendMessage"
            val jsonBody = JSONObject().apply {
                put("chat_id", chatId)
                put("text", textToSend)
                put("disable_notification", true)
            }

            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val success = response.isSuccessful
                Log.d(TAG, "Telegram signal message sent (status: ${response.code})")
                success
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending Telegram signal: ${e.message}", e)
            false
        }
    }

    /**
     * Parses an incoming Telegram message text to extract and decrypt the signaling payload.
     */
    fun parseIncomingTelegramText(text: String, secret: String): SignalingPayload? {
        if (!text.startsWith(TELEGRAM_SIGNAL_PREFIX)) return null
        return try {
            val encrypted = text.removePrefix(TELEGRAM_SIGNAL_PREFIX).trim()
            val decryptedJson = AesGcmCipher.decrypt(encrypted, secret)
            SignalingPayload.fromJson(decryptedJson)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing incoming Telegram signal", e)
            null
        }
    }

    override fun close() {
        pollingJob?.cancel()
        pollingJob = null
        Log.i(TAG, "TelegramSignalingChannel closed")
    }
}
