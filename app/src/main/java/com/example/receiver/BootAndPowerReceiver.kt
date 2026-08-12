package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.SettingsManager
import com.example.service.CameraStreamService
import com.example.util.TelegramNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootAndPowerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i("BootAndPowerReceiver", "Received broadcast action: $action")
        val settingsManager = SettingsManager(context)

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                if (settingsManager.autoStartOnBoot && settingsManager.deviceRoleMode != "VIEWER") {
                    Log.i("BootAndPowerReceiver", "Auto-starting CameraStreamService after boot...")
                    try {
                        val serviceIntent = Intent(context, CameraStreamService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            ContextCompat.startForegroundService(context, serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }

                        // Send Telegram Boot Notification
                        if (settingsManager.telegramBotToken.isNotBlank() && settingsManager.telegramChatId.isNotBlank() && settingsManager.telegramChatId.isNotBlank()) {
                            CoroutineScope(Dispatchers.IO).launch {
                                TelegramNotifier.sendSystemAlert(
                                    botToken = settingsManager.telegramBotToken,
                                    chatId = settingsManager.telegramChatId,
                                    deviceName = settingsManager.cameraDeviceName,
                                    alertTitle = "⚡ *【開機/復電自動啟動通知】*",
                                    alertDetails = "系統復電/開機完成，已自動重啟「相機監控節點」服務！"
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("BootAndPowerReceiver", "Failed to auto-start service after boot", e)
                    }
                }
            }

            Intent.ACTION_POWER_DISCONNECTED -> {
                if (settingsManager.powerCutAlertEnabled && settingsManager.deviceRoleMode != "VIEWER") {
                    val batteryPct = getBatteryPercentage(context)
                    Log.w("BootAndPowerReceiver", "Power cut detected! Disconnected from charger. Battery: $batteryPct%")
                    if (settingsManager.telegramBotToken.isNotBlank() && settingsManager.telegramChatId.isNotBlank()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            TelegramNotifier.sendSystemAlert(
                                botToken = settingsManager.telegramBotToken,
                                chatId = settingsManager.telegramChatId,
                                deviceName = settingsManager.cameraDeviceName,
                                alertTitle = "🚨 *【外部電源斷電警報】*",
                                alertDetails = "偵測到由「充電中」切換為「放電中」！家中可能發生停電或線路鬆脫。當前剩餘電量: $batteryPct%"
                            )
                        }
                    }
                }
            }

            Intent.ACTION_POWER_CONNECTED -> {
                if (settingsManager.powerCutAlertEnabled && settingsManager.deviceRoleMode != "VIEWER") {
                    val batteryPct = getBatteryPercentage(context)
                    Log.i("BootAndPowerReceiver", "Power restored! Connected to charger. Battery: $batteryPct%")
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
