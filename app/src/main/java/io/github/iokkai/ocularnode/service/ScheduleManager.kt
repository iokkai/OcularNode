package io.github.iokkai.ocularnode.service

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.util.Log
import io.github.iokkai.ocularnode.camera.CameraManagerHelper
import io.github.iokkai.ocularnode.data.SettingsManager
import io.github.iokkai.ocularnode.util.TelegramNotifier
import io.github.iokkai.ocularnode.util.ZeroTouchProvisionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Manages schedule evaluation, motion detection state, and scheduled self-healing reboot.
 */
class ScheduleManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settingsManager: SettingsManager,
    private val cameraHelper: CameraManagerHelper
) {
    private var scheduleJob: Job? = null

    fun start() {
        if (scheduleJob?.isActive == true) return

        scheduleJob = scope.launch {
            while (true) {
                // 1. Motion detection schedule
                if (settingsManager.motionScheduleEnabled) {
                    val isTime = isCurrentTimeInSchedule(
                        settingsManager.motionScheduleStartTime,
                        settingsManager.motionScheduleEndTime
                    )
                    // Auto-enable/disable motion detection based on schedule
                    if (cameraHelper.isMotionDetectionEnabled != isTime) {
                        cameraHelper.isMotionDetectionEnabled = isTime
                        settingsManager.motionDetectionEnabled = isTime
                        Log.i("ScheduleManager", "Schedule changed motion detection to: $isTime")
                    }
                }

                // 2. Scheduled Self-Healing Reboot (Problem 7)
                if (settingsManager.scheduledRebootEnabled) {
                    checkAndPerformScheduledReboot()
                }

                delay(60000L) // check every minute
            }
        }
    }

    private fun checkAndPerformScheduledReboot() {
        val now = Calendar.getInstance()
        val currentH = now.get(Calendar.HOUR_OF_DAY)
        val currentM = now.get(Calendar.MINUTE)
        val targetTime = settingsManager.scheduledRebootTime.ifBlank { "04:00" }
        val parts = targetTime.split(":")
        val targetH = parts.getOrNull(0)?.toIntOrNull() ?: 4
        val targetM = parts.getOrNull(1)?.toIntOrNull() ?: 0

        if (currentH == targetH && currentM == targetM) {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            if (settingsManager.lastScheduledRebootDate == todayStr) {
                // Already rebooted today
                return
            }

            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val isDO = dpm?.isDeviceOwnerApp(context.packageName) == true

            if (!isDO) {
                Log.w("ScheduleManager", "Scheduled reboot triggered but app is not Device Owner. Skipping.")
                return
            }

            if (cameraHelper.isRecordingVideo) {
                Log.w("ScheduleManager", "Scheduled reboot postponed: camera is currently recording video.")
                return
            }

            Log.i("ScheduleManager", "⏰ Triggering scheduled self-healing reboot at $targetTime...")
            settingsManager.lastScheduledRebootDate = todayStr

            // Send Telegram alert if configured
            if (settingsManager.telegramBotToken.isNotBlank() && settingsManager.telegramChatId.isNotBlank()) {
                scope.launch(Dispatchers.IO) {
                    try {
                        TelegramNotifier.sendSystemAlert(
                            botToken = settingsManager.telegramBotToken,
                            chatId = settingsManager.telegramChatId,
                            deviceName = settingsManager.cameraDeviceName,
                            alertTitle = "⏰ [系統排程自我淨化重啟]",
                            alertDetails = "執行每日/每週定時記憶體與硬體自癒重開機 ($todayStr $targetTime)",
                            context = context
                        )
                    } catch (e: Exception) {
                        Log.e("ScheduleManager", "Error sending reboot notification", e)
                    }
                }
            }

            val admin = ZeroTouchProvisionManager.getAdminComponent(context)
            try {
                dpm.reboot(admin)
            } catch (e: Exception) {
                Log.e("ScheduleManager", "Failed to execute dpm.reboot", e)
            }
        }
    }

    fun stop() {
        scheduleJob?.cancel()
        scheduleJob = null
    }

    companion object {
        fun isCurrentTimeInSchedule(start: String, end: String, testCalendar: Calendar? = null): Boolean {
            try {
                val now = testCalendar ?: Calendar.getInstance()
                val currentH = now.get(Calendar.HOUR_OF_DAY)
                val currentM = now.get(Calendar.MINUTE)
                val currentTotalM = currentH * 60 + currentM

                val startParts = start.split(":")
                val startH = startParts.getOrNull(0)?.toIntOrNull() ?: return false
                val startM = startParts.getOrNull(1)?.toIntOrNull() ?: return false
                if (startH !in 0..23 || startM !in 0..59) return false
                val startTotalM = startH * 60 + startM

                val endParts = end.split(":")
                val endH = endParts.getOrNull(0)?.toIntOrNull() ?: return false
                val endM = endParts.getOrNull(1)?.toIntOrNull() ?: return false
                if (endH !in 0..23 || endM !in 0..59) return false
                val endTotalM = endH * 60 + endM

                return if (startTotalM < endTotalM) {
                    currentTotalM in startTotalM..endTotalM
                } else {
                    currentTotalM >= startTotalM || currentTotalM <= endTotalM
                }
            } catch (e: Exception) {
                return false
            }
        }
    }
}
