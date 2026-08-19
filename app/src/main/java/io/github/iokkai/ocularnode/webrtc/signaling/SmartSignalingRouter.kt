package io.github.iokkai.ocularnode.webrtc.signaling

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Represents the signaling/connection tier that is currently active.
 * Ordered from fastest (LAN) to slowest (relay) — used for logging and future UI display.
 */
sealed class ConnectionTier(val label: String) {
    /** Direct LAN socket over IPv6 link-local / global address */
    object LanIpv6 : ConnectionTier("LAN_IPV6")

    /** Direct LAN socket over IPv4 (192.168.x.x) */
    object LanIpv4 : ConnectionTier("LAN_IPV4")

    /** MQTT over TCP 1883 — crosses NAT via STUN/ICE */
    object MqttTcp : ConnectionTier("MQTT_TCP")

    /** MQTT over WebSocket / TLS 443 — firewall-friendly fallback */
    object MqttWss : ConnectionTier("MQTT_WSS")

    /** No channel is currently active */
    object None : ConnectionTier("NONE")
}

/**
 * Smart Signaling Router coordinating Layer 1 (LAN IPv6/IPv4 Socket), Layer 2 (MQTT TCP 1883),
 * and Layer 3 (MQTT over WebSocket Port 443 / WSS).
 *
 * Connection priority (Happy Eyeballs — RFC 8305 inspired):
 *   LAN IPv6 ──┐
 *   LAN IPv4 ──┤ (parallel, 150ms window)
 *   MQTT TCP ──┤
 *   MQTT WSS ──┘
 *
 * The first channel to respond wins and is cached as [activeChannel].
 * If the cached channel fails for [STALE_TIMEOUT_MS] milliseconds consecutively,
 * a full Happy Eyeballs re-race is triggered.
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

        /**
         * Maximum time (ms) to wait for LAN channels before falling back to MQTT.
         * Keeps the Happy Eyeballs race from blocking indefinitely on unreachable LAN IPs.
         */
        private const val LAN_PROBE_TIMEOUT_MS = 150L

        /**
         * After this duration of consecutive failures on the cached [activeChannel],
         * force a full re-race to re-select the best available channel.
         */
        private const val STALE_TIMEOUT_MS = 5_000L
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

    /** Timestamp of the last successful message delivery via [activeChannel]. */
    @Volatile
    private var activeChannelLastSuccessMs: Long = 0L

    /** The tier label of the currently cached active channel (for logging / future UI). */
    @Volatile
    var activeConnectionTier: ConnectionTier = ConnectionTier.None
        private set

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
     * Sends a signaling payload using Happy Eyeballs parallel probing.
     *
     * Strategy:
     * 1. If a non-stale [activeChannel] exists, try it first (fast path).
     * 2. If that fails or the channel is stale, run a full parallel race:
     *    - LAN IPv6 and LAN IPv4 are raced with a [LAN_PROBE_TIMEOUT_MS] ceiling.
     *    - MQTT (TCP then WSS) races concurrently without a timeout ceiling.
     * 3. The first winner is cached as the new [activeChannel].
     */
    suspend fun dispatchMessage(message: SignalingPayload): Boolean = withContext(Dispatchers.IO) {
        val current = activeChannel
        val isStale = (System.currentTimeMillis() - activeChannelLastSuccessMs) > STALE_TIMEOUT_MS

        if (current != null && !isStale) {
            val success = current.sendMessage(channelKey, secret, message)
            if (success) {
                activeChannelLastSuccessMs = System.currentTimeMillis()
                return@withContext true
            }
            Log.w(TAG, "Active channel ${activeConnectionTier.label} failed — re-racing all channels")
            invalidateActiveChannel()
        } else if (isStale && current != null) {
            Log.d(TAG, "Channel ${activeConnectionTier.label} is stale (>${STALE_TIMEOUT_MS}ms) — re-racing")
            invalidateActiveChannel()
        }

        runHappyEyeballs(message)
    }

    /**
     * Races all channels in parallel.
     * LAN channels are wrapped in [withTimeoutOrNull] with [LAN_PROBE_TIMEOUT_MS] to prevent
     * blocking the [select] indefinitely if the camera is not on the local network.
     */
    private suspend fun runHappyEyeballs(message: SignalingPayload): Boolean {
        val lanIpv6 = localChannelIpv6
        val lanIpv4 = localChannelIpv4
        val mqtt = mqttChannel

        val lanIpv6Deferred = async {
            withTimeoutOrNull(LAN_PROBE_TIMEOUT_MS) {
                if (lanIpv6 != null && lanIpv6.isReachable()) {
                    if (lanIpv6.sendMessage(channelKey, secret, message)) lanIpv6 else null
                } else null
            }
        }

        val lanIpv4Deferred = async {
            withTimeoutOrNull(LAN_PROBE_TIMEOUT_MS) {
                if (lanIpv4 != null && lanIpv4.isReachable()) {
                    if (lanIpv4.sendMessage(channelKey, secret, message)) lanIpv4 else null
                } else null
            }
        }

        // MQTT has no timeout ceiling — it is the guaranteed fallback
        val mqttDeferred = async {
            if (mqtt.sendMessage(channelKey, secret, message)) mqtt else null
        }

        // Whichever succeeds first (including null from timed-out LAN) wins
        val winnerChannel = select<SignalingChannel?> {
            lanIpv6Deferred.onAwait { it }
            lanIpv4Deferred.onAwait { it }
            mqttDeferred.onAwait { it }
        }

        if (winnerChannel != null) {
            activeChannel = winnerChannel
            activeChannelLastSuccessMs = System.currentTimeMillis()
            activeConnectionTier = resolveChannelTier(winnerChannel)
            val wssInfo = if (winnerChannel is MqttSignalingChannel && winnerChannel.isUsingWss) " (WSS Mode)" else ""
            Log.i(TAG, "Happy Eyeballs selected channel: ${activeConnectionTier.label}$wssInfo")
            return true
        }

        Log.e(TAG, "All signaling channels failed to deliver message: ${message.type}")
        return false
    }

    private fun resolveChannelTier(channel: SignalingChannel): ConnectionTier = when {
        channel === localChannelIpv6 -> ConnectionTier.LanIpv6
        channel === localChannelIpv4 -> ConnectionTier.LanIpv4
        channel is MqttSignalingChannel && channel.isUsingWss -> ConnectionTier.MqttWss
        channel is MqttSignalingChannel -> ConnectionTier.MqttTcp
        else -> ConnectionTier.None
    }

    private fun invalidateActiveChannel() {
        activeChannel = null
        activeConnectionTier = ConnectionTier.None
        activeChannelLastSuccessMs = 0L
    }

    fun close() {
        mqttChannel.close()
        localChannelIpv6?.close()
        localChannelIpv4?.close()
        invalidateActiveChannel()
        Log.i(TAG, "SmartSignalingRouter closed")
    }
}
