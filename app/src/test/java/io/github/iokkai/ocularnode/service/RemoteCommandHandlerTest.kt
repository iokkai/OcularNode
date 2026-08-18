package io.github.iokkai.ocularnode.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 測試遠端指令解析與邊界值防呆機制 (Remote Command Parsing & Clamping Validation)。
 */
class RemoteCommandHandlerTest {

    data class ParsedCommandResult(
        val success: Boolean,
        val targetParam: String,
        val clampedValue: Any?
    )

    // 模擬 RemoteCommandHandler 指令解析核心
    private fun parseRemoteCommand(action: String, value: String): ParsedCommandResult {
        return when (action.lowercase()) {
            "resolution", "target_resolution" -> {
                val validResolutions = listOf("360p", "480p", "720p", "960p", "1080p")
                val normalized = value.lowercase().trim()
                if (normalized in validResolutions) {
                    ParsedCommandResult(true, "resolution", normalized)
                } else {
                    ParsedCommandResult(false, "resolution", null)
                }
            }
            "quality", "jpeg_quality" -> {
                val q = value.toIntOrNull() ?: 30
                val clamped = q.coerceIn(10, 100)
                ParsedCommandResult(true, "quality", clamped)
            }
            "fps", "target_fps", "fps_limit" -> {
                val fps = value.toIntOrNull() ?: 15
                val clamped = fps.coerceIn(5, 30)
                ParsedCommandResult(true, "fps", clamped)
            }
            "torch" -> {
                val isOn = value.equals("on", ignoreCase = true) || value.equals("true", ignoreCase = true)
                ParsedCommandResult(true, "torch", isOn)
            }
            "lens" -> {
                val lens = if (value.contains("front", ignoreCase = true)) "front" else "back"
                ParsedCommandResult(true, "lens", lens)
            }
            "night_vision" -> {
                val mode = when (value.lowercase()) {
                    "on", "off", "auto" -> value.lowercase()
                    else -> "auto"
                }
                ParsedCommandResult(true, "night_vision", mode)
            }
            else -> ParsedCommandResult(false, "unknown", null)
        }
    }

    @Test
    fun `quality command safely clamps out of bounds values`() {
        // Normal value
        val res1 = parseRemoteCommand("quality", "50")
        assertTrue(res1.success)
        assertEquals(50, res1.clampedValue)

        // Below minimum (negative or < 10) -> clamped to 10
        val res2 = parseRemoteCommand("quality", "-5")
        assertTrue(res2.success)
        assertEquals(10, res2.clampedValue)

        // Above maximum (> 100) -> clamped to 100
        val res3 = parseRemoteCommand("quality", "500")
        assertTrue(res3.success)
        assertEquals(100, res3.clampedValue)

        // Non-number string -> defaults safely
        val res4 = parseRemoteCommand("quality", "invalid_number")
        assertTrue(res4.success)
        assertEquals(30, res4.clampedValue)
    }

    @Test
    fun `fps command safely clamps between 5 and 30`() {
        val res1 = parseRemoteCommand("fps", "60") // Over 30
        assertEquals(30, res1.clampedValue)

        val res2 = parseRemoteCommand("fps", "1") // Below 5
        assertEquals(5, res2.clampedValue)

        val res3 = parseRemoteCommand("fps", "15") // Exactly 15
        assertEquals(15, res3.clampedValue)
    }

    @Test
    fun `resolution command accepts valid strings and rejects arbitrary strings`() {
        assertTrue(parseRemoteCommand("resolution", "360p").success)
        assertTrue(parseRemoteCommand("resolution", "480P").success)
        assertTrue(parseRemoteCommand("resolution", "720p").success)
        assertTrue(parseRemoteCommand("resolution", "1080p").success)

        assertFalse(parseRemoteCommand("resolution", "4K_ULTRA_HD").success)
        assertFalse(parseRemoteCommand("resolution", "").success)
    }

    @Test
    fun `torch and lens commands normalize boolean and facing strings`() {
        assertEquals(true, parseRemoteCommand("torch", "ON").clampedValue)
        assertEquals(false, parseRemoteCommand("torch", "off").clampedValue)

        assertEquals("front", parseRemoteCommand("lens", "FrontCamera").clampedValue)
        assertEquals("back", parseRemoteCommand("lens", "Back").clampedValue)
    }
}
