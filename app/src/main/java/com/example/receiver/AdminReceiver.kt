package com.example.receiver

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.PersistableBundle
import android.util.Log
import com.example.data.SettingsManager
import com.example.util.ZeroTouchProvisionManager

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
            val extras = intent.getParcelableExtra<PersistableBundle>(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE)
            val authKey = extras?.getString("tailscale_auth_key") ?: ""
            val role = extras?.getString("device_role") ?: "CAMERA"
            val wifiSsid = extras?.getString("wifi_ssid") ?: ""

            Log.i("AdminReceiver", "Provisioning extras intercepted: Role=$role, AuthKeyLength=${authKey.length}, WifiSSID=$wifiSsid")

            val settingsManager = SettingsManager(context)
            settingsManager.deviceRoleMode = role
            if (authKey.isNotBlank()) {
                settingsManager.tailscaleAuthKey = authKey
            }

            // 觸發零接觸安裝與設定流程
            ZeroTouchProvisionManager.startZeroTouchPipeline(context, authKey)
        } catch (e: Exception) {
            Log.e("AdminReceiver", "Error in onProfileProvisioningComplete", e)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.example.ACTION_INSTALL_COMPLETE") {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            if (status == PackageInstaller.STATUS_SUCCESS) {
                Log.i("AdminReceiver", "Tailscale APK 靜默安裝成功！")
                val settingsManager = SettingsManager(context)
                val authKey = settingsManager.tailscaleAuthKey
                ZeroTouchProvisionManager.injectTailscaleRestrictionsAndEnableVpn(context, authKey)
            } else {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.e("AdminReceiver", "Tailscale APK 靜默安裝失敗 (Status $status): $message")
            }
            return
        }
        super.onReceive(context, intent)
    }
}

