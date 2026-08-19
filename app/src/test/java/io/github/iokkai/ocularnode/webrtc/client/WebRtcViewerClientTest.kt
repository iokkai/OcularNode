package io.github.iokkai.ocularnode.webrtc.client

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.iokkai.ocularnode.webrtc.WebRtcSessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebRtcViewerClientTest {

    @Test
    fun testViewerClientInitializationAndDisconnect() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sessionManager = WebRtcSessionManager.getInstance(context)
        val client = WebRtcViewerClient(sessionManager)

        assertNotNull(client.viewerSessionId)
        assertTrue(client.viewerSessionId.startsWith("viewer-"))

        assertFalse(client.isConnected.value)
        assertFalse(client.isConnecting.value)
        assertFalse(client.isRoaming.value)
        assertFalse(client.isDataChannelOpen)

        client.disconnect()

        assertFalse(client.isConnected.value)
        assertFalse(client.isRoaming.value)
        assertEquals("Disconnected", client.statusMessage.value)
    }

    @Test
    fun testWatchdogConstants() {
        assertEquals(2500L, WebRtcViewerClient.LEVEL_2_DISCONNECT_THRESHOLD_MS)
        assertEquals(10000L, WebRtcViewerClient.LEVEL_3_HARD_RESET_THRESHOLD_MS)
    }
}
