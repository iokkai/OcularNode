package io.github.iokkai.ocularnode.webrtc.signaling

import android.util.Log
import io.github.iokkai.ocularnode.webrtc.crypto.AesGcmCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID
import javax.net.ssl.SSLSocketFactory

/**
 * Plan B & B': Anonymous MQTT Signaling Channel with End-to-End Encryption (AES-256-GCM),
 * multi-broker failover (HiveMQ -> EMQX -> Mosquitto), and automatic Layer 3 fallback
 * to MQTT over WebSocket (WSS / Port 443, 8884, 8084) when TCP Port 1883 is blocked.
 */
class MqttSignalingChannel(
    private val customBrokers: List<String>? = null,
    private val preferWss: Boolean = false
) : SignalingChannel {

    companion object {
        private const val TAG = "MqttSignalingChannel"

        // Tier 2: Standard TCP Brokers (Fastest & Ultra-lightweight)
        val DEFAULT_TCP_BROKERS = listOf(
            "tcp://broker.hivemq.com:1883",
            "tcp://broker.emqx.io:1883",
            "tcp://test.mosquitto.org:1883"
        )

        // Tier 3: WSS / WebSocket Brokers (Firewall Traversal / Port 443, 8884, 8084)
        val DEFAULT_WSS_BROKERS = listOf(
            "wss://broker.hivemq.com:8884/mqtt",
            "wss://broker.emqx.io:8084/mqtt",
            "wss://test.mosquitto.org:8081/mqtt"
        )

        val DEFAULT_BROKERS = DEFAULT_TCP_BROKERS + DEFAULT_WSS_BROKERS
    }

    override val channelType: SignalingChannelType = SignalingChannelType.MQTT

    private var mqttClient: MqttClient? = null
    private var currentTopic: String? = null
    private var currentSecret: String? = null
    private var messageListener: ((SignalingPayload) -> Unit)? = null

    /**
     * Currently active broker URL after successful connection.
     */
    var activeBrokerUrl: String? = null
        private set

    /**
     * Indicates whether the active connection is using WSS (WebSocket over SSL).
     */
    val isUsingWss: Boolean
        get() = activeBrokerUrl?.startsWith("wss://") == true || activeBrokerUrl?.startsWith("ssl://") == true

    private fun getFullTopic(channelKey: String): String {
        return "ocularnode/v1/$channelKey/signal"
    }

    /**
     * Connects to the best available MQTT broker.
     * Prioritizes TCP 1883 brokers first; if all TCP brokers fail or time out (e.g. firewall blocked),
     * automatically falls back to WSS (Port 443 / 8884 / 8084) brokers.
     */
    suspend fun connectToBestBroker(): MqttClient = withContext(Dispatchers.IO) {
        val brokersToTry = when {
            customBrokers != null -> customBrokers
            preferWss -> DEFAULT_WSS_BROKERS + DEFAULT_TCP_BROKERS
            else -> DEFAULT_TCP_BROKERS + DEFAULT_WSS_BROKERS
        }

        var lastException: Exception? = null

        for (brokerUrl in brokersToTry) {
            try {
                Log.d(TAG, "Attempting connection to MQTT broker: $brokerUrl")
                val clientId = "OcularNode-" + UUID.randomUUID().toString().take(8)
                val client = MqttClient(brokerUrl, clientId, MemoryPersistence())

                val options = MqttConnectOptions().apply {
                    isCleanSession = true
                    connectionTimeout = 4 // 4s timeout per broker for fast failover
                    keepAliveInterval = 30
                    isAutomaticReconnect = true

                    if (brokerUrl.startsWith("ssl://") || brokerUrl.startsWith("wss://")) {
                        try {
                            socketFactory = SSLSocketFactory.getDefault()
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to set SSLSocketFactory, using default", e)
                        }
                    }
                }

                client.connect(options)
                activeBrokerUrl = brokerUrl
                Log.i(TAG, "Successfully connected to MQTT broker: $brokerUrl (isWSS: $isUsingWss)")
                io.github.iokkai.ocularnode.util.AppLogger.d("MQTT", "信令伺服器連線成功 ($brokerUrl)")
                return@withContext client
            } catch (e: Exception) {
                Log.w(TAG, "Failed to connect to broker: $brokerUrl (${e.message}), trying next...")
                lastException = e
            }
        }

        throw IllegalStateException("All MQTT brokers (TCP & WSS) failed to connect", lastException)
    }

    override suspend fun startListening(
        channelKey: String,
        secret: String,
        onMessage: (SignalingPayload) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                close() // release any previous client

                val client = connectToBestBroker()
                val topic = getFullTopic(channelKey)
                currentTopic = topic
                currentSecret = secret
                messageListener = onMessage

                client.setCallback(object : MqttCallback {
                    override fun connectionLost(cause: Throwable?) {
                        Log.w(TAG, "MQTT connection lost: ${cause?.message}")
                        io.github.iokkai.ocularnode.util.AppLogger.w("MQTT", "信令伺服器連線中斷 (${cause?.message ?: "Unknown"})")
                    }

                    override fun messageArrived(incomingTopic: String?, message: MqttMessage?) {
                        if (incomingTopic == topic && message != null) {
                            try {
                                val encryptedPayload = String(message.payload, Charsets.UTF_8)
                                val decryptedJson = AesGcmCipher.decrypt(encryptedPayload, secret)
                                val payload = SignalingPayload.fromJson(decryptedJson)
                                Log.d(TAG, "Decrypted signaling message: ${payload.type} from ${payload.senderId} (session: ${payload.sessionId})")
                                messageListener?.invoke(payload)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error decrypting/parsing incoming MQTT message", e)
                            }
                        }
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                })

                client.subscribe(topic, 1) // QoS 1 for reliable delivery
                mqttClient = client
                Log.i(TAG, "Subscribed to MQTT topic: $topic via $activeBrokerUrl")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start MQTT listening", e)
                throw e
            }
        }
    }

    override suspend fun sendMessage(
        channelKey: String,
        secret: String,
        message: SignalingPayload
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val client = if (mqttClient != null && mqttClient!!.isConnected) {
                mqttClient!!
            } else {
                connectToBestBroker().also { mqttClient = it }
            }

            val topic = getFullTopic(channelKey)
            val rawJson = message.toJson()
            val encryptedPayload = AesGcmCipher.encrypt(rawJson, secret)

            val mqttMessage = MqttMessage(encryptedPayload.toByteArray(Charsets.UTF_8)).apply {
                qos = 1
                isRetained = false
            }

            client.publish(topic, mqttMessage)
            Log.d(TAG, "Published encrypted signaling message ${message.type} to topic $topic via $activeBrokerUrl")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending MQTT message: ${e.message}", e)
            false
        }
    }

    override fun close() {
        try {
            mqttClient?.let {
                if (it.isConnected) {
                    it.disconnectForcibly(1000)
                }
                it.close()
            }
            mqttClient = null
            activeBrokerUrl = null
            Log.i(TAG, "MQTT signaling channel closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing MQTT channel", e)
        }
    }
}
