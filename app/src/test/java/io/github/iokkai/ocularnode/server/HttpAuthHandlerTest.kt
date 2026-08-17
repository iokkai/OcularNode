package io.github.iokkai.ocularnode.server

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.iokkai.ocularnode.data.SettingsManager
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HttpAuthHandlerTest {

    private lateinit var context: Context
    private lateinit var settingsManager: SettingsManager
    private lateinit var authHandler: HttpAuthHandler

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        settingsManager = SettingsManager(context)
        authHandler = HttpAuthHandler()
    }

    @Test
    fun isRequestAuthorized_whenAuthDisabled_alwaysReturnsTrue() {
        settingsManager.httpAuthEnabled = false
        val headers = emptyMap<String, String>()
        val isAuth = authHandler.isRequestAuthorized(settingsManager, headers, "/mjpeg")
        assertTrue(isAuth)
    }

    @Test
    fun isRequestAuthorized_whenAuthEnabled_blocksUnauthenticatedRequests() {
        settingsManager.httpAuthEnabled = true
        settingsManager.httpPinCode = "1234"
        val headers = emptyMap<String, String>()

        val isAuth = authHandler.isRequestAuthorized(settingsManager, headers, "/mjpeg")
        assertFalse(isAuth)
    }

    @Test
    fun handleLogin_withCorrectPin_generatesValidTokenAndAllowsAccess() {
        settingsManager.httpAuthEnabled = true
        settingsManager.httpPinCode = "8888"

        // Incorrect PIN
        val failedResult = authHandler.handleLogin("{\"pin\":\"0000\"}", "/auth/login", settingsManager)
        assertNull(failedResult)

        // Correct PIN
        val successResult = authHandler.handleLogin("{\"pin\":\"8888\"}", "/auth/login", settingsManager)
        assertNotNull(successResult)

        val json = JSONObject(successResult!!)
        val token = json.getString("token")
        assertTrue(token.isNotBlank())

        // 1. Test Bearer Header
        val bearerHeaders = mapOf("authorization" to "Bearer $token")
        assertTrue(authHandler.isRequestAuthorized(settingsManager, bearerHeaders, "/mjpeg"))

        // 2. Test X-Auth-Token Header
        val customHeaders = mapOf("x-auth-token" to token)
        assertTrue(authHandler.isRequestAuthorized(settingsManager, customHeaders, "/mjpeg"))

        // 3. Test Cookie session_token
        val cookieHeaders = mapOf("cookie" to "other_cookie=123; session_token=$token; lang=zh")
        assertTrue(authHandler.isRequestAuthorized(settingsManager, cookieHeaders, "/mjpeg"))

        // 4. Test URL Query parameter
        assertTrue(authHandler.isRequestAuthorized(settingsManager, emptyMap(), "/mjpeg?token=$token"))
        assertTrue(authHandler.isRequestAuthorized(settingsManager, emptyMap(), "/mjpeg?t=123456&token=$token&fps=30"))
    }

    @Test
    fun isTokenValid_withBlankOrInvalidToken_returnsFalse() {
        assertFalse(authHandler.isTokenValid(""))
        assertFalse(authHandler.isTokenValid("non_existent_token_12345"))
    }
}
