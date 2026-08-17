package io.github.iokkai.ocularnode.server

import io.github.iokkai.ocularnode.data.SettingsManager
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 負責 HTTP 請求的認證授權、PIN 碼比對與 Session Token 生命週期管理（支援 TTL 與過期清理）。
 */
class HttpAuthHandler {

    companion object {
        /** Token 預設有效時間：24 小時 */
        const val TOKEN_TTL_MS = 24 * 60 * 60 * 1000L
        /** 最大快取 Token 數量上限 */
        private const val MAX_CACHED_TOKENS = 500
    }

    /** 儲存 Session Token -> 到期時間戳 (ms) */
    private val tokenExpirationMap = ConcurrentHashMap<String, Long>()

    /**
     * 檢查 Token 是否有效且尚未過期
     */
    fun isTokenValid(token: String): Boolean {
        if (token.isBlank()) return false
        val now = System.currentTimeMillis()
        val expiry = tokenExpirationMap[token] ?: return false
        if (now > expiry) {
            tokenExpirationMap.remove(token)
            return false
        }
        return true
    }

    /**
     * 檢查 HTTP 請求是否已通過認證授權
     */
    fun isRequestAuthorized(settingsManager: SettingsManager, headers: Map<String, String>, rawPath: String): Boolean {
        if (!settingsManager.httpAuthEnabled) return true

        // 1. Bearer Token
        val authHeader = headers["authorization"] ?: ""
        if (authHeader.startsWith("Bearer ", ignoreCase = true)) {
            val token = authHeader.substringAfter("Bearer ").trim()
            if (isTokenValid(token)) return true
        }

        // 2. Custom header (X-Auth-Token)
        val customToken = headers["x-auth-token"] ?: ""
        if (isTokenValid(customToken)) return true

        // 3. Cookie session_token
        val cookie = headers["cookie"] ?: ""
        if (cookie.contains("session_token=")) {
            val token = cookie.substringAfter("session_token=").substringBefore(";").trim()
            if (isTokenValid(token)) return true
        }

        // 4. URL query param (?token=... 或 &token=...)
        if (rawPath.contains("token=")) {
            val token = rawPath.substringAfter("token=").substringBefore("&").trim()
            if (isTokenValid(token)) return true
        }

        return false
    }

    /**
     * 處理 POST /auth/login：驗證 PIN 碼並發放具備 TTL 的 Session Token
     * 若成功回傳 Token JSON 字串，失敗回傳 null
     */
    fun handleLogin(body: String, rawPath: String, settingsManager: SettingsManager): String? {
        var inputPin = ""
        try {
            if (body.isNotBlank()) {
                val json = JSONObject(body)
                inputPin = json.optString("pin", "").trim()
            }
        } catch (_: Exception) {}

        if (inputPin.isBlank() && rawPath.contains("pin=")) {
            inputPin = rawPath.substringAfter("pin=").substringBefore("&").trim()
        }

        val expectedPin = settingsManager.httpPinCode
        val isPinCorrect = !settingsManager.httpAuthEnabled || safeCompareStrings(inputPin, expectedPin)

        if (isPinCorrect) {
            cleanupExpiredTokens()

            val token = UUID.randomUUID().toString().replace("-", "")
            val now = System.currentTimeMillis()
            tokenExpirationMap[token] = now + TOKEN_TTL_MS

            return JSONObject().apply {
                put("status", "ok")
                put("token", token)
                put("expiresInSeconds", TOKEN_TTL_MS / 1000)
                put("message", "Authenticated successfully")
            }.toString()
        }
        return null
    }

    /**
     * 處理 GET /auth/status：回傳當前授權要求狀態
     */
    fun handleAuthStatus(headers: Map<String, String>, rawPath: String, settingsManager: SettingsManager): String {
        val isAuth = isRequestAuthorized(settingsManager, headers, rawPath)
        return JSONObject().apply {
            put("status", "ok")
            put("authEnabled", settingsManager.httpAuthEnabled)
            put("authenticated", isAuth)
        }.toString()
    }

    /**
     * 清理過期的 Token；若超過上限則剔除最舊的條目
     */
    private fun cleanupExpiredTokens() {
        val now = System.currentTimeMillis()
        val iterator = tokenExpirationMap.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now > entry.value) {
                iterator.remove()
            }
        }

        if (tokenExpirationMap.size > MAX_CACHED_TOKENS) {
            // 超過上限時清理較舊的 Token
            val sortedByExpiry = tokenExpirationMap.entries.sortedBy { it.value }
            val removeCount = tokenExpirationMap.size - (MAX_CACHED_TOKENS / 2)
            for (i in 0 until removeCount) {
                if (i < sortedByExpiry.size) {
                    tokenExpirationMap.remove(sortedByExpiry[i].key)
                }
            }
        }
    }

    /**
     * 固定時間長度字串比較，防範計時側信道攻擊 (Timing Attack)
     */
    private fun safeCompareStrings(a: String, b: String): Boolean {
        val bytesA = a.toByteArray(Charsets.UTF_8)
        val bytesB = b.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(bytesA, bytesB)
    }
}
