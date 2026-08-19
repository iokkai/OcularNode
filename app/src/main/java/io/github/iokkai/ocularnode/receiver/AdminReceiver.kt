package io.github.iokkai.ocularnode.receiver

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import android.util.Log
import io.github.iokkai.ocularnode.data.SettingsManager
import io.github.iokkai.ocularnode.util.TailscaleLegacyManager
import io.github.iokkai.ocularnode.util.ZeroTouchProvisionManager
import io.github.iokkai.ocularnode.webrtc.crypto.PairingSecretManager

class AdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i("AdminReceiver", "Device Admin / Device Owner enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i("AdminReceiver", "Device Admin / Device Owner disabled")
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        Log.i("AdminReceiver", "onProfileProvisioningComplete triggered")

        try {
            val extras = androidx.core.content.IntentCompat.getParcelableExtra(intent, DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, PersistableBundle::class.java)
            val authKey = extras?.getString("tailscale_auth_key") ?: ""
            val mqttSecret = extras?.getString("mqtt_device_secret") ?: ""
            val role = extras?.getString("device_role") ?: "CAMERA"
            val wifiSsid = extras?.getString("wifi_ssid") ?: ""
            val connectionMode = extras?.getString("connection_mode") ?: (if (authKey.isNotBlank()) "TAILSCALE" else "WEBRTC")

            Log.i("AdminReceiver", "Provisioning extras intercepted: Role=$role, Mode=$connectionMode, SecretLength=${mqttSecret.length}, WifiSSID=$wifiSsid")

            val settingsManager = SettingsManager.getInstance(context)
            settingsManager.deviceRoleMode = role
            settingsManager.connectionMode = connectionMode
            settingsManager.isKioskModeActive = true
            if (authKey.isNotBlank()) {
                settingsManager.tailscaleAuthKey = authKey
            }
            if (mqttSecret.isNotBlank()) {
                PairingSecretManager.getInstance(context).setDeviceSecret(mqttSecret)
            }

            // 電池最佳化豁免
            ZeroTouchProvisionManager.exemptFromBatteryOptimizations(context)

            // 強制開啟系統 NTP 自動校時 (防止長期斷網離線導致時鐘漂移與 TLS 證書失效)
            try {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                val admin = ZeroTouchProvisionManager.getAdminComponent(context)
                if (dpm?.isDeviceOwnerApp(context.packageName) == true) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        dpm.setAutoTimeEnabled(admin, true)
                    } else {
                        @Suppress("DEPRECATION")
                        dpm.setAutoTimeRequired(admin, true)
                    }
                    Log.i("AdminReceiver", "已成功啟用 Device Owner 強制 NTP 自動校時 (Auto Time Required)")
                }
            } catch (e: Exception) {
                Log.w("AdminReceiver", "設定 AutoTimeRequired 失敗", e)
            }

            // 1. 自動喚醒並啟動 MainActivity (確保在 Provisioning 結束後自動開啟 App 畫面)
            try {
                val launchIntent = Intent(context, io.github.iokkai.ocularnode.MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(launchIntent)
                Log.i("AdminReceiver", "已成功喚醒並啟動 MainActivity")
            } catch (e: Exception) {
                Log.e("AdminReceiver", "啟動 MainActivity 失敗", e)
            }

            // 2. 僅在明確配置 Tailscale 模式時觸發 Tailscale 下載與 VPN 安裝 (WebRTC P2P 預設秒級完成)
            if (connectionMode == "TAILSCALE" && authKey.isNotBlank()) {
                TailscaleLegacyManager.startZeroTouchPipeline(context, authKey)
            }
        } catch (e: Exception) {
            Log.e("AdminReceiver", "Error in onProfileProvisioningComplete", e)
        }
    }
}

