package io.github.iokkai.ocularnode.ui.viewer

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.iokkai.ocularnode.util.QRCodeUtils
import io.github.iokkai.ocularnode.webrtc.crypto.AesGcmCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class WizardUiState(
    val currentStep: Int = 1,
    val wifiSsid: String = "",
    val wifiPassword: String = "",
    val isGeneratingQrCode: Boolean = false,
    val qrCodeBitmap: Bitmap? = null,
    val qrCodeError: String? = null,
    val generatedDeviceSecret: String? = null
)

class WizardViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(WizardUiState())
    val uiState: StateFlow<WizardUiState> = _uiState.asStateFlow()

    fun autoFillCurrentWifi(context: Context): Boolean {
        val currentSsid = io.github.iokkai.ocularnode.util.NetworkUtils.getCurrentWifiSsid(context)
        return if (!currentSsid.isNullOrBlank()) {
            _uiState.update { it.copy(wifiSsid = currentSsid) }
            true
        } else {
            false
        }
    }

    fun setWifiSsid(ssid: String) {
        _uiState.update { it.copy(wifiSsid = ssid) }
    }

    fun setWifiPassword(pwd: String) {
        _uiState.update { it.copy(wifiPassword = pwd) }
    }

    fun goToStep(step: Int) {
        _uiState.update { it.copy(currentStep = step) }
    }

    fun generateProvisioningQrCode(context: Context) {
        val ssid = _uiState.value.wifiSsid.trim()
        val password = _uiState.value.wifiPassword

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGeneratingQrCode = true,
                    qrCodeError = null,
                    qrCodeBitmap = null
                )
            }

            try {
                // 1. Generate new 128-bit Device Secret for E2EE
                val deviceSecret = AesGcmCipher.generateRandomDeviceSecret()

                // 2. Fetch Latest APK URL from GitHub
                val fetchedApkUrl = io.github.iokkai.ocularnode.util.ZeroTouchProvisionManager.getLatestReleaseApkUrl(
                    io.github.iokkai.ocularnode.BuildConfig.GITHUB_OWNER,
                    io.github.iokkai.ocularnode.BuildConfig.GITHUB_REPO
                )

                // 3. Fetch APK Checksum (Required for Android 9+ DO provisioning)
                val checksum = io.github.iokkai.ocularnode.util.ZeroTouchProvisionManager.getApkSha256Checksum(fetchedApkUrl)
                    ?: throw Exception("Unable to get APK Checksum (SHA-256), required for Android 9+ provisioning. Please check network connection.")

                // 4. Build DO Provisioning JSON
                val extrasBundle = JSONObject().apply {
                    put("mqtt_device_secret", deviceSecret)
                    put("device_role", "CAMERA")
                }

                val provisioningJson = JSONObject().apply {
                    put(
                        "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME",
                        "io.github.iokkai.ocularnode/io.github.iokkai.ocularnode.receiver.AdminReceiver"
                    )
                    put(
                        "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION",
                        fetchedApkUrl
                    )
                    put(
                        "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM",
                        checksum
                    )
                    put("android.app.extra.PROVISIONING_WIFI_SSID", ssid)
                    put("android.app.extra.PROVISIONING_WIFI_PASSWORD", password)
                    put("android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED", true)
                    put("android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE", extrasBundle)
                }

                val jsonString = provisioningJson.toString()

                // 5. Generate QR Code Bitmap
                val bitmap = withContext(Dispatchers.Default) {
                    QRCodeUtils.generateQRCodeBitmap(jsonString, 512)
                }

                if (bitmap != null) {
                    _uiState.update {
                        it.copy(
                            isGeneratingQrCode = false,
                            generatedDeviceSecret = deviceSecret,
                            qrCodeBitmap = bitmap,
                            qrCodeError = null
                        )
                    }
                } else {
                    throw Exception("Failed to generate QR Code bitmap")
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGeneratingQrCode = false,
                        qrCodeError = e.localizedMessage ?: "Failed to generate provisioning QR code"
                    )
                }
            }
        }
    }
}
