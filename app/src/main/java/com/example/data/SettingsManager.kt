package com.example.data

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tailcam_settings", Context.MODE_PRIVATE)

    var deviceRoleMode: String // "UNSET", "CAMERA", "VIEWER"
        get() = prefs.getString(KEY_DEVICE_ROLE_MODE, "UNSET") ?: "UNSET"
        set(value) = prefs.edit().putString(KEY_DEVICE_ROLE_MODE, value).apply()

    var serverPort: Int
        get() = prefs.getInt(KEY_PORT, 8080)
        set(value) = prefs.edit().putInt(KEY_PORT, value).apply()

    var cameraDeviceName: String
        get() = prefs.getString(KEY_DEVICE_NAME, android.os.Build.MODEL) ?: "Android Camera"
        set(value) = prefs.edit().putString(KEY_DEVICE_NAME, value).apply()

    var telegramBotToken: String
        get() = prefs.getString(KEY_TG_BOT_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TG_BOT_TOKEN, value).apply()

    var telegramChatId: String
        get() = prefs.getString(KEY_TG_CHAT_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TG_CHAT_ID, value).apply()

    var motionDetectionEnabled: Boolean
        get() = prefs.getBoolean(KEY_MOTION_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_MOTION_ENABLED, value).apply()

    var motionSensitivity: Float // 1.0 (low sensitivity = high diff threshold) to 10.0 (high sensitivity)
        get() = prefs.getFloat(KEY_MOTION_SENSITIVITY, 5.0f)
        set(value) = prefs.edit().putFloat(KEY_MOTION_SENSITIVITY, value).apply()

    var motionCooldownSeconds: Int
        get() = prefs.getInt(KEY_MOTION_COOLDOWN, 30)
        set(value) = prefs.edit().putInt(KEY_MOTION_COOLDOWN, value).apply()

    var nightVisionMode: String // "off", "on", "auto"
        get() = prefs.getString(KEY_NIGHT_VISION, "auto") ?: "auto"
        set(value) = prefs.edit().putString(KEY_NIGHT_VISION, value).apply()

    var autoNightVisionThreshold: Float // Luma value 0..255 below which night vision engages
        get() = prefs.getFloat(KEY_NIGHT_VISION_LUMA, 45.0f)
        set(value) = prefs.edit().putFloat(KEY_NIGHT_VISION_LUMA, value).apply()

    var defaultQuality: Int // JPEG compression 30..90
        get() = prefs.getInt(KEY_DEFAULT_QUALITY, 60)
        set(value) = prefs.edit().putInt(KEY_DEFAULT_QUALITY, value).apply()

    var defaultResolution: String // "1080p", "720p", "480p", "360p"
        get() = prefs.getString(KEY_DEFAULT_RESOLUTION, "720p") ?: "720p"
        set(value) = prefs.edit().putString(KEY_DEFAULT_RESOLUTION, value).apply()

    var operatingMode: String // "monitor" (監看模式) or "detection" (動態偵測模式)
        get() = prefs.getString(KEY_OPERATING_MODE, "monitor") ?: "monitor"
        set(value) = prefs.edit().putString(KEY_OPERATING_MODE, value).apply()

    var playLocalAlarmOnMotion: Boolean
        get() = prefs.getBoolean(KEY_PLAY_ALARM, false)
        set(value) = prefs.edit().putBoolean(KEY_PLAY_ALARM, value).apply()

    var mlKitFilterEnabled: Boolean
        get() = prefs.getBoolean(KEY_MLKIT_FILTER, true)
        set(value) = prefs.edit().putBoolean(KEY_MLKIT_FILTER, value).apply()

    var autoStorageCleanupEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CLEANUP_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CLEANUP_ENABLED, value).apply()

    var storageLimitGB: Float
        get() = prefs.getFloat(KEY_STORAGE_LIMIT_GB, 2.0f)
        set(value) = prefs.edit().putFloat(KEY_STORAGE_LIMIT_GB, value).apply()

    var maxEventCountLimit: Int
        get() = prefs.getInt(KEY_MAX_EVENT_COUNT, 200)
        set(value) = prefs.edit().putInt(KEY_MAX_EVENT_COUNT, value).apply()

    var livePreviewInListEnabled: Boolean
        get() = prefs.getBoolean(KEY_LIVE_PREVIEW_IN_LIST, false)
        set(value) = prefs.edit().putBoolean(KEY_LIVE_PREVIEW_IN_LIST, value).apply()

    companion object {
        private const val KEY_DEVICE_ROLE_MODE = "device_role_mode"
        private const val KEY_PORT = "server_port"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_TG_BOT_TOKEN = "tg_bot_token"
        private const val KEY_TG_CHAT_ID = "tg_chat_id"
        private const val KEY_MOTION_ENABLED = "motion_enabled"
        private const val KEY_MOTION_SENSITIVITY = "motion_sensitivity"
        private const val KEY_MOTION_COOLDOWN = "motion_cooldown"
        private const val KEY_NIGHT_VISION = "night_vision"
        private const val KEY_NIGHT_VISION_LUMA = "night_vision_luma"
        private const val KEY_DEFAULT_QUALITY = "default_quality"
        private const val KEY_DEFAULT_RESOLUTION = "default_resolution"
        private const val KEY_OPERATING_MODE = "operating_mode"
        private const val KEY_PLAY_ALARM = "play_alarm"
        private const val KEY_MLKIT_FILTER = "mlkit_filter"
        private const val KEY_AUTO_CLEANUP_ENABLED = "auto_cleanup_enabled"
        private const val KEY_STORAGE_LIMIT_GB = "storage_limit_gb"
        private const val KEY_MAX_EVENT_COUNT = "max_event_count"
        private const val KEY_LIVE_PREVIEW_IN_LIST = "live_preview_in_list"
    }
}
