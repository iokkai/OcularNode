package io.github.iokkai.ocularnode.ui.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.iokkai.ocularnode.data.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsTurnTest {

    @Test
    fun testSettingsViewModelTurnUpdates() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val settingsManager = SettingsManager.getInstance(app)
        val viewModel = SettingsViewModel(app)

        viewModel.updateCustomTurnServerUrl("turn:turn.cloudflare.com:3478")
        viewModel.updateCustomTurnUsername("admin")
        viewModel.updateCustomTurnPassword("secret123")

        assertEquals("turn:turn.cloudflare.com:3478", viewModel.customTurnServerUrl.value)
        assertEquals("admin", viewModel.customTurnUsername.value)
        assertEquals("secret123", viewModel.customTurnPassword.value)

        assertEquals("turn:turn.cloudflare.com:3478", settingsManager.customTurnServerUrl)
        assertEquals("admin", settingsManager.customTurnUsername)
        assertEquals("secret123", settingsManager.customTurnPassword)

        // Clean up
        viewModel.updateCustomTurnServerUrl("")
        viewModel.updateCustomTurnUsername("")
        viewModel.updateCustomTurnPassword("")
    }
}
