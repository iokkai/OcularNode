package io.github.iokkai.ocularnode.service

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Base64
import android.util.Log
import io.github.iokkai.ocularnode.camera.CameraManagerHelper
import io.github.iokkai.ocularnode.camera.EventVideoRecorder
import io.github.iokkai.ocularnode.data.AppDatabase
import io.github.iokkai.ocularnode.data.MotionEvent
import io.github.iokkai.ocularnode.data.NotificationCategory
import io.github.iokkai.ocularnode.data.SettingsDataStore
import io.github.iokkai.ocularnode.data.SettingsManager
import io.github.iokkai.ocularnode.util.MlKitFilterHelper
import io.github.iokkai.ocularnode.util.NetworkUtils
import io.github.iokkai.ocularnode.util.TelegramNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/**
 * Handles the end-to-end motion detection processing pipeline:
 * ML Kit categorization, instant snapshot saving, FIFO disk cleanup,
 * Room DB insertion, video recording triggering, and Telegram alerts.
 */
class MotionPipelineManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settingsManager: SettingsManager,
    private val database: AppDatabase,
    private val cameraHelper: CameraManagerHelper,
    private val eventVideoRecorderGetter: () -> EventVideoRecorder?,
    private val isThermalThrottled: () -> Boolean
) {
    private var recordingTimerJob: Job? = null

    fun cancelActiveRecordingTimer() {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
    }

    fun processMotion(percentage: Float, thumbnailBytes: ByteArray, frameBitmap: Bitmap?) {
        scope.launch(Dispatchers.IO) {
            val timestamp = System.currentTimeMillis()
            val mediaDir = File(context.getExternalFilesDir(null), "media").apply { mkdirs() }

            val dataStore = SettingsDataStore.getInstance(context)
            val enabledCategories = mutableSetOf<NotificationCategory>()
            val enabledRecordingCategories = mutableSetOf<NotificationCategory>()
            for (category in NotificationCategory.entries) {
                if (dataStore.getCategoryEnabled(category).first()) {
                    enabledCategories.add(category)
                }
                if (dataStore.getCategoryRecordingEnabled(category).first()) {
                    enabledRecordingCategories.add(category)
                }
            }

            // Stage 2: Google ML Kit Object Detection / Image Labeling Analysis
            var aiSummary = ""
            var shouldSuppressNotification = false
            var shouldTriggerRecording = true

            if (isThermalThrottled()) {
                aiSummary = "🔥 [Thermal Throttling] AI analysis paused"
                Log.w("MotionPipelineManager", "Thermal Throttling Active: Paused ML Kit AI analysis to prevent device overheating.")
            } else if (settingsManager.mlKitFilterEnabled && frameBitmap != null) {
                val mlResult = MlKitFilterHelper.analyzeFrame(context, frameBitmap, enabledCategories, enabledRecordingCategories)
                aiSummary = mlResult.summaryText
                shouldSuppressNotification = mlResult.shouldSuppressNotification
                shouldTriggerRecording = mlResult.shouldTriggerRecording
            }

            // Save Instant Snapshot File
            var snapshotPath: String? = null
            if (thumbnailBytes.isNotEmpty()) {
                try {
                    val snapshotFile = File(mediaDir, "snapshot_${timestamp}.jpg")
                    snapshotFile.writeBytes(thumbnailBytes)
                    snapshotPath = snapshotFile.absolutePath
                } catch (e: Exception) {
                    Log.e("MotionPipelineManager", "Error saving snapshot file", e)
                }
            }

            // Loop Storage Management & Quota Cleanup (FIFO)
            StorageCleanupManager.performCleanupIfNeeded(
                database = database,
                settingsManager = settingsManager,
                mediaDir = mediaDir,
                filesDir = context.filesDir
            )

            val thumbBase64 = if (thumbnailBytes.isNotEmpty()) {
                android.util.Base64.encodeToString(thumbnailBytes, android.util.Base64.NO_WRAP)
            } else null

            val ipAddresses = NetworkUtils.getIpAddresses(context)
            val event = MotionEvent(
                timestamp = timestamp,
                cameraName = settingsManager.cameraDeviceName,
                cameraIp = ipAddresses.localIp ?: "Unknown",
                motionPercentage = percentage,
                thumbnailBase64 = thumbBase64,
                isRead = false,
                telegramSentSuccess = false,
                aiSummary = aiSummary,
                aiFiltered = shouldSuppressNotification,
                snapshotPath = snapshotPath,
                videoPath = null
            )
            val eventId = database.motionEventDao().insertEvent(event)

            val botToken = settingsManager.telegramBotToken
            val chatId = settingsManager.telegramChatId
            val mediaType = settingsManager.telegramSendMediaType // "photo", "video", or "both"

            val sendVideoAlertIfNeeded: suspend (File) -> Unit = { videoFile ->
                if ((mediaType == "video" || mediaType == "both") &&
                    botToken.isNotBlank() && chatId.isNotBlank() &&
                    !shouldSuppressNotification
                ) {
                    val sent = TelegramNotifier.sendVideoAlert(
                        botToken = botToken,
                        chatId = chatId,
                        deviceName = settingsManager.cameraDeviceName,
                        motionPercentage = percentage,
                        videoFile = videoFile,
                        aiSummary = aiSummary,
                        context = context
                    )
                    if (sent) {
                        Log.i("MotionPipelineManager", "Telegram video alert sent successfully for event $eventId")
                    } else {
                        Log.e("MotionPipelineManager", "Telegram video alert failed for event $eventId")
                    }
                }
            }

            // Video Recording Debounce & Prolonging Logic
            if (!shouldTriggerRecording) {
                Log.i("MotionPipelineManager", "ML Kit Filter: Recording suppressed based on category settings ($aiSummary).")
            } else if (isThermalThrottled()) {
                Log.w("MotionPipelineManager", "Thermal Throttling Active: Video recording paused to prevent device overheating.")
            } else if (settingsManager.eventVideoRecordingEnabled) {
                Log.i("MotionPipelineManager", "Triggering EventVideoRecorder dynamically")
                eventVideoRecorderGetter()?.triggerRecording { savedFile ->
                    scope.launch(Dispatchers.IO) {
                        if (savedFile != null) {
                            Log.i("MotionPipelineManager", "Event video recording saved to ${savedFile.absolutePath} for event $eventId")
                            database.motionEventDao().updateVideoPath(eventId, savedFile.absolutePath)
                            sendVideoAlertIfNeeded(savedFile)
                        } else {
                            Log.e("MotionPipelineManager", "Event video recording failed for event $eventId")
                        }
                    }
                }
            } else {
                synchronized(this@MotionPipelineManager) {
                    if (cameraHelper.isRecordingVideo) {
                        // Re-motion detected during recording -> Prolong timer
                        Log.i("MotionPipelineManager", "Motion re-detected! Resetting 20s recording timer.")
                        recordingTimerJob?.cancel()
                        recordingTimerJob = scope.launch(Dispatchers.IO) {
                            delay(20000L) // 20s
                            Log.i("MotionPipelineManager", "20s motion inactivity reached. Stopping video recording.")
                            cameraHelper.stopRecording()
                        }
                    } else {
                        // Start new video recording
                        val videoFile = File(mediaDir, "video_${timestamp}.mp4")
                        cameraHelper.startRecording(videoFile) { success, videoPath ->
                            scope.launch(Dispatchers.IO) {
                                if (success && videoPath != null) {
                                    Log.i("MotionPipelineManager", "Video recording saved to $videoPath for event $eventId")
                                    database.motionEventDao().updateVideoPath(eventId, videoPath)
                                    sendVideoAlertIfNeeded(File(videoPath))
                                } else {
                                    Log.e("MotionPipelineManager", "Video recording failed for event $eventId")
                                }
                            }
                        }

                        recordingTimerJob?.cancel()
                        recordingTimerJob = scope.launch(Dispatchers.IO) {
                            delay(20000L) // 20s
                            Log.i("MotionPipelineManager", "20s motion inactivity reached. Stopping video recording.")
                            cameraHelper.stopRecording()
                        }
                    }
                }
            }

            if (shouldSuppressNotification) {
                Log.i("MotionPipelineManager", "ML Kit Filter: Notification suppressed based on category settings ($aiSummary).")
                return@launch
            }

            if (settingsManager.notificationScheduleEnabled) {
                val isTimeForNotification = ScheduleManager.isCurrentTimeInSchedule(
                    settingsManager.notificationScheduleStartTime,
                    settingsManager.notificationScheduleEndTime
                )
                if (!isTimeForNotification) {
                    Log.i("MotionPipelineManager", "Notification Schedule: Outside window (${settingsManager.notificationScheduleStartTime} ~ ${settingsManager.notificationScheduleEndTime}). Notification & Alarm suppressed.")
                    return@launch
                }
            }

            if (settingsManager.playLocalAlarmOnMotion) {
                playAlarmSound()
            }

            if (botToken.isNotBlank() && chatId.isNotBlank() && (mediaType == "photo" || mediaType == "both")) {
                val sent = TelegramNotifier.sendMotionAlert(
                    botToken = botToken,
                    chatId = chatId,
                    deviceName = settingsManager.cameraDeviceName,
                    motionPercentage = percentage,
                    photoBytes = thumbnailBytes,
                    aiSummary = aiSummary,
                    context = context
                )
                if (sent) {
                    database.motionEventDao().updateTelegramSentSuccess(eventId)
                }
            }
        }
    }

    fun playAlarmSound() {
        try {
            val toneG = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            toneG.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 800)
            scope.launch(Dispatchers.Default) {
                delay(1000L)
                try {
                    toneG.release()
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e("MotionPipelineManager", "Error playing alarm sound", e)
        }
    }
}
