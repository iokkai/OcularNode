package io.github.iokkai.ocularnode.webrtc.stun

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StunPoolManagerTest {

    @Test
    fun testDefaultEndpointsConfiguration() {
        val manager = StunPoolManager()
        val defaultEndpoints = StunPoolManager.DEFAULT_STUN_ENDPOINTS

        assertTrue(defaultEndpoints.size >= 4)
        assertTrue(defaultEndpoints.any { it.host.contains("google.com") })
        assertTrue(defaultEndpoints.any { it.host.contains("cloudflare.com") })
        assertTrue(defaultEndpoints.any { it.port == 443 }) // Nextcloud Port 443 STUN

        val iceServers = manager.getRankedIceServers()
        assertEquals(defaultEndpoints.size, iceServers.size)
    }

    @Test
    fun testProbeAndRankLogic() = runBlocking {
        val manager = StunPoolManager()

        val mockEndpoints = listOf(
            StunEndpoint("stun:test1.example.com:3478", "test1.example.com", 3478),
            StunEndpoint("stun:test2.example.com:3478", "test2.example.com", 3478)
        )

        // Running with short timeout in unit test environment
        val rankedServers = manager.probeAndRank(mockEndpoints, timeoutMs = 200)
        assertNotNull(rankedServers)
        assertEquals(2, rankedServers.size)
    }
}
