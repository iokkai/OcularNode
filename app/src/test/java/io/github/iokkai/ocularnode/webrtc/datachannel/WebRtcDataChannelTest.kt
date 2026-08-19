package io.github.iokkai.ocularnode.webrtc.datachannel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebRtcDataChannelTest {

    @Test
    fun testDataChannelCommandSerialization() {
        val original = DataChannelCommand(
            action = DataChannelCommand.ACTION_TORCH_TOGGLE,
            params = mapOf("value" to "on"),
            timestamp = 1700000000000L
        )

        val json = original.toJson()
        assertTrue(json.contains(DataChannelCommand.ACTION_TORCH_TOGGLE))
        assertTrue(json.contains("on"))

        val deserialized = DataChannelCommand.fromJson(json)
        assertEquals(original.action, deserialized.action)
        assertEquals("on", deserialized.params["value"])
        assertEquals(original.timestamp, deserialized.timestamp)
    }

    @Test
    fun testFromLegacyMapping() {
        val torchCmd = DataChannelCommand.fromLegacy("torch", "on")
        assertEquals(DataChannelCommand.ACTION_TORCH_TOGGLE, torchCmd.action)
        assertEquals("on", torchCmd.params["value"])
        assertEquals(Pair("torch", "on"), torchCmd.toLegacyPair())

        val camCmd = DataChannelCommand.fromLegacy("camera", "")
        assertEquals(DataChannelCommand.ACTION_SWITCH_CAMERA, camCmd.action)
        assertEquals(Pair("camera", ""), camCmd.toLegacyPair())

        val resCmd = DataChannelCommand.fromLegacy("resolution", "720p")
        assertEquals(DataChannelCommand.ACTION_RESOLUTION, resCmd.action)
        assertEquals(Pair("resolution", "720p"), resCmd.toLegacyPair())

        val fpsCmd = DataChannelCommand.fromLegacy("fps", "30")
        assertEquals(DataChannelCommand.ACTION_FPS, fpsCmd.action)
        assertEquals(Pair("fps", "30"), fpsCmd.toLegacyPair())

        val nightCmd = DataChannelCommand.fromLegacy("night_vision", "auto")
        assertEquals(DataChannelCommand.ACTION_NIGHT_VISION, nightCmd.action)
        assertEquals(Pair("night_vision", "auto"), nightCmd.toLegacyPair())

        val alarmCmd = DataChannelCommand.fromLegacy("alarm", "")
        assertEquals(DataChannelCommand.ACTION_PLAY_ALARM, alarmCmd.action)
        assertEquals(Pair("alarm", ""), alarmCmd.toLegacyPair())
    }

    @Test
    fun testZoomCommandMapping() {
        val zoomCmd = DataChannelCommand.fromLegacy("zoom", "2.5")
        assertEquals(DataChannelCommand.ACTION_PTZ_ZOOM, zoomCmd.action)
        assertEquals("2.5", zoomCmd.params["value"])
        assertEquals(Pair("zoom", "2.5"), zoomCmd.toLegacyPair())
    }
}
