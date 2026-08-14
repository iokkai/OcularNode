package io.github.iokkai.ocularnode.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import io.github.iokkai.ocularnode.data.SettingsManager
import io.github.iokkai.ocularnode.util.ZeroTouchProvisionManager

/**
 * 專門接收 PackageInstaller 靜默安裝回執的廣播接收器。
 * 獨立於 AdminReceiver，避免 BIND_DEVICE_ADMIN 權限限制導致系統 PackageInstaller 廣播無法派發。
 */
class PackageInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "io.github.iokkai.ocularnode.ACTION_INSTALL_COMPLETE") {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME) ?: ""

            if (status == PackageInstaller.STATUS_SUCCESS) {
                Log.i("PackageInstallReceiver", "APK 靜默安裝成功！Package: $packageName")
                val settingsManager = SettingsManager(context)
                val authKey = settingsManager.tailscaleAuthKey
                ZeroTouchProvisionManager.injectTailscaleRestrictionsAndEnableVpn(context, authKey)
            } else {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.e("PackageInstallReceiver", "APK 靜默安裝失敗 (Status $status, Package: $packageName): $message")
            }
        }
    }
}
