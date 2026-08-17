package io.github.iokkai.ocularnode.server

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-2 Token masking 邏輯測試。
 * 測試 botToken 遮蔽格式與防覆蓋保護。
 */
class TokenMaskingTest {

    /** 複製 CameraApiHandler 中的 masking 邏輯 */
    private fun maskToken(rawToken: String): String {
        return if (rawToken.length > 8) {
            "${rawToken.take(6)}****${rawToken.takeLast(4)}"
        } else if (rawToken.isNotBlank()) {
            "****"
        } else {
            ""
        }
    }

    /** 複製 RemoteCommandHandler 中防覆蓋判斷邏輯 */
    private fun shouldUpdateToken(incoming: String): Boolean {
        val trimmed = incoming.trim()
        return trimmed.isNotBlank() && !trimmed.contains("*")
    }

    // ── Masking ──

    @Test
    fun `long token is masked keeping prefix and suffix`() {
        val token = "123456789:ABCDefghIJK"
        val masked = maskToken(token)
        assertTrue(masked.startsWith("123456"))
        assertTrue(masked.endsWith("hIJK"))
        assertTrue(masked.contains("****"))
        assertFalse(masked.contains("789:ABCD"))
    }

    @Test
    fun `short non-empty token is fully masked`() {
        val masked = maskToken("12345")
        assertTrue(masked == "****")
    }

    @Test
    fun `empty token returns empty string`() {
        val masked = maskToken("")
        assertTrue(masked.isEmpty())
    }

    // ── Anti-overwrite protection ──

    @Test
    fun `real token triggers update`() {
        assertTrue(shouldUpdateToken("123456789:ABCDefghIJK"))
    }

    @Test
    fun `masked token (contains asterisks) does NOT trigger update`() {
        assertFalse(shouldUpdateToken("123456****hIJK"))
        assertFalse(shouldUpdateToken("****"))
        assertFalse(shouldUpdateToken("123***456"))
    }

    @Test
    fun `blank token does NOT trigger update`() {
        assertFalse(shouldUpdateToken(""))
        assertFalse(shouldUpdateToken("   "))
    }

    @Test
    fun `token with leading and trailing spaces is trimmed before check`() {
        // Real token with spaces should still trigger update
        assertTrue(shouldUpdateToken("  123456789:ABC  "))
        // Masked token with spaces should not
        assertFalse(shouldUpdateToken("  123456****  "))
    }
}
