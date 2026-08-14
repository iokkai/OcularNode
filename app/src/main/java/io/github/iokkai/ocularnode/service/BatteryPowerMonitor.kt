package io.github.iokkai.ocularnode.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import io.github.iokkai.ocularnode.R
import io.github.iokkai.ocularnode.data.SettingsManager
import io.github.iokkai.ocularnode.util.TelegramNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages battery level, power connection/disconnection events,
 * battery temperature tracking, and thermal throttling state.
 */
class BatteryPowerMonitor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settingsManager: SettingsManager,
    private val onThermalThrottleStart: () -> Unit
) {
    private var batteryReceiver: BroadcastReceiver? = null
    private var lastIsCharging: Boolean? = null
    private var hasSentLowBatteryAlert: Boolean = false

    private val _isThermalThrottled = MutableStateFlow(false)
    val isThermalThrottled: StateFlow<Boolean> = _isThermalThrottled.asStateFlow()

    private val _batteryTemp = MutableStateFlow(0.0f)
    val batteryTemp: StateFlow<Float> = _batteryTemp.asStateFlow()

    fun register() {
        if (batteryReceiver != null) return

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }

        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val action = intent?.action ?: return

                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else -1

                // Battery Temperature Monitoring & Thermal Throttling
                val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                if (tempTenths > 0) {
                    val tempCelsius = tempTenths / 10.0f
                    _batteryTemp.value = tempCelsius

                    val highTempThreshold = 45.0f
                    val recoveryTempThreshold = 42.0f

                    if (tempCelsius >= highTempThreshold && !_isThermalThrottled.value) {
                        _isThermalThrottled.value = true
                        Log.w("BatteryPowerMonitor", "🔥 Thermal Throttling ACTIVATED! Battery Temp: ${tempCelsius}°C >= ${highTempThreshold}°C")

                        onThermalThrottleStart()

                        // Send alert via Telegram
                        scope.launch(Dispatchers.IO) {
                            TelegramNotifier.sendSystemAlert(
                                botToken = settingsManager.telegramBotToken,
                                chatId = settingsManager.telegramChatId,
                                deviceName = settingsManager.cameraDeviceName,
                                alertTitle = context.getString(R.string.tg_alert_thermal_start_title),
                                alertDetails = context.getString(R.string.tg_alert_thermal_start_desc, tempCelsius, highTempThreshold),
                                context = context
                            )
                        }
                    } else if (tempCelsius <= recoveryTempThreshold && _isThermalThrottled.value) {
                        _isThermalThrottled.value = false
                        Log.i("BatteryPowerMonitor", "🧊 Thermal Throttling DEACTIVATED! Battery Temp: ${tempCelsius}°C <= ${recoveryTempThreshold}°C")

                        // Send recovery alert via Telegram
                        scope.launch(Dispatchers.IO) {
                            TelegramNotifier.sendSystemAlert(
                                botToken = settingsManager.telegramBotToken,
                                chatId = settingsManager.telegramChatId,
                                deviceName = settingsManager.cameraDeviceName,
                                alertTitle = context.getString(R.string.tg_alert_thermal_end_title),
                                alertDetails = context.getString(R.string.tg_alert_thermal_end_desc, tempCelsius, recoveryTempThreshold),
                                context = context
                            )
                        }
                    }
                }

                if (!settingsManager.powerCutAlertEnabled) return

                // Power disconnected (Charging -> Discharging)
                if (action == Intent.ACTION_POWER_DISCONNECTED || (lastIsCharging == true && !isCharging)) {
                    if (lastIsCharging != false) {
                        lastIsCharging = false
                        Log.w("BatteryPowerMonitor", "Power disconnected! Battery: $batteryPct%")
                        scope.launch(Dispatchers.IO) {
                            TelegramNotifier.sendSystemAlert(
                                botToken = settingsManager.telegramBotToken,
                                chatId = settingsManager.telegramChatId,
                                deviceName = settingsManager.cameraDeviceName,
                                alertTitle = context.getString(R.string.tg_alert_power_cut_title),
                                alertDetails = context.getString(R.string.tg_alert_power_cut_desc, batteryPct),
                                context = context
                            )
                        }
                    }
                }

                // Power connected (Discharging -> Charging)
                if (action == Intent.ACTION_POWER_CONNECTED || (lastIsCharging == false && isCharging)) {
                    if (lastIsCharging != true) {
                        lastIsCharging = true
                        hasSentLowBatteryAlert = false
                        Log.i("BatteryPowerMonitor", "Power connected! Battery: $batteryPct%")
                        scope.launch(Dispatchers.IO) {
                            TelegramNotifier.sendSystemAlert(
                                botToken = settingsManager.telegramBotToken,
                                chatId = settingsManager.telegramChatId,
                                deviceName = settingsManager.cameraDeviceName,
                                alertTitle = context.getString(R.string.tg_alert_power_restore_title),
                                alertDetails = context.getString(R.string.tg_alert_power_restore_desc, batteryPct),
                                context = context
                            )
                        }
                    }
                }

                // Low Battery check
                if (batteryPct in 1..settingsManager.lowBatteryAlertThreshold && !isCharging && !hasSentLowBatteryAlert) {
                    hasSentLowBatteryAlert = true
                    Log.w("BatteryPowerMonitor", "Low battery threshold hit! Battery: $batteryPct%")
                    scope.launch(Dispatchers.IO) {
                        TelegramNotifier.sendSystemAlert(
                            botToken = settingsManager.telegramBotToken,
                            chatId = settingsManager.telegramChatId,
                            deviceName = settingsManager.cameraDeviceName,
                            alertTitle = context.getString(R.string.tg_alert_low_battery_title),
                            alertDetails = context.getString(R.string.tg_alert_low_battery_desc, batteryPct, settingsManager.lowBatteryAlertThreshold),
                            context = context
                        )
                    }
                }

                if (batteryPct > settingsManager.lowBatteryAlertThreshold + 5 || isCharging) {
                    hasSentLowBatteryAlert = false
                }

                lastIsCharging = isCharging
            }
        }

        try {
            context.registerReceiver(batteryReceiver, filter)
        } catch (e: Exception) {
            Log.e("BatteryPowerMonitor", "Failed to register battery receiver", e)
        }
    }

    fun unregister() {
        batteryReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Exception) {}
            batteryReceiver = null
        }
    }
}
