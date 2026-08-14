package io.github.iokkai.ocularnode.receiver

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.PersistableBundle
import android.util.Log
import io.github.iokkai.ocularnode.data.SettingsManager
import io.github.iokkai.ocularnode.util.ZeroTouchProvisionManager

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
            val role = extras?.getString("device_role") ?: "CAMERA"
            val wifiSsid = extras?.getString("wifi_ssid") ?: ""

            Log.i("AdminReceiver", "Provisioning extras intercepted: Role=$role, AuthKeyLength=${authKey.length}, WifiSSID=$wifiSsid")

            val settingsManager = SettingsManager(context)
            settingsManager.deviceRoleMode = role
            settingsManager.isKioskModeActive = true
            if (authKey.isNotBlank()) {
                settingsManager.tailscaleAuthKey = authKey
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

            // 2. 觸發零接觸安裝與設定流程 (下載 Tailscale、靜默安裝、政策注入、Always-On VPN)
            ZeroTouchProvisionManager.startZeroTouchPipeline(context, authKey)
        } catch (e: Exception) {
            Log.e("AdminReceiver", "Error in onProfileProvisioningComplete", e)
        }
    }
}

