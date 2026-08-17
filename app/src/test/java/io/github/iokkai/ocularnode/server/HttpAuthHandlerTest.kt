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
        val failedResult = authHandler.handleLogin("{\"pin\":\"0000\"}", "/auth/login", settingsManager, "192.168.1.100")
        assertTrue(failedResult is LoginResult.InvalidPin)

        // Correct PIN
        val successResult = authHandler.handleLogin("{\"pin\":\"8888\"}", "/auth/login", settingsManager, "192.168.1.100")
        assertTrue(successResult is LoginResult.Success)

        val json = JSONObject((successResult as LoginResult.Success).responseJson)
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
    fun handleLogin_withRepeatedFailures_triggersBruteForceLockout() {
        settingsManager.httpAuthEnabled = true
        settingsManager.httpPinCode = "9999"
        val clientIp = "192.168.1.250"

        // Fail 4 times -> still InvalidPin
        for (i in 1..4) {
            val res = authHandler.handleLogin("{\"pin\":\"wrong\"}", "/auth/login", settingsManager, clientIp)
            assertTrue(res is LoginResult.InvalidPin)
        }

        // 5th failure -> triggers LockedOut (30s)
        val fifthRes = authHandler.handleLogin("{\"pin\":\"wrong\"}", "/auth/login", settingsManager, clientIp)
        assertTrue(fifthRes is LoginResult.LockedOut)
        assertTrue((fifthRes as LoginResult.LockedOut).retryAfterSeconds in 25..30)

        // Even with correct PIN while locked out, it must still reject with LockedOut
        val attemptDuringLockout = authHandler.handleLogin("{\"pin\":\"9999\"}", "/auth/login", settingsManager, clientIp)
        assertTrue(attemptDuringLockout is LoginResult.LockedOut)
    }

    @Test
    fun isTokenValid_withBlankOrInvalidToken_returnsFalse() {
        assertFalse(authHandler.isTokenValid(""))
        assertFalse(authHandler.isTokenValid("non_existent_token_12345"))
    }
}
