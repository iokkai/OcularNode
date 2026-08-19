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
class QRCodeUtilsTest {

    @Test
    fun testParseLegacyJsonQrCode() {
        val json = """{"name":"Living Room","ip":"100.64.1.2","port":8080}"""
        val info = QRCodeUtils.parseScannedQrCode(json)

        assertNotNull(info)
        assertEquals("Living Room", info?.name)
        assertEquals("100.64.1.2", info?.ipAddress)
        assertEquals(8080, info?.port)
        assertNull(info?.deviceSecret)
        assertNull(info?.ipv6Address)
    }

    @Test
    fun testParseWebRtcJsonQrCodeWithIpv6() {
        val qrContent = QRCodeUtils.generateWebRtcQrContent(
            name = "Balcony Camera",
            ipAddress = "192.168.1.55",
            port = 8080,
            deviceId = "ocular-dev-1234",
            deviceSecret = "secret-hex-abcde",
            ipv6Address = "2001:b400:e234:5678::1"
        )

        val info = QRCodeUtils.parseScannedQrCode(qrContent)
        assertNotNull(info)
        assertEquals("Balcony Camera", info?.name)
        assertEquals("192.168.1.55", info?.ipAddress)
        assertEquals("2001:b400:e234:5678::1", info?.ipv6Address)
        assertEquals(8080, info?.port)
        assertEquals("ocular-dev-1234", info?.deviceId)
        assertEquals("secret-hex-abcde", info?.deviceSecret)
    }

    @Test
    fun testParseUrlFormatWithBracketedIpv6() {
        val url = "http://[2001:b400:e234:5678::1]:8080?name=Kitchen&secret=sec12345&deviceId=dev999"
        val info = QRCodeUtils.parseScannedQrCode(url)

        assertNotNull(info)
        assertEquals("Kitchen", info?.name)
        assertEquals("2001:b400:e234:5678::1", info?.ipAddress)
        assertEquals("2001:b400:e234:5678::1", info?.ipv6Address)
        assertEquals(8080, info?.port)
        assertEquals("sec12345", info?.deviceSecret)
        assertEquals("dev999", info?.deviceId)
    }

    @Test
    fun testParseUrlFormatWithSecret() {
        val url = "http://192.168.1.80:8080?name=Kitchen&secret=sec12345&deviceId=dev999&ipv6=2404:6800:4008:803::200e"
        val info = QRCodeUtils.parseScannedQrCode(url)

        assertNotNull(info)
        assertEquals("Kitchen", info?.name)
        assertEquals("192.168.1.80", info?.ipAddress)
        assertEquals("2404:6800:4008:803::200e", info?.ipv6Address)
        assertEquals(8080, info?.port)
        assertEquals("sec12345", info?.deviceSecret)
        assertEquals("dev999", info?.deviceId)
    }
}
