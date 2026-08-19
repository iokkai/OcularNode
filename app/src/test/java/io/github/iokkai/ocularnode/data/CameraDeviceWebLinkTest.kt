package io.github.iokkai.ocularnode.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

class CameraDeviceWebLinkTest {

    @Test
    fun testGetWebViewerUrl_containsAllRequiredFieldsInHash() {
        val camera = CameraDevice(
            id = 1L,
            name = "客廳 鏡頭 (Living Room)",
            ipAddress = "192.168.1.105",
            port = 8080,
            deviceId = "cam-uuid-12345",
            deviceSecret = "secret-key-abcdef",
            ipv6Address = "2001:b400:e123:4567::1"
        )

        val url = camera.getWebViewerUrl()

        // 1. Must use Hash fragment (#) to protect credentials from being sent in HTTP headers
        assertTrue("URL must contain '#' hash fragment", url.contains("#"))
        assertTrue("Base URL must be valid", url.startsWith("https://iokkai.github.io/OcularNode/viewer/"))

        val hashPart = url.substringAfter("#")
        val params = hashPart.split("&").associate {
            val parts = it.split("=", limit = 2)
            parts[0] to (if (parts.size > 1) URLDecoder.decode(parts[1], "UTF-8") else "")
        }

        assertEquals("cam-uuid-12345", params["id"])
        assertEquals("secret-key-abcdef", params["secret"])
        assertEquals("客廳 鏡頭 (Living Room)", params["name"])
        assertEquals("192.168.1.105", params["ip"])
        assertEquals("2001:b400:e123:4567::1", params["ipv6"])
        assertEquals("8080", params["port"])
    }

    @Test
    fun testGetWebViewerUrl_handlesNullIpv6AndSecretGracefully() {
        val camera = CameraDevice(
            id = 2L,
            name = "Kitchen",
            ipAddress = "192.168.1.200",
            port = 8081,
            deviceId = "cam-999",
            deviceSecret = null,
            ipv6Address = null
        )

        val url = camera.getWebViewerUrl()

        assertTrue(url.contains("#"))
        val hashPart = url.substringAfter("#")

        assertTrue(hashPart.contains("id=cam-999"))
        assertTrue(hashPart.contains("ip=192.168.1.200"))
        assertTrue(hashPart.contains("port=8081"))
        assertFalse(hashPart.contains("secret="))
        assertFalse(hashPart.contains("ipv6="))
    }

    @Test
    fun testGetWebViewerUrl_customBaseUrl() {
        val camera = CameraDevice(
            id = 3L,
            name = "Balcony",
            ipAddress = "192.168.1.150",
            port = 8080,
            deviceId = "cam-balcony"
        )

        val customBase = "http://localhost:3000/viewer/"
        val url = camera.getWebViewerUrl(baseUrl = customBase)

        assertTrue(url.startsWith(customBase))
        assertTrue(url.contains("#id=cam-balcony"))
    }
}
