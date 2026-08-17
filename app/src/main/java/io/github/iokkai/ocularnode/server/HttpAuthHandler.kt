package io.github.iokkai.ocularnode.server

import io.github.iokkai.ocularnode.data.SettingsManager
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 負責 HTTP 請求的認證授權、PIN 碼比對與 Session Token 管理。
 */
class HttpAuthHandler {

    private val validSessionTokens = ConcurrentHashMap.newKeySet<String>()

    /**
     * 檢查 HTTP 請求是否已通過認證授權
     */
    fun isRequestAuthorized(settingsManager: SettingsManager, headers: Map<String, String>, rawPath: String): Boolean {
        if (!settingsManager.httpAuthEnabled) return true

        // 1. Bearer Token
        val authHeader = headers["authorization"] ?: ""
        if (authHeader.startsWith("Bearer ", ignoreCase = true)) {
            val token = authHeader.substringAfter("Bearer ").trim()
            if (token.isNotBlank() && validSessionTokens.contains(token)) return true
        }

        // 2. Custom header (X-Auth-Token)
        val customToken = headers["x-auth-token"] ?: ""
        if (customToken.isNotBlank() && validSessionTokens.contains(customToken)) return true

        // 3. Cookie session_token
        val cookie = headers["cookie"] ?: ""
        if (cookie.contains("session_token=")) {
            val token = cookie.substringAfter("session_token=").substringBefore(";").trim()
            if (token.isNotBlank() && validSessionTokens.contains(token)) return true
        }

        // 4. URL query param (?token=... 或 &token=...)
        if (rawPath.contains("token=")) {
            val token = rawPath.substringAfter("token=").substringBefore("&").trim()
            if (token.isNotBlank() && validSessionTokens.contains(token)) return true
        }

        return false
    }

    /**
     * 處理 POST /auth/login：驗證 PIN 碼並發放 Session Token
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
        if (inputPin == expectedPin || !settingsManager.httpAuthEnabled) {
            val token = UUID.randomUUID().toString().replace("-", "")
            if (validSessionTokens.size > 200) {
                validSessionTokens.clear()
            }
            validSessionTokens.add(token)

            return JSONObject().apply {
                put("status", "ok")
                put("token", token)
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
}
