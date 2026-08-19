package io.github.iokkai.ocularnode.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraDeviceMigrationTest {

    @Test
    fun testCameraDeviceWebRtcFieldsDefaultNull() {
        val camera = CameraDevice(
            name = "Default Cam",
            ipAddress = "10.0.0.1",
            port = 8080
        )

        assertNull(camera.deviceSecret)
        assertNull(camera.deviceId)
        assertNull(camera.ipv6Address)
        assertEquals("http://10.0.0.1:8080", camera.getBaseUrl())
    }

    @Test
    fun testCameraDeviceWithWebRtcCredentials() {
        val camera = CameraDevice(
            name = "WebRTC Node",
            ipAddress = "10.0.0.2",
            port = 8080,
            deviceSecret = "secret-aes-key",
            deviceId = "node-999",
            ipv6Address = "2001:db8::1"
        )

        assertEquals("secret-aes-key", camera.deviceSecret)
        assertEquals("node-999", camera.deviceId)
        assertEquals("2001:db8::1", camera.ipv6Address)
    }
}
