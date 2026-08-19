package io.github.iokkai.ocularnode.webrtc.signaling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SmartSignalingRouterTest {

    @Test
    fun testRouterInitializationWithIpv4AndIpv6() {
        val router = SmartSignalingRouter(
            channelKey = "test-key",
            secret = "test-secret",
            cameraLocalIp = "192.168.1.100",
            cameraPort = 8080,
            cameraIpv6 = "2001:b400:e123::1"
        )

        assertNotNull(router.mqttChannel)
        assertNotNull(router.localChannelIpv6)
        assertNotNull(router.localChannelIpv4)
        assertNotNull(router.localChannel)

        // localChannel prioritizes IPv6
        assertEquals(router.localChannelIpv6, router.localChannel)

        router.close()
    }

    @Test
    fun testRouterInitializationIpv4Only() {
        val router = SmartSignalingRouter(
            channelKey = "test-key",
            secret = "test-secret",
            cameraLocalIp = "192.168.1.100",
            cameraPort = 8080,
            cameraIpv6 = null
        )

        assertNotNull(router.mqttChannel)
        assertNull(router.localChannelIpv6)
        assertNotNull(router.localChannelIpv4)
        assertEquals(router.localChannelIpv4, router.localChannel)

        router.close()
    }

    @Test
    fun testRouterInitializationMqttOnly() {
        val router = SmartSignalingRouter(
            channelKey = "test-key",
            secret = "test-secret",
            cameraLocalIp = null,
            cameraPort = 8080,
            cameraIpv6 = null
        )

        assertNotNull(router.mqttChannel)
        assertNull(router.localChannelIpv6)
        assertNull(router.localChannelIpv4)
        assertNull(router.localChannel)

        router.close()
    }
}
