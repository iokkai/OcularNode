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
import io.github.iokkai.ocularnode.R
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
        val settingsManager = SettingsManager.getInstance(context)

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
                    // 1. 啟動 MainActivity (UI 會在前景啟動服務，避免在背景直接啟動帶有 camera 權限的 FGS 被拒)
                    try {
                        val activityIntent = Intent(context, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            val options = android.app.ActivityOptions.makeBasic()
                            options.setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                            val pi = android.app.PendingIntent.getActivity(
                                context, 0, activityIntent,
                                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                            )
                            pi.send(context, 0, null, null, null, null, options.toBundle())
                        } else {
                            context.startActivity(activityIntent)
                        }
                        Log.i("BootAndPowerReceiver", "已成功喚醒並啟動 MainActivity (Kiosk 監控畫面)")
                    } catch (e: Exception) {
                        Log.e("BootAndPowerReceiver", "Failed to start activity on boot", e)
                    }

                    // 2. 啟動前景服務 CameraStreamService (若上面的 Activity 啟動失敗，這裡作為備援)
                    try {
                        val serviceIntent = Intent(context, CameraStreamService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            ContextCompat.startForegroundService(context, serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                        Log.i("BootAndPowerReceiver", "已成功啟動 CameraStreamService 前景服務")
                    } catch (e: Exception) {
                        Log.e("BootAndPowerReceiver", "Failed to start service on boot (可能因 Android 14+ 背景啟動限制)", e)
                    }

                    try {

                        // 發送 Telegram 開機/更新自動重啟通知
                        if (settingsManager.telegramBotToken.isNotBlank() && settingsManager.telegramChatId.isNotBlank()) {
                            val pendingResult = goAsync()
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    TelegramNotifier.sendSystemAlert(
                                        botToken = settingsManager.telegramBotToken,
                                        chatId = settingsManager.telegramChatId,
                                        deviceName = settingsManager.cameraDeviceName,
                                        alertTitle = context.getString(R.string.tg_alert_reboot_title),
                                        alertDetails = context.getString(R.string.tg_alert_reboot_desc, action ?: ""),
                                        context = context
                                    )
                                } catch (e: Exception) {
                                    Log.e("BootAndPowerReceiver", "Error sending reboot alert", e)
                                } finally {
                                    pendingResult.finish()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("BootAndPowerReceiver", "Failed to start service or activity on boot", e)
                    }
                } else {
                    Log.i("BootAndPowerReceiver", "Not Device Owner or auto-start disabled, skipping.")
                }
            }

            Intent.ACTION_POWER_DISCONNECTED -> {
                // 若前景服務已在執行，由 BatteryPowerMonitor 統一負責（避免重複通知）
                if (CameraStreamService.isServiceRunning) {
                    Log.i("BootAndPowerReceiver", "CameraStreamService 正在執行中，電源斷開事件由 BatteryPowerMonitor 處理，略過 BroadcastReceiver。")
                    return
                }

                if (settingsManager.powerCutAlertEnabled && settingsManager.deviceRoleMode != "VIEWER") {
                    val now = System.currentTimeMillis()
                    if (now - lastPowerCutTime < DEBOUNCE_WINDOW_MS) {
                        Log.i("BootAndPowerReceiver", "電源斷開事件命中防抖視窗 (10s)，略過重複發送。")
                        return
                    }
                    lastPowerCutTime = now

                    val batteryPct = getBatteryPercentage(context)
                    Log.w("BootAndPowerReceiver", "Power disconnected! Battery: $batteryPct%")
                    if (settingsManager.telegramBotToken.isNotBlank() && settingsManager.telegramChatId.isNotBlank()) {
                        val pendingResult = goAsync()
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                TelegramNotifier.sendSystemAlert(
                                    botToken = settingsManager.telegramBotToken,
                                    chatId = settingsManager.telegramChatId,
                                    deviceName = settingsManager.cameraDeviceName,
                                    alertTitle = context.getString(R.string.tg_alert_power_cut_title),
                                    alertDetails = context.getString(R.string.tg_alert_power_cut_desc, batteryPct),
                                    context = context
                                )
                            } catch (e: Exception) {
                                Log.e("BootAndPowerReceiver", "Error sending power cut alert", e)
                            } finally {
                                pendingResult.finish()
                            }
                        }
                    }
                }
            }

            Intent.ACTION_POWER_CONNECTED -> {
                // 若前景服務已在執行，由 BatteryPowerMonitor 統一負責（避免重複通知）
                if (CameraStreamService.isServiceRunning) {
                    Log.i("BootAndPowerReceiver", "CameraStreamService 正在執行中，電源接通事件由 BatteryPowerMonitor 處理，略過 BroadcastReceiver。")
                    return
                }

                if (settingsManager.powerCutAlertEnabled && settingsManager.deviceRoleMode != "VIEWER") {
                    val now = System.currentTimeMillis()
                    if (now - lastPowerRestoreTime < DEBOUNCE_WINDOW_MS) {
                        Log.i("BootAndPowerReceiver", "電源接通事件命中防抖視窗 (10s)，略過重複發送。")
                        return
                    }
                    lastPowerRestoreTime = now

                    val batteryPct = getBatteryPercentage(context)
                    Log.i("BootAndPowerReceiver", "Power restored! Battery: $batteryPct%")
                    if (settingsManager.telegramBotToken.isNotBlank() && settingsManager.telegramChatId.isNotBlank()) {
                        val pendingResult = goAsync()
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                TelegramNotifier.sendSystemAlert(
                                    botToken = settingsManager.telegramBotToken,
                                    chatId = settingsManager.telegramChatId,
                                    deviceName = settingsManager.cameraDeviceName,
                                    alertTitle = context.getString(R.string.tg_alert_power_restore_title),
                                    alertDetails = context.getString(R.string.tg_alert_power_restore_desc, batteryPct),
                                    context = context
                                )
                            } catch (e: Exception) {
                                Log.e("BootAndPowerReceiver", "Error sending power restore alert", e)
                            } finally {
                                pendingResult.finish()
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val DEBOUNCE_WINDOW_MS = 10_000L
        @Volatile private var lastPowerCutTime: Long = 0L
        @Volatile private var lastPowerRestoreTime: Long = 0L
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
