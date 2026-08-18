package io.github.iokkai.ocularnode.webrtc

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.iokkai.ocularnode.di.AppContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebRtcSessionManagerTest {

    @Test
    fun testDefaultIceServers() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sessionManager = WebRtcSessionManager(context)

        val iceServers = sessionManager.getDefaultIceServers()
        assertTrue("Ice servers list should not be empty", iceServers.isNotEmpty())

        val urls = iceServers.flatMap { it.urls }
        assertTrue("Should include Google STUN server", urls.any { it.contains("stun.l.google.com") })
        assertTrue("Should include Cloudflare STUN server", urls.any { it.contains("stun.cloudflare.com") })
    }

    @Test
    fun testAppContainerProvidesWebRtcSessionManager() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appContainer = AppContainer(context)

        assertNotNull(appContainer.webRtcSessionManager)
    }
}
