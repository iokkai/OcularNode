package io.github.iokkai.ocularnode.ui.viewer

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.iokkai.ocularnode.data.SettingsManager
import io.github.iokkai.ocularnode.util.QRCodeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class WizardUiState(
    val currentStep: Int = 1,
    val wifiSsid: String = "",
    val wifiPassword: String = "",
    val apiKey: String = "",
    val isApiKeyVerified: Boolean = false,
    val isVerifyingApiKey: Boolean = false,
    val apiKeyVerifyError: String? = null,
    val isGeneratingQrCode: Boolean = false,
    val qrCodeBitmap: Bitmap? = null,
    val qrCodeError: String? = null,
    val generatedAuthKey: String? = null
)

class WizardViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(WizardUiState())
    val uiState: StateFlow<WizardUiState> = _uiState.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun loadSavedApiKey(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val savedKey = SettingsManager.getInstance(context).tailscaleApiKey
            if (savedKey.isNotBlank()) {
                _uiState.update {
                    it.copy(
                        apiKey = savedKey,
                        isApiKeyVerified = true
                    )
                }
            }
        }
    }

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

    fun setApiKey(key: String) {
        _uiState.update {
            it.copy(
                apiKey = key,
                isApiKeyVerified = false,
                apiKeyVerifyError = null
            )
        }
    }

    fun goToStep(step: Int) {
        _uiState.update { it.copy(currentStep = step) }
    }

    fun verifyApiKey(context: Context) {
        val apiKey = _uiState.value.apiKey.trim()
        if (apiKey.isBlank()) {
            _uiState.update { it.copy(apiKeyVerifyError = "Please enter Auth Key or API Key") }
            return
        }

        // 若使用者直接輸入 Tailscale Auth Key (tskey-auth-...)，格式正確即可直接使用
        if (apiKey.startsWith("tskey-auth")) {
            viewModelScope.launch(Dispatchers.IO) {
                SettingsManager.getInstance(context).tailscaleApiKey = apiKey
                _uiState.update {
                    it.copy(
                        isVerifyingApiKey = false,
                        isApiKeyVerified = true,
                        apiKeyVerifyError = null
                    )
                }
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isVerifyingApiKey = true,
                    apiKeyVerifyError = null
                )
            }

            try {
                val responseSuccess = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("https://api.tailscale.com/api/v2/tailnet/-/keys")
                        .header("Authorization", "Bearer $apiKey")
                        .get()
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            true
                        } else {
                            val errBody = response.body?.string() ?: ""
                            throw Exception("Verification failed (HTTP ${response.code}): ${response.message} $errBody")
                        }
                    }
                }

                if (responseSuccess) {
                    withContext(Dispatchers.IO) {
                        SettingsManager.getInstance(context).tailscaleApiKey = apiKey
                    }
                    _uiState.update {
                        it.copy(
                            isVerifyingApiKey = false,
                            isApiKeyVerified = true,
                            apiKeyVerifyError = null
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isVerifyingApiKey = false,
                        isApiKeyVerified = false,
                        apiKeyVerifyError = e.localizedMessage ?: "Network connection failed, please check API Key"
                    )
                }
            }
        }
    }

    fun generateProvisioningQrCode(context: Context) {
        val apiKey = _uiState.value.apiKey.trim()
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
                // 1. 取得 Auth Key (若已是 tskey-auth 則直接使用，否則透過 API 向 Tailscale 申請)
                val authKey = if (apiKey.startsWith("tskey-auth")) {
                    apiKey
                } else {
                    withContext(Dispatchers.IO) {
                        val jsonBody = JSONObject().apply {
                            val devicesObj = JSONObject().apply {
                                val createObj = JSONObject().apply {
                                    put("reusable", false)
                                    put("ephemeral", false)
                                    put("preauthorized", true)
                                }
                                put("create", createObj)
                            }
                            put("capabilities", JSONObject().put("devices", devicesObj))
                            put("expirySeconds", 86400)
                            put("description", "OcularNode Camera Provisioning Key")
                        }

                        val mediaType = "application/json; charset=utf-8".toMediaType()
                        val requestBody = jsonBody.toString().toRequestBody(mediaType)

                        val request = Request.Builder()
                            .url("https://api.tailscale.com/api/v2/tailnet/-/keys")
                            .header("Authorization", "Bearer $apiKey")
                            .post(requestBody)
                            .build()

                        httpClient.newCall(request).execute().use { response ->
                            val respStr = response.body?.string() ?: ""
                            if (!response.isSuccessful) {
                                throw Exception("Failed to request Auth Key (HTTP ${response.code}): $respStr")
                            }
                            val respJson = JSONObject(respStr)
                            respJson.optString("key").ifBlank {
                                throw Exception("Unable to parse Auth Key from response")
                            }
                        }
                    }
                }

                // 1.5 Fetch Latest APK URL from GitHub
                val fetchedApkUrl = io.github.iokkai.ocularnode.util.ZeroTouchProvisionManager.getLatestReleaseApkUrl(
                    io.github.iokkai.ocularnode.BuildConfig.GITHUB_OWNER,
                    io.github.iokkai.ocularnode.BuildConfig.GITHUB_REPO
                )

                // 1.6 Fetch APK Checksum (Required for Android 9+ DO provisioning)
                val checksum = io.github.iokkai.ocularnode.util.ZeroTouchProvisionManager.getApkSha256Checksum(fetchedApkUrl)
                    ?: throw Exception("Unable to get APK Checksum (SHA-256), required for Android 9+ provisioning. Please check network connection.")

                // 2. Build DO Provisioning JSON
                val extrasBundle = JSONObject().apply {
                    put("tailscale_auth_key", authKey)
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

                // 3. Generate QR Code Bitmap
                val bitmap = withContext(Dispatchers.Default) {
                    QRCodeUtils.generateQRCodeBitmap(jsonString, 512)
                }

                if (bitmap != null) {
                    _uiState.update {
                        it.copy(
                            isGeneratingQrCode = false,
                            generatedAuthKey = authKey,
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
