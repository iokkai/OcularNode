package io.github.iokkai.ocularnode.webrtc.signaling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MqttSignalingChannelTest {

    @Test
    fun testDefaultBrokersContainTcpAndWss() {
        assertEquals(3, MqttSignalingChannel.DEFAULT_TCP_BROKERS.size)
        assertEquals(3, MqttSignalingChannel.DEFAULT_WSS_BROKERS.size)
        assertEquals(6, MqttSignalingChannel.DEFAULT_BROKERS.size)

        assertTrue(MqttSignalingChannel.DEFAULT_TCP_BROKERS.all { it.startsWith("tcp://") })
        assertTrue(MqttSignalingChannel.DEFAULT_WSS_BROKERS.all { it.startsWith("wss://") })

        assertTrue(MqttSignalingChannel.DEFAULT_TCP_BROKERS.any { it.contains("1883") })
        assertTrue(MqttSignalingChannel.DEFAULT_WSS_BROKERS.any { it.contains("8884") })
        assertTrue(MqttSignalingChannel.DEFAULT_WSS_BROKERS.any { it.contains("8084") })
    }

    @Test
    fun testChannelInitialization() {
        val channel = MqttSignalingChannel()
        assertEquals(SignalingChannelType.MQTT, channel.channelType)
        assertFalse(channel.isUsingWss)

        val wssChannel = MqttSignalingChannel(preferWss = true)
        assertEquals(SignalingChannelType.MQTT, wssChannel.channelType)
    }

    @Test
    fun testCustomBrokerList() {
        val custom = listOf("wss://custom.broker.com:443/mqtt")
        val channel = MqttSignalingChannel(customBrokers = custom)
        assertNotNull(channel)
    }
}
