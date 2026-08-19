package io.github.iokkai.ocularnode.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NetworkUtilsIpv6Test {

    @Test
    fun testIpInfoDataClassWithIpv6() {
        val ipInfo = IpInfo(
            localIp = "192.168.1.100",
            allIps = listOf("192.168.1.100", "2001:b400:e123:4567::1"),
            ipv6GlobalAddress = "2001:b400:e123:4567::1"
        )

        assertEquals("2001:b400:e123:4567::1", ipInfo.ipv6GlobalAddress)
        assertEquals("192.168.1.100", ipInfo.localIp)
    }

    @Test
    fun testIpInfoDefaultIpv6IsNull() {
        val ipInfo = IpInfo(
            localIp = "192.168.1.50",
            allIps = listOf("192.168.1.50")
        )

        assertNull(ipInfo.ipv6GlobalAddress)
        assertEquals("192.168.1.50", ipInfo.localIp)
    }

    @Test
    fun testGetIpAddressesReturnsIpInfo() {
        val ipInfo = NetworkUtils.getIpAddresses()
        assertNotNull(ipInfo)
        assertNotNull(ipInfo.allIps)
    }
}
