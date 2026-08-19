package io.github.iokkai.ocularnode.receiver

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import io.github.iokkai.ocularnode.MainActivity
import io.github.iokkai.ocularnode.data.SettingsManager
import io.github.iokkai.ocularnode.util.ZeroTouchProvisionManager

/**
 * Android 12+ (API 31+) 專用：提供 SetupWizard 佈署模式。
 * 當系統透過 QR Code 啟動裝置佈署時，詢問 DPC 所需的管理模式。
 * 回傳 PROVISIONING_MODE_FULLY_MANAGED_DEVICE 代表將裝置設為全受控專用設備 (Device Owner)。
 */
class GetProvisioningModeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "GetProvisioningModeActivity triggered by SetupWizard")

        val resultIntent = Intent().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                putExtra(
                    DevicePolicyManager.EXTRA_PROVISIONING_MODE,
                    DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE
                )
            }
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    companion object {
        private const val TAG = "GetProvisioningMode"
    }
}

/**
 * Android 10+ (API 29+) 專用：Android Enterprise 官方政策合規與初始化 Activity。
 * 在 SetupWizard 完成佈署流程時，由系統主動以「前景」方式拉起本 Activity，
 * 藉此繞過 Android 10+ 對 BroadcastReceiver 背景啟動 Activity 的限制，
 * 確保在 Setup 結束後順暢切換至 OcularNode 主畫面並啟動零接觸安裝流程。
 */
class AdminPolicyComplianceActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "AdminPolicyComplianceActivity triggered by SetupWizard")

        try {
            val extras = androidx.core.content.IntentCompat.getParcelableExtra(
                intent,
                DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE,
                PersistableBundle::class.java
            )
            val authKey = extras?.getString("tailscale_auth_key") ?: ""
            val mqttSecret = extras?.getString("mqtt_device_secret") ?: ""
            val role = extras?.getString("device_role") ?: "CAMERA"
            val wifiSsid = extras?.getString("wifi_ssid") ?: ""
            val connectionMode = extras?.getString("connection_mode") ?: (if (authKey.isNotBlank()) "TAILSCALE" else "WEBRTC")

            Log.i(TAG, "Compliance intercepted extras: Role=$role, Mode=$connectionMode, SecretLength=${mqttSecret.length}, WifiSSID=$wifiSsid")

            val settingsManager = SettingsManager.getInstance(applicationContext)
            settingsManager.deviceRoleMode = role
            settingsManager.connectionMode = connectionMode
            settingsManager.isKioskModeActive = true
            if (authKey.isNotBlank()) {
                settingsManager.tailscaleAuthKey = authKey
            }
            if (mqttSecret.isNotBlank()) {
                io.github.iokkai.ocularnode.webrtc.crypto.PairingSecretManager.getInstance(applicationContext).setDeviceSecret(mqttSecret)
            }

            // 電池最佳化豁免
            ZeroTouchProvisionManager.exemptFromBatteryOptimizations(applicationContext)

            // 1. 喚醒並啟動 MainActivity (因為當前為 SetupWizard 信任的前景 Activity，可安全發起 Activity 轉移)
            try {
                val launchIntent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(launchIntent)
                Log.i(TAG, "已成功透過合規流程開啟 MainActivity")
            } catch (e: Exception) {
                Log.e(TAG, "啟動 MainActivity 失敗", e)
            }

            // 2. 僅在明確配置 Tailscale 模式時觸發 Tailscale 部署 (WebRTC P2P 預設秒級完成)
            if (connectionMode == "TAILSCALE" && authKey.isNotBlank()) {
                io.github.iokkai.ocularnode.util.TailscaleLegacyManager.startZeroTouchPipeline(applicationContext, authKey)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error during admin policy compliance", e)
        }

        // 回傳 RESULT_OK 通知 SetupWizard 政策套用完成，SetupWizard 將正式交棒結束
        setResult(RESULT_OK)
        finish()
    }

    companion object {
        private const val TAG = "PolicyCompliance"
    }
}
