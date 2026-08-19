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
     * Sends a generic signaling payload across this channel.
     * Returns true if successfully delivered to the channel.
     */
    suspend fun sendMessage(
        channelKey: String,
        secret: String,
        message: SignalingPayload
    ): Boolean

    /**
     * Sends an SDP Offer across the signaling channel.
     */
    suspend fun sendOffer(
        channelKey: String,
        secret: String,
        offer: SignalingPayload
    ): Boolean = sendMessage(channelKey, secret, offer)

    /**
     * Sends an SDP Answer across the signaling channel.
     */
    suspend fun sendAnswer(
        channelKey: String,
        secret: String,
        answer: SignalingPayload
    ): Boolean = sendMessage(channelKey, secret, answer)

    /**
     * Sends an ICE Candidate across the signaling channel.
     */
    suspend fun sendIceCandidate(
        channelKey: String,
        secret: String,
        candidate: SignalingPayload
    ): Boolean = sendMessage(channelKey, secret, candidate)

    /**
     * Closes the signaling channel and releases background network sockets.
     */
    fun close()
}
