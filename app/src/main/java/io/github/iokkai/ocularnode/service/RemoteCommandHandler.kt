package io.github.iokkai.ocularnode.service

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.lifecycle.LifecycleOwner
import io.github.iokkai.ocularnode.camera.CameraManagerHelper
import io.github.iokkai.ocularnode.data.NotificationCategory
import io.github.iokkai.ocularnode.data.SettingsDataStore
import io.github.iokkai.ocularnode.data.SettingsManager
import io.github.iokkai.ocularnode.server.MjpegHttpServer
import io.github.iokkai.ocularnode.util.AppLogger
import io.github.iokkai.ocularnode.util.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Handles individual remote control commands and batch JSON configuration updates
 * received via HTTP API / Web Dashboard.
 */
class RemoteCommandHandler(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settingsManager: SettingsManager,
    private val cameraHelper: CameraManagerHelper,
    private val lifecycleOwner: LifecycleOwner? = context as? LifecycleOwner,
    private val httpServerGetter: () -> MjpegHttpServer?,
    private val updateOperatingMode: (String) -> Unit,
    private val playAlarmSound: () -> Unit
) {

    fun handleRemoteControl(command: String, value: String) {
        scope.launch(Dispatchers.Main) {
            when (command.lowercase()) {
                "mode", "operating_mode" -> {
                    val targetMode = if (value.lowercase().contains("det")) "detection" else "monitor"
                    updateOperatingMode(targetMode)
                }
                "torch" -> {
                    val enable = value.lowercase() == "on" || value == "true"
                    cameraHelper.setTorch(enable)
                }
                "camera" -> {
                    cameraHelper.switchCamera(lifecycleOwner)
                }
                "quality" -> {
                    val q = value.toIntOrNull() ?: 60
                    cameraHelper.jpegQuality = q
                    settingsManager.defaultQuality = q
                }
                "resolution" -> {
                    cameraHelper.setResolution(value, lifecycleOwner)
                    settingsManager.defaultResolution = value
                }
                "fps", "target_fps", "fps_limit" -> {
                    val fpsVal = value.toIntOrNull() ?: 15
                    cameraHelper.targetFps = fpsVal
                    cameraHelper.dynamicFpsAdjustmentEnabled = true
                    Log.i("RemoteCommandHandler", "Remote updated target FPS to $fpsVal")
                }
                "rotation", "stream_rotation" -> {
                    val trimmedVal = value.trim()
                    val currentRot = settingsManager.streamRotation
                    val newRot = when (trimmedVal) {
                        "1", "+1", "cw" -> (currentRot + 90) % 360
                        "-1", "ccw" -> (currentRot - 90 + 360) % 360
                        else -> {
                            val rot = trimmedVal.toIntOrNull() ?: 0
                            ((rot % 360) + 360) % 360
                        }
                    }
                    settingsManager.streamRotation = newRot
                }
                "night_vision" -> {
                    cameraHelper.nightVisionMode = value
                    settingsManager.nightVisionMode = value
                }
                "motion" -> {
                    val enable = value.lowercase() == "true" || value == "on"
                    cameraHelper.isMotionDetectionEnabled = enable
                    settingsManager.motionDetectionEnabled = enable
                }
                "sensitivity" -> {
                    val sens = value.toFloatOrNull() ?: 5.0f
                    cameraHelper.motionSensitivity = sens
                    settingsManager.motionSensitivity = sens
                }
                "cooldown" -> {
                    val cd = value.toIntOrNull() ?: 30
                    cameraHelper.motionCooldownSeconds = cd
                    settingsManager.motionCooldownSeconds = cd
                }
                "night_vision_luma" -> {
                    val luma = value.toFloatOrNull() ?: 45.0f
                    cameraHelper.autoNightVisionThreshold = luma
                    settingsManager.autoNightVisionThreshold = luma
                }
                "night_vision_hysteresis" -> {
                    val hyst = value.toFloatOrNull() ?: 8.0f
                    cameraHelper.autoNightVisionHysteresis = hyst
                    settingsManager.autoNightVisionHysteresis = hyst
                }
                "play_alarm_setting", "play_local_alarm" -> {
                    val enable = value.lowercase() == "true" || value == "on"
                    settingsManager.playLocalAlarmOnMotion = enable
                }
                "mlkit_filter" -> {
                    val enable = value.lowercase() == "true" || value == "on"
                    settingsManager.mlKitFilterEnabled = enable
                }
                "auto_cleanup" -> {
                    val enable = value.lowercase() == "true" || value == "on"
                    settingsManager.autoStorageCleanupEnabled = enable
                }
                "storage_limit_gb" -> {
                    val limit = value.toFloatOrNull() ?: 2.0f
                    settingsManager.storageLimitGB = limit
                }
                "max_event_count" -> {
                    val maxCount = value.toIntOrNull() ?: 200
                    settingsManager.maxEventCountLimit = maxCount
                }
                "device_name", "rename" -> {
                    if (value.isNotBlank()) {
                        settingsManager.cameraDeviceName = value.trim()
                        httpServerGetter()?.let { it.deviceName = value.trim() }
                    }
                }
                "alarm" -> {
                    playAlarmSound()
                }
                "telegram_token" -> {
                    settingsManager.telegramBotToken = value
                    Log.i("RemoteCommandHandler", "Remote updated Telegram Bot Token")
                }
                "telegram_chatid" -> {
                    settingsManager.telegramChatId = value
                    Log.i("RemoteCommandHandler", "Remote updated Telegram Chat ID")
                }
                "telegram_media_type" -> {
                    settingsManager.telegramSendMediaType = value
                    Log.i("RemoteCommandHandler", "Remote updated Telegram Media Type: $value")
                }
                "telegram_config" -> {
                    try {
                        val json = JSONObject(value)
                        val token = json.optString("token", "")
                        val chatId = json.optString("chatId", "")
                        val mediaType = json.optString("mediaType", "")
                        if (token.isNotBlank()) settingsManager.telegramBotToken = token
                        if (chatId.isNotBlank()) settingsManager.telegramChatId = chatId
                        if (mediaType.isNotBlank()) settingsManager.telegramSendMediaType = mediaType
                        Log.i("RemoteCommandHandler", "Remote updated Telegram config: token length=${token.length}, chatId=$chatId, mediaType=$mediaType")
                    } catch (e: Exception) {
                        Log.e("RemoteCommandHandler", "Error parsing telegram_config JSON", e)
                    }
                }
                "motion_schedule", "motion_schedule_enabled" -> {
                    val enable = value.lowercase() == "true" || value == "on"
                    settingsManager.motionScheduleEnabled = enable
                }
                "motion_schedule_start" -> {
                    if (value.isNotBlank()) settingsManager.motionScheduleStartTime = value
                }
                "motion_schedule_end" -> {
                    if (value.isNotBlank()) settingsManager.motionScheduleEndTime = value
                }
                "notification_schedule", "notification_schedule_enabled" -> {
                    val enable = value.lowercase() == "true" || value == "on"
                    settingsManager.notificationScheduleEnabled = enable
                }
                "notification_schedule_start" -> {
                    if (value.isNotBlank()) settingsManager.notificationScheduleStartTime = value
                }
                "auto_start_boot" -> {
                    val enable = value.lowercase() == "true" || value == "on"
                    settingsManager.autoStartOnBoot = enable
                    Log.i("RemoteCommandHandler", "Remote updated autoStartOnBoot to $enable")
                }
                "power_cut_alert" -> {
                    val enable = value.lowercase() == "true" || value == "on"
                    settingsManager.powerCutAlertEnabled = enable
                    Log.i("RemoteCommandHandler", "Remote updated powerCutAlertEnabled to $enable")
                }
                "notification_schedule_end" -> {
                    if (value.isNotBlank()) settingsManager.notificationScheduleEndTime = value
                }
                "system_log_enabled" -> {
                    val enable = value.lowercase() == "true" || value == "on"
                    settingsManager.systemLogEnabled = enable
                    AppLogger.isEnabled = enable
                }
                "cat_record_toggle" -> {
                    try {
                        val json = JSONObject(value)
                        val catName = json.optString("category", "")
                        val enabled = json.optBoolean("enabled", true)
                        val category = NotificationCategory.values().find { it.name == catName }
                        if (category != null) {
                            scope.launch(Dispatchers.IO) {
                                SettingsDataStore(context).setCategoryRecordingEnabled(category, enabled)
                            }
                        }
                    } catch (e: Exception) {}
                }
                "category_toggle", "cat_toggle" -> {
                    try {
                        val json = JSONObject(value)
                        val catName = json.optString("category", "")
                        val enabled = json.optBoolean("enabled", true)
                        val category = NotificationCategory.values().find { it.name == catName }
                        if (category != null) {
                            scope.launch(Dispatchers.IO) {
                                SettingsDataStore(context).setCategoryEnabled(category, enabled)
                            }
                            Log.i("RemoteCommandHandler", "Remote updated category ${category.name} to $enabled")
                        }
                    } catch (e: Exception) {
                        Log.e("RemoteCommandHandler", "Error parsing category_toggle JSON", e)
                    }
                }
                "update", "silent_update", "check_update", "ota" -> {
                    Log.i("RemoteCommandHandler", "收到遠端更新指令，開始執行 GitHub Releases 靜默更新...")
                    scope.launch(Dispatchers.IO) {
                        try {
                            UpdateManager.checkAndSilentUpdate(context)
                        } catch (e: Exception) {
                            Log.e("RemoteCommandHandler", "遠端執行靜默更新失敗", e)
                        }
                    }
                }
                else -> {
                    if (command.startsWith("cat_")) {
                        val catName = command.removePrefix("cat_")
                        val category = NotificationCategory.values().find { it.name == catName }
                        val enable = value.lowercase() == "true" || value == "on"
                        if (category != null) {
                            scope.launch(Dispatchers.IO) {
                                SettingsDataStore(context).setCategoryEnabled(category, enable)
                            }
                            Log.i("RemoteCommandHandler", "Remote updated category ${category.name} to $enable")
                        }
                    }
                }
            }
        }
    }

    fun handleBatchConfigUpdate(jsonStr: String) {
        scope.launch(Dispatchers.Main) {
            try {
                val json = JSONObject(jsonStr)

                // 1. device section
                if (json.has("device")) {
                    val deviceObj = json.optJSONObject("device")
                    if (deviceObj != null) {
                        val name = deviceObj.optString("deviceName", "")
                        if (name.isNotBlank()) {
                            settingsManager.cameraDeviceName = name.trim()
                            httpServerGetter()?.let { it.deviceName = name.trim() }
                        }
                        val opMode = deviceObj.optString("operatingMode", "")
                        if (opMode.isNotBlank()) {
                            updateOperatingMode(opMode)
                        }
                    }
                } else if (json.has("deviceName")) {
                    val name = json.optString("deviceName", "")
                    if (name.isNotBlank()) {
                        settingsManager.cameraDeviceName = name.trim()
                        httpServerGetter()?.let { it.deviceName = name.trim() }
                    }
                }

                // 2. camera section
                if (json.has("camera")) {
                    val camObj = json.optJSONObject("camera")
                    if (camObj != null) {
                        val res = camObj.optString("resolution", "")
                        if (res.isNotBlank() && res != cameraHelper.currentResolutionString) {
                            settingsManager.defaultResolution = res
                            cameraHelper.setResolution(res, lifecycleOwner)
                        }
                        if (camObj.has("rotation") || camObj.has("streamRotation")) {
                            val rotOpt = if (camObj.has("rotation")) camObj.opt("rotation") else camObj.opt("streamRotation")
                            val rotStr = rotOpt?.toString()?.trim() ?: "0"
                            val currentRot = settingsManager.streamRotation
                            val newRot = when (rotStr) {
                                "1", "+1", "cw" -> (currentRot + 90) % 360
                                "-1", "ccw" -> (currentRot - 90 + 360) % 360
                                else -> {
                                    val rot = rotStr.toIntOrNull() ?: 0
                                    ((rot % 360) + 360) % 360
                                }
                            }
                            settingsManager.streamRotation = newRot
                        }
                        val quality = camObj.optInt("quality", -1)
                        if (quality in 10..100) {
                            settingsManager.defaultQuality = quality
                            cameraHelper.jpegQuality = quality
                        }
                        val nvMode = camObj.optString("nightVisionMode", "")
                        if (nvMode.isNotBlank()) {
                            settingsManager.nightVisionMode = nvMode
                            cameraHelper.nightVisionMode = nvMode
                        }
                        if (camObj.has("isTorchOn")) {
                            val torch = camObj.optBoolean("isTorchOn", false)
                            cameraHelper.setTorch(torch)
                        }
                        val lens = camObj.optString("lensFacing", "")
                        if (lens.isNotBlank()) {
                            val targetLens = if (lens.lowercase() == "front") CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                            if (targetLens != cameraHelper.lensFacing) {
                                cameraHelper.switchCamera(lifecycleOwner)
                            }
                        }
                    }
                }

                // 3. motionDetection section
                if (json.has("motionDetection")) {
                    val mdObj = json.optJSONObject("motionDetection")
                    if (mdObj != null) {
                        if (mdObj.has("enabled")) {
                            val enabled = mdObj.optBoolean("enabled", true)
                            settingsManager.motionDetectionEnabled = enabled
                            cameraHelper.isMotionDetectionEnabled = enabled
                        }
                        if (mdObj.has("scheduleEnabled") || mdObj.has("motionScheduleEnabled")) {
                            settingsManager.motionScheduleEnabled = mdObj.optBoolean("scheduleEnabled", mdObj.optBoolean("motionScheduleEnabled", false))
                        }
                        if (mdObj.has("scheduleStart") || mdObj.has("motionScheduleStart")) {
                            val start = mdObj.optString("scheduleStart", mdObj.optString("motionScheduleStart", "22:00"))
                            if (start.isNotBlank()) settingsManager.motionScheduleStartTime = start
                        }
                        if (mdObj.has("scheduleEnd") || mdObj.has("motionScheduleEnd")) {
                            val end = mdObj.optString("scheduleEnd", mdObj.optString("motionScheduleEnd", "06:00"))
                            if (end.isNotBlank()) settingsManager.motionScheduleEndTime = end
                        }
                        if (mdObj.has("sensitivity")) {
                            val sens = mdObj.optDouble("sensitivity", 5.0).toFloat()
                            settingsManager.motionSensitivity = sens
                            cameraHelper.motionSensitivity = sens
                        }
                        if (mdObj.has("cooldownSeconds")) {
                            val cd = mdObj.optInt("cooldownSeconds", 30)
                            settingsManager.motionCooldownSeconds = cd
                            cameraHelper.motionCooldownSeconds = cd
                        }
                        if (mdObj.has("categories")) {
                            val catObj = mdObj.optJSONObject("categories")
                            if (catObj != null) {
                                val dataStore = SettingsDataStore(context)
                                scope.launch(Dispatchers.IO) {
                                    for (cat in NotificationCategory.values()) {
                                        if (catObj.has(cat.name)) {
                                            val catEnable = catObj.optBoolean(cat.name, true)
                                            dataStore.setCategoryEnabled(cat, catEnable)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. recording section
                if (json.has("recording")) {
                    val recObj = json.optJSONObject("recording")
                    if (recObj != null) {
                        if (recObj.has("eventRecordingEnabled")) {
                            val enabled = recObj.optBoolean("eventRecordingEnabled", true)
                            settingsManager.eventVideoRecordingEnabled = enabled
                        }
                        if (recObj.has("maxStorageGb")) {
                            val gb = recObj.optDouble("maxStorageGb", 2.0).toFloat()
                            settingsManager.storageLimitGB = gb
                        }
                        if (recObj.has("categoryRecording")) {
                            val catRecObj = recObj.optJSONObject("categoryRecording")
                            if (catRecObj != null) {
                                val dataStore = SettingsDataStore(context)
                                scope.launch(Dispatchers.IO) {
                                    for (cat in NotificationCategory.values()) {
                                        if (catRecObj.has(cat.name)) {
                                            val catRecEnable = catRecObj.optBoolean(cat.name, true)
                                            dataStore.setCategoryRecordingEnabled(cat, catRecEnable)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. notifications section
                if (json.has("notifications")) {
                    val notifObj = json.optJSONObject("notifications")
                    if (notifObj != null) {
                        if (notifObj.has("powerCutAlertEnabled")) {
                            settingsManager.powerCutAlertEnabled = notifObj.optBoolean("powerCutAlertEnabled", true)
                        }
                        if (notifObj.has("systemLogEnabled")) {
                            settingsManager.systemLogEnabled = notifObj.optBoolean("systemLogEnabled", true)
                        }
                        if (notifObj.has("scheduleEnabled") || notifObj.has("notificationScheduleEnabled")) {
                            settingsManager.notificationScheduleEnabled = notifObj.optBoolean("scheduleEnabled", notifObj.optBoolean("notificationScheduleEnabled", false))
                        }
                        if (notifObj.has("scheduleStart") || notifObj.has("notificationScheduleStart")) {
                            val start = notifObj.optString("scheduleStart", notifObj.optString("notificationScheduleStart", "22:00"))
                            if (start.isNotBlank()) settingsManager.notificationScheduleStartTime = start
                        }
                        if (notifObj.has("scheduleEnd") || notifObj.has("notificationScheduleEnd")) {
                            val end = notifObj.optString("scheduleEnd", notifObj.optString("notificationScheduleEnd", "06:00"))
                            if (end.isNotBlank()) settingsManager.notificationScheduleEndTime = end
                        }
                        if (notifObj.has("telegram")) {
                            val tgObj = notifObj.optJSONObject("telegram")
                            if (tgObj != null) {
                                val token = tgObj.optString("botToken", "")
                                val chatId = tgObj.optString("chatId", "")
                                val mediaType = tgObj.optString("mediaType", "")
                                if (token.isNotBlank()) settingsManager.telegramBotToken = token
                                if (chatId.isNotBlank()) settingsManager.telegramChatId = chatId
                                if (mediaType.isNotBlank()) settingsManager.telegramSendMediaType = mediaType
                            }
                        }
                    }
                }

                Log.i("RemoteCommandHandler", "Batch configuration applied successfully")
            } catch (e: Exception) {
                Log.e("RemoteCommandHandler", "Error applying batch configuration", e)
            }
        }
    }
}
