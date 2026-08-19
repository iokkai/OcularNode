package io.github.iokkai.ocularnode.webrtc

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.iokkai.ocularnode.data.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebRtcIceServerTest {

    @Test
    fun testDefaultIceServersIncludeStunServers() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsManager.getInstance(context)
        settings.customTurnServerUrl = ""

        val sessionManager = WebRtcSessionManager.getInstance(context)
        val iceServers = sessionManager.getDefaultIceServers()

        assertNotNull(iceServers)
        assertTrue(iceServers.isNotEmpty())
        assertTrue(iceServers.any { it.urls.any { url -> url.contains("stun") } })
    }

    @Test
    fun testCustomTurnServerInjection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsManager.getInstance(context)
        settings.customTurnServerUrl = "turn:turn.cloudflare.com:3478"
        settings.customTurnUsername = "testuser"
        settings.customTurnPassword = "testpassword"

        val sessionManager = WebRtcSessionManager.getInstance(context)
        val iceServers = sessionManager.getDefaultIceServers()

        assertNotNull(iceServers)
        assertTrue(iceServers.any { it.urls.any { url -> url.contains("turn.cloudflare.com") } })

        // Clean up
        settings.customTurnServerUrl = ""
        settings.customTurnUsername = ""
        settings.customTurnPassword = ""
    }
}
