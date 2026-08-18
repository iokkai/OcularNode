package io.github.iokkai.ocularnode.webrtc.signaling

import android.util.Log
import io.github.iokkai.ocularnode.webrtc.crypto.AesGcmCipher
import kotlinx.coroutines.Dispatchers
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
 */
class TelegramSignalingChannel(
    private val botToken: String,
    private val chatId: String
) : SignalingChannel {

    companion object {
        private const val TAG = "TelegramSignaling"
        private const val TELEGRAM_SIGNAL_PREFIX = "[OCULAR_RTC_SIG]:"
    }

    override val channelType: SignalingChannelType = SignalingChannelType.TELEGRAM

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun startListening(
        channelKey: String,
        secret: String,
        onMessage: (SignalingPayload) -> Unit
    ) {
        // Listening in Telegram is done via TelegramNotifier/TelegramPairingService polling loop
        Log.d(TAG, "Telegram signaling listener attached for chat $chatId")
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
        // OkHttpClient resources
    }
}
