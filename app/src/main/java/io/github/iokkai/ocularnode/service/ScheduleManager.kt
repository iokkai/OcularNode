package io.github.iokkai.ocularnode.service

import android.util.Log
import io.github.iokkai.ocularnode.camera.CameraManagerHelper
import io.github.iokkai.ocularnode.data.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Manages schedule evaluation and periodically synchronizes motion detection state.
 */
class ScheduleManager(
    private val scope: CoroutineScope,
    private val settingsManager: SettingsManager,
    private val cameraHelper: CameraManagerHelper
) {
    private var scheduleJob: Job? = null

    fun start() {
        if (scheduleJob?.isActive == true) return

        scheduleJob = scope.launch {
            while (true) {
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
                delay(60000L) // check every minute
            }
        }
    }

    fun stop() {
        scheduleJob?.cancel()
        scheduleJob = null
    }

    companion object {
        fun isCurrentTimeInSchedule(start: String, end: String): Boolean {
            try {
                val now = Calendar.getInstance()
                val currentH = now.get(Calendar.HOUR_OF_DAY)
                val currentM = now.get(Calendar.MINUTE)
                val currentTotalM = currentH * 60 + currentM

                val startParts = start.split(":")
                val startH = startParts.getOrNull(0)?.toIntOrNull() ?: 22
                val startM = startParts.getOrNull(1)?.toIntOrNull() ?: 0
                val startTotalM = startH * 60 + startM

                val endParts = end.split(":")
                val endH = endParts.getOrNull(0)?.toIntOrNull() ?: 6
                val endM = endParts.getOrNull(1)?.toIntOrNull() ?: 0
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
