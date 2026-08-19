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
    }

    @Test
    fun testParseWebRtcJsonQrCode() {
        val qrContent = QRCodeUtils.generateWebRtcQrContent(
            name = "Balcony Camera",
            ipAddress = "192.168.1.55",
            port = 8080,
            deviceId = "ocular-dev-1234",
            deviceSecret = "secret-hex-abcde"
        )

        val info = QRCodeUtils.parseScannedQrCode(qrContent)
        assertNotNull(info)
        assertEquals("Balcony Camera", info?.name)
        assertEquals("192.168.1.55", info?.ipAddress)
        assertEquals(8080, info?.port)
        assertEquals("ocular-dev-1234", info?.deviceId)
        assertEquals("secret-hex-abcde", info?.deviceSecret)
    }

    @Test
    fun testParseUrlFormatWithSecret() {
        val url = "http://192.168.1.80:8080?name=Kitchen&secret=sec12345&deviceId=dev999"
        val info = QRCodeUtils.parseScannedQrCode(url)

        assertNotNull(info)
        assertEquals("Kitchen", info?.name)
        assertEquals("192.168.1.80", info?.ipAddress)
        assertEquals(8080, info?.port)
        assertEquals("sec12345", info?.deviceSecret)
        assertEquals("dev999", info?.deviceId)
    }
}
