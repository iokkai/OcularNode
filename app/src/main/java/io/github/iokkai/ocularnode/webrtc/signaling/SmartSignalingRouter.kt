package io.github.iokkai.ocularnode.webrtc.signaling

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext

/**
 * Smart Signaling Router coordinating Layer 1 (LAN IPv6/IPv4 Socket), Layer 2 (MQTT TCP 1883),
 * and Layer 3 (MQTT over WebSocket Port 443 / WSS).
 * Implements "Happy Eyeballs" parallel probing to prioritize IPv6 and achieve 0ms unnecessary waiting.
 */
class SmartSignalingRouter(
    private val channelKey: String,
    private val secret: String,
    private val cameraLocalIp: String? = null,
    private val cameraPort: Int = 8080,
    private val telegramBotToken: String? = null,
    private val telegramChatId: String? = null,
    private val cameraIpv6: String? = null
) {

    companion object {
        private const val TAG = "SmartSignalingRouter"
    }

    val mqttChannel: MqttSignalingChannel by lazy {
        MqttSignalingChannel()
    }

    val localChannelIpv6: LocalSocketSignalingChannel? by lazy {
        if (!cameraIpv6.isNullOrBlank()) {
            LocalSocketSignalingChannel(cameraIpv6, cameraPort)
        } else {
            null
        }
    }

    val localChannelIpv4: LocalSocketSignalingChannel? by lazy {
        if (!cameraLocalIp.isNullOrBlank()) {
            LocalSocketSignalingChannel(cameraLocalIp, cameraPort)
        } else {
            null
        }
    }

    val localChannel: LocalSocketSignalingChannel?
        get() = localChannelIpv6 ?: localChannelIpv4

    @Volatile
    private var activeChannel: SignalingChannel? = null

    /**
     * Starts listening on all configured channels (MQTT primary with TCP/WSS fallback, LAN secondary).
     */
    suspend fun startListening(
        scope: CoroutineScope,
        onMessage: (SignalingPayload, SignalingChannelType) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            // Start MQTT listening (automatically handles TCP -> WSS fallback)
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
    }

    /**
     * Sends a signaling payload using Happy Eyeballs parallel probing (IPv6 LAN -> IPv4 LAN -> MQTT).
     */
    suspend fun dispatchMessage(message: SignalingPayload): Boolean = withContext(Dispatchers.IO) {
        val current = activeChannel
        if (current != null) {
            val success = current.sendMessage(channelKey, secret, message)
            if (success) return@withContext true
            Log.w(TAG, "Active channel ${current.channelType} failed to deliver, falling back...")
        }

        val lanIpv6 = localChannelIpv6
        val lanIpv4 = localChannelIpv4
        val mqtt = mqttChannel

        // Happy Eyeballs: Probe LAN IPv6, LAN IPv4 (150ms timeout), and MQTT (TCP/WSS) in parallel
        val lanIpv6Deferred = async {
            if (lanIpv6 != null && lanIpv6.isReachable()) {
                val sent = lanIpv6.sendMessage(channelKey, secret, message)
                if (sent) lanIpv6 else null
            } else {
                null
            }
        }

        val lanIpv4Deferred = async {
            if (lanIpv4 != null && lanIpv4.isReachable()) {
                val sent = lanIpv4.sendMessage(channelKey, secret, message)
                if (sent) lanIpv4 else null
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
            lanIpv6Deferred.onAwait { it }
            lanIpv4Deferred.onAwait { it }
            mqttDeferred.onAwait { it }
        }

        if (winnerChannel != null) {
            activeChannel = winnerChannel
            val wssInfo = if (winnerChannel is MqttSignalingChannel && winnerChannel.isUsingWss) " (WSS Mode)" else ""
            Log.i(TAG, "Happy Eyeballs selected channel: ${winnerChannel.channelType}$wssInfo")
            return@withContext true
        }

        Log.e(TAG, "All signaling channels failed to deliver message: ${message.type}")
        false
    }

    fun close() {
        mqttChannel.close()
        localChannelIpv6?.close()
        localChannelIpv4?.close()
        activeChannel = null
        Log.i(TAG, "SmartSignalingRouter closed")
    }
}
