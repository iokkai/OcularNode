package io.github.iokkai.ocularnode.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/**
 * S-4 Rate Limiting 邏輯測試。
 * 直接測試滑動視窗演算法，不依賴 Android 環境。
 */
class MjpegHttpServerRateLimitTest {

    companion object {
        private const val MAX_REQUESTS = 30
        private const val WINDOW_MS = 10_000L
    }

    // 複製 MjpegHttpServer 中 checkRateLimit 的純邏輯以進行單元測試
    private val ipRequestLog = ConcurrentHashMap<String, ArrayDeque<Long>>()

    private fun checkRateLimit(ip: String, now: Long = System.currentTimeMillis()): Boolean {
        val timestamps = ipRequestLog.getOrPut(ip) { ArrayDeque() }
        synchronized(timestamps) {
            while (timestamps.isNotEmpty() && now - timestamps.first() > WINDOW_MS) {
                timestamps.removeFirst()
            }
            return if (timestamps.size < MAX_REQUESTS) {
                timestamps.addLast(now)
                true
            } else {
                false
            }
        }
    }

    @Test
    fun `first request from new IP is always allowed`() {
        assertTrue(checkRateLimit("192.168.1.1"))
    }

    @Test
    fun `allows up to MAX_REQUESTS within window`() {
        val ip = "10.0.0.1"
        val now = System.currentTimeMillis()
        repeat(MAX_REQUESTS) { i ->
            assertTrue("Request ${i + 1} should be allowed", checkRateLimit(ip, now + i))
        }
    }

    @Test
    fun `blocks the (MAX_REQUESTS + 1)th request within window`() {
        val ip = "10.0.0.2"
        val now = System.currentTimeMillis()
        repeat(MAX_REQUESTS) { i -> checkRateLimit(ip, now + i) }
        assertFalse(checkRateLimit(ip, now + MAX_REQUESTS))
    }

    @Test
    fun `allows requests again after window expires`() {
        val ip = "10.0.0.3"
        val now = System.currentTimeMillis()
        repeat(MAX_REQUESTS) { i -> checkRateLimit(ip, now + i) }
        assertFalse(checkRateLimit(ip, now + MAX_REQUESTS))

        // Advance time past the window (10s + 1ms)
        val futureTime = now + WINDOW_MS + 1
        assertTrue(checkRateLimit(ip, futureTime))
    }

    @Test
    fun `different IPs have independent rate limit windows`() {
        val ip1 = "172.16.0.1"
        val ip2 = "172.16.0.2"
        val now = System.currentTimeMillis()

        repeat(MAX_REQUESTS) { i -> checkRateLimit(ip1, now + i) }
        assertFalse(checkRateLimit(ip1, now + MAX_REQUESTS))
        assertTrue(checkRateLimit(ip2, now))
    }

    @Test
    fun `sliding window evicts expired entries correctly`() {
        val ip = "192.168.0.100"
        val baseTime = 1_000_000L

        repeat(20) { i -> checkRateLimit(ip, baseTime + i * 100) }
        assertEquals(20, ipRequestLog[ip]?.size)

        repeat(10) { i -> checkRateLimit(ip, baseTime + 5_000 + i * 100) }
        assertEquals(30, ipRequestLog[ip]?.size)

        // Advance past window: first 20 should evict, 10 remain + 1 new
        checkRateLimit(ip, baseTime + WINDOW_MS + 1)
        val remaining = ipRequestLog[ip]?.size ?: 0
        assertTrue("Stale entries should have been evicted; remaining=$remaining", remaining < 30)
    }

    @Test
    fun `cleanup removes stale empty IP entries`() {
        val ip = "10.10.10.10"
        val now = System.currentTimeMillis()

        checkRateLimit(ip, now)
        assertTrue(ipRequestLog.containsKey(ip))

        val expiredTime = now + WINDOW_MS + 1
        val expiredIps = mutableListOf<String>()
        for ((k, timestamps) in ipRequestLog) {
            synchronized(timestamps) {
                while (timestamps.isNotEmpty() && expiredTime - timestamps.first() > WINDOW_MS) {
                    timestamps.removeFirst()
                }
                if (timestamps.isEmpty()) expiredIps.add(k)
            }
        }
        expiredIps.forEach { ipRequestLog.remove(it) }

        assertFalse("Expired IP should have been cleaned up", ipRequestLog.containsKey(ip))
    }
}
