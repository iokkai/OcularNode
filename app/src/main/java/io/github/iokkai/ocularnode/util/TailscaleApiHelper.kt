package io.github.iokkai.ocularnode.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 代表 Tailscale 裝置的連線與金鑰過期資訊
 */
data class TailscaleDeviceExpiryInfo(
    val deviceId: String,
    val name: String,
    val ipAddresses: List<String>,
    val keyExpiryDisabled: Boolean,
    val expiresAt: String?,
    val isExpired: Boolean = false
) {
    /**
     * 計算距離過期的剩餘天數，若已過期或已停用過期則回傳相應數值
     */
    fun getRemainingDays(): Long? {
        if (keyExpiryDisabled) return null
        if (expiresAt.isNullOrBlank()) return null
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            val cleanStr = expiresAt.substringBefore("Z").substringBefore(".")
            val expireDate = sdf.parse(cleanStr) ?: return null
            val diffMs = expireDate.time - System.currentTimeMillis()
            (diffMs / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * 負責透過 Tailscale 官方 REST API 查詢節點過期狀態與一鍵停用 180 天金鑰過期 (Disable Key Expiry)。
 */
object TailscaleApiHelper {

    private const val TAG = "TailscaleApiHelper"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * 查詢使用者 Tailnet 中所有裝置的過期資訊
     */
    suspend fun fetchDevicesExpiryInfo(apiKey: String): Result<List<TailscaleDeviceExpiryInfo>> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank() || cleanKey.startsWith("tskey-auth")) {
            return@withContext Result.failure(Exception("Auth Key does not have API permission, only API Key (tskey-api-...) is supported."))
        }

        try {
            val request = Request.Builder()
                .url("https://api.tailscale.com/api/v2/tailnet/-/devices")
                .header("Authorization", "Bearer $cleanKey")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Tailscale API Error (HTTP ${response.code}): $bodyStr"))
                }

                val json = JSONObject(bodyStr)
                val devicesArray = json.optJSONArray("devices") ?: JSONArray()
                val list = mutableListOf<TailscaleDeviceExpiryInfo>()

                for (i in 0 until devicesArray.length()) {
                    val dev = devicesArray.getJSONObject(i)
                    val id = dev.optString("id", "")
                    val name = dev.optString("name", "")
                    val keyExpiryDisabled = dev.optBoolean("keyExpiryDisabled", false)
                    val expires = dev.optString("expires", "")
                    val addressesArray = dev.optJSONArray("addresses")
                    val addrs = mutableListOf<String>()
                    if (addressesArray != null) {
                        for (j in 0 until addressesArray.length()) {
                            addrs.add(addressesArray.getString(j))
                        }
                    }

                    list.add(
                        TailscaleDeviceExpiryInfo(
                            deviceId = id,
                            name = name,
                            ipAddresses = addrs,
                            keyExpiryDisabled = keyExpiryDisabled,
                            expiresAt = if (expires.isNotBlank()) expires else null
                        )
                    )
                }

                Log.i(TAG, "Successfully fetched ${list.size} Tailscale devices info")
                Result.success(list)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Tailscale devices", e)
            Result.failure(e)
        }
    }

    /**
     * 透過 API 一鍵停用指定裝置的 180 天金鑰過期
     */
    suspend fun disableDeviceKeyExpiry(apiKey: String, deviceId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank() || cleanKey.startsWith("tskey-auth")) {
            return@withContext Result.failure(Exception("Only API Key (tskey-api-...) has permission to modify device settings."))
        }

        try {
            val payload = JSONObject().apply {
                put("keyExpiryDisabled", true)
            }
            val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("https://api.tailscale.com/api/v2/device/$deviceId/key")
                .header("Authorization", "Bearer $cleanKey")
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.i(TAG, "Successfully disabled key expiry for device $deviceId")
                    Result.success(true)
                } else {
                    val body = response.body?.string() ?: ""
                    Log.e(TAG, "Failed to disable key expiry (HTTP ${response.code}): $body")
                    Result.failure(Exception("HTTP ${response.code}: $body"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling key expiry for device $deviceId", e)
            Result.failure(e)
        }
    }
}
