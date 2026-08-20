package io.github.iokkai.ocularnode.util

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

object PowerManagementUtils {
    private const val TAG = "PowerManagement"

    /**
     * 檢查並將 OcularNode (自身 Package) 請求電池最佳化豁免。
     * 在 Device Owner 專用設備模式下直接跳過以維持零接觸無縫體驗。
     */
    fun exemptFromBatteryOptimizations(context: Context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val isDO = dpm?.isDeviceOwnerApp(context.packageName) == true

        // Device Owner 模式下為零接觸專用設備，絕對不要彈出任何系統設定畫面打斷流程
        if (isDO) {
            Log.i(TAG, "Device Owner 專用設備模式：跳過手動電池最佳化設定畫面，由前景服務與 WakeLock 保持常駐")
            return
        }

        val pkg = context.packageName
        try {
            val isIgnoring = powerManager.isIgnoringBatteryOptimizations(pkg)
            Log.i(TAG, "檢查電池最佳化豁免狀態 [$pkg]: isIgnoring=$isIgnoring")

            if (!isIgnoring) {
                // 自身 App (非 DO)：利用 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 專用系統對話框請求加入白名單
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$pkg")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Log.i(TAG, "已發送自身 [$pkg] 電池最佳化豁免請求對話框")
            }
        } catch (e: Exception) {
            Log.e(TAG, "處理自身電池最佳化豁免時發生例外狀況: ${e.message}", e)
        }
    }
}
