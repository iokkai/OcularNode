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

/**
 * Plan B: Anonymous MQTT Signaling Channel with End-to-End Encryption (AES-256-GCM)
 * and automatic multi-broker fallback (HiveMQ -> EMQX -> Mosquitto).
 */
class MqttSignalingChannel(
    private val brokerUrls: List<String> = DEFAULT_BROKERS
) : SignalingChannel {

    companion object {
        private const val TAG = "MqttSignalingChannel"

        val DEFAULT_BROKERS = listOf(
            "tcp://broker.hivemq.com:1883",
            "tcp://broker.emqx.io:1883",
            "tcp://test.mosquitto.org:1883"
        )
    }

    override val channelType: SignalingChannelType = SignalingChannelType.MQTT

    private var mqttClient: MqttClient? = null
    private var currentTopic: String? = null
    private var currentSecret: String? = null
    private var messageListener: ((SignalingPayload) -> Unit)? = null

    private fun getFullTopic(channelKey: String): String {
        return "ocularnode/v1/$channelKey/signal"
    }

    private suspend fun connectToBestBroker(): MqttClient = withContext(Dispatchers.IO) {
        var lastException: Exception? = null

        for (brokerUrl in brokerUrls) {
            try {
                val clientId = "OcularNode-" + UUID.randomUUID().toString().take(8)
                val client = MqttClient(brokerUrl, clientId, MemoryPersistence())

                val options = MqttConnectOptions().apply {
                    isCleanSession = true
                    connectionTimeout = 5
                    keepAliveInterval = 30
                    isAutomaticReconnect = true
                }

                client.connect(options)
                Log.i(TAG, "Successfully connected to MQTT broker: $brokerUrl")
                return@withContext client
            } catch (e: Exception) {
                Log.w(TAG, "Failed to connect to broker: $brokerUrl, trying next...", e)
                lastException = e
            }
        }

        throw IllegalStateException("All MQTT brokers failed to connect", lastException)
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
                Log.i(TAG, "Subscribed to MQTT topic: $topic")
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
            val client = mqttClient ?: connectToBestBroker().also { mqttClient = it }
            val topic = getFullTopic(channelKey)

            val rawJson = message.toJson()
            val encryptedPayload = AesGcmCipher.encrypt(rawJson, secret)

            val mqttMessage = MqttMessage(encryptedPayload.toByteArray(Charsets.UTF_8)).apply {
                qos = 1
                isRetained = false
            }

            client.publish(topic, mqttMessage)
            Log.d(TAG, "Published encrypted signaling message ${message.type} to topic $topic")
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
            Log.i(TAG, "MQTT signaling channel closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing MQTT channel", e)
        }
    }
}
