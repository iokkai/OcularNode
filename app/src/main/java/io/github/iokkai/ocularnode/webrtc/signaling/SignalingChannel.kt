package io.github.iokkai.ocularnode.webrtc.signaling

enum class SignalingChannelType {
    LOCAL_SOCKET, // Plan C (LAN direct 150ms probe)
    MQTT,         // Plan B (WAN anonymous MQTT with multi-broker fallback)
    TELEGRAM      // Plan A (WAN Telegram Bot fallback)
}

/**
 * Unified signaling channel interface. Implementations (MQTT, Local Socket, Telegram)
 * conform to this contract to allow transparent routing and hot fallback.
 */
interface SignalingChannel {

    val channelType: SignalingChannelType

    /**
     * Starts listening for signaling messages on a specific topic or channel ID.
     */
    suspend fun startListening(
        channelKey: String,
        secret: String,
        onMessage: (SignalingPayload) -> Unit
    )

    /**
     * Sends a signaling payload across this channel.
     * Returns true if successfully delivered to the channel.
     */
    suspend fun sendMessage(
        channelKey: String,
        secret: String,
        message: SignalingPayload
    ): Boolean

    /**
     * Closes the signaling channel and releases background network sockets.
     */
    fun close()
}
