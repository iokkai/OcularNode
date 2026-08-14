package io.github.iokkai.ocularnode.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkUtilsTest {

    @Test
    fun isTailscaleCgnatIp_withValidTailscaleAddresses_returnsTrue() {
        // Lower boundary of 100.64.0.0/10
        assertTrue(NetworkUtils.isTailscaleCgnatIp("100.64.0.0"))
        assertTrue(NetworkUtils.isTailscaleCgnatIp("100.64.0.1"))

        // Typical Tailscale IPs
        assertTrue(NetworkUtils.isTailscaleCgnatIp("100.100.120.130"))
        assertTrue(NetworkUtils.isTailscaleCgnatIp("100.80.50.25"))

        // Upper boundary of 100.64.0.0/10
        assertTrue(NetworkUtils.isTailscaleCgnatIp("100.127.255.254"))
        assertTrue(NetworkUtils.isTailscaleCgnatIp("100.127.255.255"))
    }

    @Test
    fun isTailscaleCgnatIp_withOutOfRangeOrInvalidAddresses_returnsFalse() {
        // Just below lower boundary (100.63.x.x)
        assertFalse(NetworkUtils.isTailscaleCgnatIp("100.63.255.255"))
        assertFalse(NetworkUtils.isTailscaleCgnatIp("100.0.0.1"))

        // Just above upper boundary (100.128.x.x)
        assertFalse(NetworkUtils.isTailscaleCgnatIp("100.128.0.0"))
        assertFalse(NetworkUtils.isTailscaleCgnatIp("100.200.1.1"))

        // Standard Private IPs
        assertFalse(NetworkUtils.isTailscaleCgnatIp("192.168.1.100"))
        assertFalse(NetworkUtils.isTailscaleCgnatIp("10.0.0.1"))
        assertFalse(NetworkUtils.isTailscaleCgnatIp("172.16.0.1"))
        assertFalse(NetworkUtils.isTailscaleCgnatIp("127.0.0.1"))

        // Malformed IP strings
        assertFalse(NetworkUtils.isTailscaleCgnatIp("100.64.1"))
        assertFalse(NetworkUtils.isTailscaleCgnatIp("100.64.1.1.1"))
        assertFalse(NetworkUtils.isTailscaleCgnatIp("100.notanumber.1.1"))
        assertFalse(NetworkUtils.isTailscaleCgnatIp("not_an_ip"))
        assertFalse(NetworkUtils.isTailscaleCgnatIp(""))
    }
}
