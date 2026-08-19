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
                Log.i("PackageInstallReceiver", "APK 安裝成功！Package: $packageName")
                val settingsManager = SettingsManager.getInstance(context)

                if (packageName == context.packageName || packageName.isBlank() || packageName.contains("ocularnode", ignoreCase = true)) {
                    Log.i("PackageInstallReceiver", "偵測到 OcularNode 自身更新完成，呼叫 AutoWakeAndLaunchManager 喚醒並重啟...")
                    io.github.iokkai.ocularnode.util.AutoWakeAndLaunchManager.wakeAndLaunchApp(
                        context = context,
                        reason = "PackageInstaller Silent Update"
                    )
                } else {
                    val authKey = settingsManager.tailscaleAuthKey
                    ZeroTouchProvisionManager.injectTailscaleRestrictionsAndEnableVpn(context, authKey)
                }
            } else if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                val confirmationIntent = androidx.core.content.IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirmationIntent != null) {
                    confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(confirmationIntent)
                        Log.i("PackageInstallReceiver", "請求使用者確認安裝: $packageName")
                    } catch (e: Exception) {
                        Log.e("PackageInstallReceiver", "無法啟動安裝確認視窗", e)
                    }
                }
            } else {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.e("PackageInstallReceiver", "APK 安裝失敗 (Status $status, Package: $packageName): $message")
            }
        }
    }
}
