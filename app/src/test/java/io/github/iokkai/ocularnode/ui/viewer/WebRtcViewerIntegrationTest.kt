package io.github.iokkai.ocularnode.ui.viewer

import io.github.iokkai.ocularnode.data.CameraDevice
import io.github.iokkai.ocularnode.webrtc.datachannel.DataChannelCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebRtcViewerIntegrationTest {

    @Test
    fun testDataChannelCommandCreation() {
        val torchCmd = DataChannelCommand.fromLegacy("torch", "on")
        assertEquals(DataChannelCommand.ACTION_TORCH_TOGGLE, torchCmd.action)
        assertEquals("on", torchCmd.params["value"])

        val cameraSwitchCmd = DataChannelCommand.fromLegacy("camera", "switch")
        assertEquals(DataChannelCommand.ACTION_SWITCH_CAMERA, cameraSwitchCmd.action)

        val nightVisionCmd = DataChannelCommand.fromLegacy("night_vision", "auto")
        assertEquals(DataChannelCommand.ACTION_NIGHT_VISION, nightVisionCmd.action)
        assertEquals("auto", nightVisionCmd.params["value"])
    }

    @Test
    fun testWebRtcStatusBadgeLogic() {
        // Case 1: Roaming
        val isRoaming = true
        val statusText1 = if (isRoaming) "⏳ 漫遊重連中" else "Other"
        assertEquals("⏳ 漫遊重連中", statusText1)

        // Case 2: WebRTC Connected with IPv6
        val cameraIpv6 = CameraDevice(
            name = "Test Cam",
            ipAddress = "192.168.1.5",
            port = 8080,
            ipv6Address = "2001:b400::1"
        )
        val isWebRtcConnected = true
        val isConnecting = false
        val customTurnUrl = ""

        val badgeText = when {
            isRoaming -> "⏳ 漫遊重連中"
            isConnecting -> "🔄 ICE 連線中"
            isWebRtcConnected -> {
                when {
                    !cameraIpv6.ipv6Address.isNullOrBlank() -> "⚡ P2P (IPv6)"
                    customTurnUrl.isNotBlank() -> "🛡️ TURN 中繼"
                    else -> "⚡ P2P (STUN)"
                }
            }
            else -> "❌ 斷線重連中"
        }
        assertEquals("⚡ P2P (IPv6)", badgeText)

        // Case 3: WebRTC Connected with STUN
        val cameraIpv4 = CameraDevice(
            name = "Test Cam",
            ipAddress = "192.168.1.5",
            port = 8080,
            ipv6Address = null
        )
        val badgeTextStun = when {
            !cameraIpv4.ipv6Address.isNullOrBlank() -> "⚡ P2P (IPv6)"
            customTurnUrl.isNotBlank() -> "🛡️ TURN 中繼"
            else -> "⚡ P2P (STUN)"
        }
        assertEquals("⚡ P2P (STUN)", badgeTextStun)

        // Case 4: WebRTC Connected with Custom TURN
        val customTurn = "turn:coturn.example.com:3478"
        val badgeTextTurn = when {
            !cameraIpv4.ipv6Address.isNullOrBlank() -> "⚡ P2P (IPv6)"
            customTurn.isNotBlank() -> "🛡️ TURN 中繼"
            else -> "⚡ P2P (STUN)"
        }
        assertEquals("🛡️ TURN 中繼", badgeTextTurn)
    }

    @Test
    fun testPtzZoomCommand() {
        val zoomScale = 2.5f
        val zoomCmd = DataChannelCommand(
            action = DataChannelCommand.ACTION_PTZ_ZOOM,
            params = mapOf("zoom" to zoomScale)
        )
        assertEquals(DataChannelCommand.ACTION_PTZ_ZOOM, zoomCmd.action)
        assertEquals(2.5f, zoomCmd.params["zoom"])
        val jsonStr = zoomCmd.toJson()
        assertNotNull(jsonStr)
        assertTrue(jsonStr.contains("zoom") && jsonStr.contains("2.5"))
    }
}
