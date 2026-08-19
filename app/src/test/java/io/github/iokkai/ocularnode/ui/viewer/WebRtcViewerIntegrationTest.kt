package io.github.iokkai.ocularnode.ui.viewer

import io.github.iokkai.ocularnode.data.CameraDevice
import io.github.iokkai.ocularnode.webrtc.datachannel.DataChannelCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebRtcViewerIntegrationTest {

    private fun computeStatusBadge(
        isRoaming: Boolean,
        isConnecting: Boolean,
        isWebRtcConnected: Boolean,
        camera: CameraDevice,
        customTurnUrl: String = ""
    ): String {
        return when {
            isRoaming -> "⏳ 漫遊重連中"
            isConnecting -> "🔄 ICE 連線中"
            isWebRtcConnected -> {
                when {
                    !camera.ipv6Address.isNullOrBlank() -> "⚡ P2P (IPv6)"
                    customTurnUrl.isNotBlank() -> "🛡️ TURN 中繼"
                    else -> "⚡ P2P (STUN)"
                }
            }
            else -> "❌ 斷線重連中"
        }
    }

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
        val defaultCam = CameraDevice(name = "Test", ipAddress = "192.168.1.5", port = 8080)
        val ipv6Cam = CameraDevice(name = "Test IPv6", ipAddress = "192.168.1.5", port = 8080, ipv6Address = "2001:b400::1")

        // Case 1: Roaming
        assertEquals(
            "⏳ 漫遊重連中",
            computeStatusBadge(isRoaming = true, isConnecting = false, isWebRtcConnected = true, camera = defaultCam)
        )

        // Case 2: ICE Connecting
        assertEquals(
            "🔄 ICE 連線中",
            computeStatusBadge(isRoaming = false, isConnecting = true, isWebRtcConnected = false, camera = defaultCam)
        )

        // Case 3: WebRTC Connected with IPv6
        assertEquals(
            "⚡ P2P (IPv6)",
            computeStatusBadge(isRoaming = false, isConnecting = false, isWebRtcConnected = true, camera = ipv6Cam)
        )

        // Case 4: WebRTC Connected with STUN (IPv4)
        assertEquals(
            "⚡ P2P (STUN)",
            computeStatusBadge(isRoaming = false, isConnecting = false, isWebRtcConnected = true, camera = defaultCam)
        )

        // Case 5: WebRTC Connected with Custom TURN
        assertEquals(
            "🛡️ TURN 中繼",
            computeStatusBadge(isRoaming = false, isConnecting = false, isWebRtcConnected = true, camera = defaultCam, customTurnUrl = "turn:coturn.example.com:3478")
        )

        // Case 6: Disconnected
        assertEquals(
            "❌ 斷線重連中",
            computeStatusBadge(isRoaming = false, isConnecting = false, isWebRtcConnected = false, camera = defaultCam)
        )
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
