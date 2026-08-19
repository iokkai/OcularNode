package io.github.iokkai.ocularnode.util

import android.content.Context
import android.os.PersistableBundle
import androidx.test.core.app.ApplicationProvider
import io.github.iokkai.ocularnode.data.SettingsManager
import io.github.iokkai.ocularnode.webrtc.crypto.PairingSecretManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TailscaleDecouplingTest {

    private lateinit var context: Context
    private lateinit var settingsManager: SettingsManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        settingsManager = SettingsManager.getInstance(context)
        settingsManager.connectionMode = "WEBRTC"
    }

    @Test
    fun testDefaultConnectionModeIsWebRtc() {
        assertEquals("WEBRTC", settingsManager.connectionMode)
        assertFalse(TailscaleLegacyManager.isTailscaleLegacyEnabled(context))
    }

    @Test
    fun testSwitchToTailscaleConnectionMode() {
        settingsManager.connectionMode = "TAILSCALE"
        assertEquals("TAILSCALE", settingsManager.connectionMode)
        assertTrue(TailscaleLegacyManager.isTailscaleLegacyEnabled(context))
    }

    @Test
    fun testProvisioningExtrasParsingWithWebRtcSecret() {
        val extras = PersistableBundle().apply {
            putString("mqtt_device_secret", "sec_1234567890abcdef")
            putString("device_role", "CAMERA")
            putString("connection_mode", "WEBRTC")
        }

        val role = extras.getString("device_role") ?: "CAMERA"
        val connectionMode = extras.getString("connection_mode") ?: "WEBRTC"
        val mqttSecret = extras.getString("mqtt_device_secret") ?: ""

        assertEquals("CAMERA", role)
        assertEquals("WEBRTC", connectionMode)
        assertEquals("sec_1234567890abcdef", mqttSecret)

        if (mqttSecret.isNotBlank()) {
            PairingSecretManager.getInstance(context).setDeviceSecret(mqttSecret)
        }
        assertEquals("sec_1234567890abcdef", PairingSecretManager.getInstance(context).getDeviceSecret())
    }

    @Test
    fun testTailscalePipelineSkippedWhenWebRtcMode() {
        // When connection mode is WEBRTC (default), isTailscaleLegacyEnabled should be false
        settingsManager.connectionMode = "WEBRTC"
        assertFalse(TailscaleLegacyManager.isTailscaleLegacyEnabled(context))

        // When connection mode is TAILSCALE, isTailscaleLegacyEnabled should be true
        settingsManager.connectionMode = "TAILSCALE"
        assertTrue(TailscaleLegacyManager.isTailscaleLegacyEnabled(context))
    }
}
