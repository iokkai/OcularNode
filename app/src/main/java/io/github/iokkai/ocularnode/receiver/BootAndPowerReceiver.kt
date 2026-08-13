package io.github.iokkai.ocularnode.receiver

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.iokkai.ocularnode.MainActivity
import io.github.iokkai.ocularnode.data.SettingsManager
import io.github.iokkai.ocularnode.service.CameraStreamService
import io.github.iokkai.ocularnode.util.TelegramNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 系統開機、更新與電源狀態變更廣播接收器 (BootAndPowerReceiver)
 * 負責在開機、LOCKED_BOOT、軟體靜默更新 (MY_PACKAGE_REPLACED) 或 QUICKBOOT 時，
 * 自動檢查 Device Owner 特權與設定，喚醒前景監控服務並將畫面鎖定喚醒至 MainActivity Kiosk 模式。
 */
class BootAndPowerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i("BootAndPowerReceiver", "收到廣播事件 Action: $action")
        val settingsManager = SettingsManager(context)

        // 檢查當前 App 是否具備 Device Owner 特權
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val isDeviceOwner = dpm?.isDeviceOwnerApp(context.packageName) == true

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                Log.i("BootAndPowerReceiver", "偵測到開機/更新完成事件 ($action)，Device Owner 狀態: $isDeviceOwner")

                // 當具備 Device Owner 特權 (代表為專用鏡頭端/Kiosk 模式) 或設定了開機自啟且非僅觀看端模式
                if (isDeviceOwner || (settingsManager.autoStartOnBoot && settingsManager.deviceRoleMode != "VIEWER")) {
                    try {
                        // 1. 立即啟動前景服務 CameraStreamService (相容 Android 8.0+ startForegroundService)
                        val serviceIntent = Intent(context, CameraStreamService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            ContextCompat.startForegroundService(context, serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                        Log.i("BootAndPowerReceiver", "已成功啟動 CameraStreamService 前景服務")

                        // 2. 建立啟動 MainActivity 的 Intent，必須加上 Intent.FLAG_ACTIVITY_NEW_TASK
                        val activityIntent = Intent(context, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        context.startActivity(activityIntent)
                        Log.i("BootAndPowerReceiver", "已成功喚醒並啟動 MainActivity (Kiosk 監控畫面)")

                        // 發送 Telegram 開機/更新自動重啟通知
                        if (settingsManager.telegramBotToken.isNotBlank() && settingsManager.telegramChatId.isNotBlank()) {
                            CoroutineScope(Dispatchers.IO).launch {
                                TelegramNotifier.sendSystemAlert(
                                    botToken = settingsManager.telegramBotToken,
                                    chatId = settingsManager.telegramChatId,
                                    deviceName = settingsManager.cameraDeviceName,
                                    alertTitle = "⚡ *【開機/更新自動重啟通知】*",
                                    alertDetails = "系統收到廣播 ($action)，已自動喚醒「相機監控節點」服務與 Kiosk 監控畫面！"
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("BootAndPowerReceiver", "自動啟動服務或畫面失敗", e)
                    }
                } else {
                    Log.i("BootAndPowerReceiver", "非 Device Owner 且未啟用開機自啟，跳過啟動。")
                }
            }

            Intent.ACTION_POWER_DISCONNECTED -> {
                if (settingsManager.powerCutAlertEnabled && settingsManager.deviceRoleMode != "VIEWER") {
                    val batteryPct = getBatteryPercentage(context)
                    Log.w("BootAndPowerReceiver", "電源斷開！當前電量: $batteryPct%")
                    if (settingsManager.telegramBotToken.isNotBlank() && settingsManager.telegramChatId.isNotBlank()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            TelegramNotifier.sendSystemAlert(
                                botToken = settingsManager.telegramBotToken,
                                chatId = settingsManager.telegramChatId,
                                deviceName = settingsManager.cameraDeviceName,
                                alertTitle = "🚨 *【外部電源斷電警報】*",
                                alertDetails = "偵測到充電中斷！家中可能發生停電或線路鬆脫。當前剩餘電量: $batteryPct%"
                            )
                        }
                    }
                }
            }

            Intent.ACTION_POWER_CONNECTED -> {
                if (settingsManager.powerCutAlertEnabled && settingsManager.deviceRoleMode != "VIEWER") {
                    val batteryPct = getBatteryPercentage(context)
                    Log.i("BootAndPowerReceiver", "電源恢復！當前電量: $batteryPct%")
                    if (settingsManager.telegramBotToken.isNotBlank() && settingsManager.telegramChatId.isNotBlank()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            TelegramNotifier.sendSystemAlert(
                                botToken = settingsManager.telegramBotToken,
                                chatId = settingsManager.telegramChatId,
                                deviceName = settingsManager.cameraDeviceName,
                                alertTitle = "🔌 *【外部電源已恢復連接】*",
                                alertDetails = "設備已重新連接外部電源進行充電。當前電量: $batteryPct%"
                            )
                        }
                    }
                }
            }
        }
    }

    private fun getBatteryPercentage(context: Context): Int {
        return try {
            val batteryStatus: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                (level * 100 / scale.toFloat()).toInt()
            } else {
                -1
            }
        } catch (e: Exception) {
            -1
        }
    }
}
