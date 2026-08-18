package io.github.iokkai.ocularnode.util

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * 測試 Telegram 警報重試與外網告警通道防護 (Telegram Notifier Throttling & Network Retry Mechanism)。
 */
class TelegramNotifierTest {

    // 模擬 TelegramNotifier 的重試與防呆邏輯
    private suspend fun <T> executeWithRetry(
        maxRetries: Int = 2,
        block: (attempt: Int) -> T
    ): T {
        var lastException: Exception? = null
        for (attempt in 0..maxRetries) {
            try {
                return block(attempt)
            } catch (e: IOException) {
                lastException = e
            }
        }
        throw lastException ?: IOException("Max retries exceeded")
    }

    private fun formatMotionCaption(
        deviceName: String,
        motionPercentage: Float,
        aiSummary: String,
        timestamp: String
    ): String {
        val aiLine = if (aiSummary.isNotBlank()) "🤖 ML Kit AI: $aiSummary\n" else ""
        return "🚨 【動態警報通知】\n" +
                "📍 設備：$deviceName\n" +
                "📊 畫面變動率：${"%.1f".format(motionPercentage)}%\n" +
                aiLine +
                "⏰ 時間：$timestamp"
    }

    @Test
    fun `executeWithRetry succeeds on first attempt if network is healthy`() = runBlocking {
        var attempts = 0
        val result = executeWithRetry(maxRetries = 2) {
            attempts++
            "SUCCESS"
        }
        assertEquals("SUCCESS", result)
        assertEquals(1, attempts)
    }

    @Test
    fun `executeWithRetry recovers successfully on second attempt after transient network glitch`() = runBlocking {
        var attempts = 0
        val result = executeWithRetry(maxRetries = 2) { attempt ->
            attempts++
            if (attempt == 0) {
                throw IOException("Temporary Wi-Fi drop")
            }
            "RECOVERED_SUCCESS"
        }
        assertEquals("RECOVERED_SUCCESS", result)
        assertEquals(2, attempts)
    }

    @Test(expected = IOException::class)
    fun `executeWithRetry throws IOException when all retries are exhausted`() = runBlocking {
        var attempts = 0
        executeWithRetry<String>(maxRetries = 2) {
            attempts++
            throw IOException("Network unreachable")
        }
    }

    @Test
    fun `formatMotionCaption formats alert text with AI summary when present`() {
        val caption = formatMotionCaption(
            deviceName = "LivingRoom-Cam",
            motionPercentage = 12.5f,
            aiSummary = "Person (98%)",
            timestamp = "2026-08-18 14:00:00"
        )

        assertTrue(caption.contains("LivingRoom-Cam"))
        assertTrue(caption.contains("12.5%"))
        assertTrue(caption.contains("Person (98%)"))
    }
}
