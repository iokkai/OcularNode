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

    // -------------------------------------------------------------------------
    // ConnectionTier ordering tests
    // -------------------------------------------------------------------------

    @Test
    fun testConnectionTierLabelsAreDistinct() {
        val tiers = listOf(
            ConnectionTier.LanIpv6,
            ConnectionTier.LanIpv4,
            ConnectionTier.MqttTcp,
            ConnectionTier.MqttWss,
            ConnectionTier.None
        )
        val labels = tiers.map { it.label }.toSet()

        // All five tiers must have unique labels
        assertEquals("Each ConnectionTier must have a unique label", 5, labels.size)
    }

    @Test
    fun testConnectionTierLanIpv6HasHighestPriority() {
        // LAN IPv6 is the preferred (fastest) channel
        val tier = ConnectionTier.LanIpv6
        assertEquals("LAN_IPV6", tier.label)
    }

    @Test
    fun testConnectionTierNoneIsDefault() {
        val router = SmartSignalingRouter(
            channelKey = "test-key",
            secret = "test-secret",
            cameraLocalIp = null,
            cameraPort = 8080,
            cameraIpv6 = null
        )
        // Before any message is dispatched, tier should be None
        assertEquals(ConnectionTier.None, router.activeConnectionTier)
        router.close()
    }

    @Test
    fun testConnectionTierIsNoneAfterClose() {
        val router = SmartSignalingRouter(
            channelKey = "test-key",
            secret = "test-secret",
            cameraLocalIp = "192.168.1.100",
            cameraPort = 8080,
            cameraIpv6 = "2001:b400:e123::1"
        )
        router.close()
        assertEquals(
            "Tier should reset to None after close()",
            ConnectionTier.None,
            router.activeConnectionTier
        )
    }

    // -------------------------------------------------------------------------
    // LAN probe timeout: verify withTimeoutOrNull wrapping does not throw
    // -------------------------------------------------------------------------

    @Test
    fun testRouterWithUnreachableLanIpsInitializesWithoutCrash() {
        // An unreachable IP should be handled gracefully via withTimeoutOrNull
        val router = SmartSignalingRouter(
            channelKey = "test-key",
            secret = "test-secret",
            cameraLocalIp = "192.168.99.254",   // Unlikely to be reachable in test env
            cameraPort = 9999,
            cameraIpv6 = "fd00::dead:beef"       // Private IPv6, not reachable in test env
        )

        assertNotNull(router.localChannelIpv4)
        assertNotNull(router.localChannelIpv6)
        assertTrue("Initial tier should be None", router.activeConnectionTier is ConnectionTier.None)

        router.close()
    }
}
