package io.github.iokkai.ocularnode.webrtc.signaling

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext

/**
 * Smart Signaling Router coordinating Plan A (Telegram), Plan B (MQTT), and Plan C (LAN Socket).
 * Implements "Happy Eyeballs" parallel probing to achieve 0ms unnecessary waiting.
 */
class SmartSignalingRouter(
    private val channelKey: String,
    private val secret: String,
    private val cameraLocalIp: String? = null,
    private val cameraPort: Int = 8080,
    private val telegramBotToken: String? = null,
    private val telegramChatId: String? = null
) {

    companion object {
        private const val TAG = "SmartSignalingRouter"
    }

    val mqttChannel: MqttSignalingChannel by lazy {
        MqttSignalingChannel()
    }

    val localChannel: LocalSocketSignalingChannel? by lazy {
        if (!cameraLocalIp.isNullOrBlank()) {
            LocalSocketSignalingChannel(cameraLocalIp, cameraPort)
        } else {
            null
        }
    }

    val telegramChannel: TelegramSignalingChannel? by lazy {
        if (!telegramBotToken.isNullOrBlank() && !telegramChatId.isNullOrBlank()) {
            TelegramSignalingChannel(telegramBotToken, telegramChatId)
        } else {
            null
        }
    }

    @Volatile
    private var activeChannel: SignalingChannel? = null

    /**
     * Starts listening on all configured channels (MQTT primary, LAN secondary).
     */
    suspend fun startListening(
        scope: CoroutineScope,
        onMessage: (SignalingPayload, SignalingChannelType) -> Unit
    ) = withContext(Dispatchers.IO) {
        // Start MQTT listening
        scope.launch {
            try {
                mqttChannel.startListening(channelKey, secret) { payload ->
                    onMessage(payload, SignalingChannelType.MQTT)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start MQTT listener: ${e.message}")
            }
        }
    }

    /**
     * Sends a signaling payload using Happy Eyeballs parallel probing or active channel.
     */
    suspend fun dispatchMessage(message: SignalingPayload): Boolean = withContext(Dispatchers.IO) {
        val current = activeChannel
        if (current != null) {
            val success = current.sendMessage(channelKey, secret, message)
            if (success) return@withContext true
            Log.w(TAG, "Active channel ${current.channelType} failed to deliver, falling back...")
        }

        val lan = localChannel
        val mqtt = mqttChannel
        val tg = telegramChannel

        // Happy Eyeballs: Probe LAN (150ms timeout) and MQTT in parallel
        val lanDeferred = async {
            if (lan != null && lan.isReachable()) {
                val sent = lan.sendMessage(channelKey, secret, message)
                if (sent) lan else null
            } else {
                null
            }
        }

        val mqttDeferred = async {
            val sent = mqtt.sendMessage(channelKey, secret, message)
            if (sent) mqtt else null
        }

        // Whichever succeeds first becomes the active channel
        val winnerChannel = select<SignalingChannel?> {
            lanDeferred.onAwait { it }
            mqttDeferred.onAwait { it }
        }

        if (winnerChannel != null) {
            activeChannel = winnerChannel
            Log.i(TAG, "Happy Eyeballs selected channel: ${winnerChannel.channelType}")
            return@withContext true
        }

        // If both LAN and MQTT failed, fallback to Telegram (Plan A)
        if (tg != null) {
            Log.i(TAG, "Falling back to Telegram signaling...")
            val tgSent = tg.sendMessage(channelKey, secret, message)
            if (tgSent) {
                activeChannel = tg
                return@withContext true
            }
        }

        Log.e(TAG, "All signaling channels failed to deliver message: ${message.type}")
        false
    }

    fun close() {
        mqttChannel.close()
        localChannel?.close()
        telegramChannel?.close()
        activeChannel = null
        Log.i(TAG, "SmartSignalingRouter closed")
    }
}
