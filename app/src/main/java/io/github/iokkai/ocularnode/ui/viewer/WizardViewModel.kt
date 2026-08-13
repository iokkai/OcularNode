@file:Suppress("DEPRECATION")
package io.github.iokkai.ocularnode.ui.viewer

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "ocular_node_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            e.printStackTrace()
            context.getSharedPreferences("ocular_node_fallback_prefs", Context.MODE_PRIVATE)
        }
    }

    fun loadSavedApiKey(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = getEncryptedPrefs(context)
            val savedKey = prefs.getString("tailscale_api_key", null)
            if (!savedKey.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        apiKey = savedKey,
                        isApiKeyVerified = true
                    )
                }
            }
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
            _uiState.update { it.copy(apiKeyVerifyError = "請輸入 API Key") }
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
                            throw Exception("驗證失敗 (HTTP ${response.code}): ${response.message} $errBody")
                        }
                    }
                }

                if (responseSuccess) {
                    withContext(Dispatchers.IO) {
                        getEncryptedPrefs(context).edit()
                            .putString("tailscale_api_key", apiKey)
                            .apply()
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
                        apiKeyVerifyError = e.localizedMessage ?: "網路連線失敗，請檢查 API Key"
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
                // 1. POST https://api.tailscale.com/api/v2/tailnet/-/keys to generate Auth Key
                val authKey = withContext(Dispatchers.IO) {
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
                            throw Exception("申請 Auth Key 失敗 (HTTP ${response.code}): $respStr")
                        }
                        val respJson = JSONObject(respStr)
                        respJson.optString("key").ifBlank {
                            throw Exception("無法從回應解析 Auth Key")
                        }
                    }
                }

                // 1.5 Fetch Latest APK URL from GitHub
                val fetchedApkUrl = io.github.iokkai.ocularnode.util.ZeroTouchProvisionManager.getLatestReleaseApkUrl(
                    io.github.iokkai.ocularnode.BuildConfig.GITHUB_OWNER,
                    io.github.iokkai.ocularnode.BuildConfig.GITHUB_REPO
                )

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
                    put("android.app.extra.PROVISIONING_WIFI_SSID", ssid)
                    put("android.app.extra.PROVISIONING_WIFI_PASSWORD", password)
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
                    throw Exception("生成 QR Code 圖片失敗")
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGeneratingQrCode = false,
                        qrCodeError = e.localizedMessage ?: "產生部署條碼失敗"
                    )
                }
            }
        }
    }
}
