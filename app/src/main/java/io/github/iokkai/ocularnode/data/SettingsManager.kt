@file:Suppress("DEPRECATION") // EncryptedSharedPreferences & MasterKey 在 security-crypto:1.1.0 被標記 deprecated，但目前無替代 API
package io.github.iokkai.ocularnode.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 應用程式設定管理器 (SettingsManager)
 *
 * - 一般設定：使用普通 SharedPreferences（效能優先）
 * - 敏感憑證 (Telegram Bot Token, Chat ID, TURN Password)：
 *   使用 EncryptedSharedPreferences（AES256-SIV + AES256-GCM 加密）
 *
 * 首次建立時會自動執行一次性遷移：將舊版明文 SharedPreferences 中的
 * 敏感欄位搬移至加密存儲，並從明文存儲中移除。
 */
class SettingsManager(context: Context) {

    companion object {
        private const val TAG = "SettingsManager"

        @Volatile
        private var INSTANCE: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        // --- 一般設定 Key ---
        private const val KEY_DEVICE_ROLE_MODE = "device_role_mode"
        private const val KEY_PORT = "server_port"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_TG_SEND_MEDIA_TYPE = "tg_send_media_type"
        private const val KEY_MOTION_ENABLED = "motion_enabled"
        private const val KEY_MOTION_SENSITIVITY = "motion_sensitivity"
        private const val KEY_MOTION_COOLDOWN = "motion_cooldown"
        private const val KEY_NIGHT_VISION = "night_vision"
        private const val KEY_NIGHT_VISION_LUMA = "night_vision_luma"
        private const val KEY_NIGHT_VISION_HYSTERESIS = "night_vision_hysteresis"
        private const val KEY_DEFAULT_QUALITY = "default_quality"
        private const val KEY_STREAM_ROTATION = "stream_rotation"
        private const val KEY_DEFAULT_RESOLUTION = "default_resolution"
        private const val KEY_OPERATING_MODE = "operating_mode"
        private const val KEY_PLAY_ALARM = "play_alarm"
        private const val KEY_MLKIT_FILTER = "mlkit_filter"
        private const val KEY_AUTO_CLEANUP_ENABLED = "auto_cleanup_enabled"
        private const val KEY_STORAGE_LIMIT_GB = "storage_limit_gb"
        private const val KEY_MAX_EVENT_COUNT = "max_event_count"
        private const val KEY_LIVE_PREVIEW_IN_LIST = "live_preview_in_list"
        private const val KEY_MOTION_SCHEDULE_ENABLED = "motion_schedule_enabled"
        private const val KEY_MOTION_SCHEDULE_START = "motion_schedule_start"
        private const val KEY_MOTION_SCHEDULE_END = "motion_schedule_end"
        private const val KEY_NOTIFICATION_SCHEDULE_ENABLED = "notification_schedule_enabled"
        private const val KEY_NOTIFICATION_SCHEDULE_START = "notification_schedule_start"
        private const val KEY_NOTIFICATION_SCHEDULE_END = "notification_schedule_end"
        private const val KEY_AUTO_START_BOOT = "auto_start_boot"
        private const val KEY_POWER_CUT_ALERT = "power_cut_alert"
        private const val KEY_LOW_BATTERY_THRESHOLD = "low_battery_threshold"
        private const val KEY_SYSTEM_LOG_ENABLED = "system_log_enabled"
        private const val KEY_DYNAMIC_FPS_ENABLED = "dynamic_fps_enabled"
        private const val KEY_KIOSK_MODE_ACTIVE = "kiosk_mode_active"
        private const val KEY_HTTP_AUTH_ENABLED = "http_auth_enabled"
        private const val KEY_EVENT_VIDEO_RECORDING_ENABLED = "pref_event_video_recording_enabled"
        private const val KEY_SCHEDULED_REBOOT_ENABLED = "scheduled_reboot_enabled"
        private const val KEY_SCHEDULED_REBOOT_TIME = "scheduled_reboot_time"
        private const val KEY_LAST_SCHEDULED_REBOOT_DATE = "last_scheduled_reboot_date"
        private const val KEY_CUSTOM_TURN_SERVER_URL = "custom_turn_server_url"
        private const val KEY_CUSTOM_TURN_USERNAME = "custom_turn_username"
        private const val KEY_MJPEG_STREAM_ENABLED = "mjpeg_stream_enabled"

        // --- 敏感憑證 Key（存於 EncryptedSharedPreferences）---
        private const val KEY_TG_BOT_TOKEN = "tg_bot_token"
        private const val KEY_TG_CHAT_ID = "tg_chat_id"
        private const val KEY_HTTP_PIN_CODE = "http_pin_code"
        private const val KEY_CUSTOM_TURN_PASSWORD = "custom_turn_password"

        // 遷移標記
        private const val KEY_SECRETS_MIGRATED = "secrets_migrated_to_encrypted"
    }

    /** 一般設定（明文，效能優先） */
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ocularnode_settings", Context.MODE_PRIVATE)

    /** 敏感憑證（AES256 加密） */
    private val secretPrefs: SharedPreferences = createEncryptedPrefs(context)

    init {
        migrateSecretsIfNeeded()
    }

    /**
     * 批次更新一般設定（明文），合併至單次 SharedPreferences.Editor.apply() 提交，避免多重磁碟 I/O。
     */
    fun batchEdit(block: (SharedPreferences.Editor) -> Unit) {
        val editor = prefs.edit()
        block(editor)
        editor.apply()
    }

    /**
     * 批次更新加密憑證，合併至單次 secretPrefs.Editor.apply() 提交。
     */
    fun batchEditSecrets(block: (SharedPreferences.Editor) -> Unit) {
        val editor = secretPrefs.edit()
        block(editor)
        editor.apply()
    }

    /**
     * 建立 EncryptedSharedPreferences 實例。
     * 若加密存儲損毀（極罕見），則刪除損毀檔案並重新建立，避免 App 崩潰。
     * 若處於不支援 Android KeyStore 的環境（如 Robolectric / 單元測試環境），則安全降級為一般 SharedPreferences。
     */
    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            try {
                EncryptedSharedPreferences.create(
                    context,
                    "ocularnode_secrets",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                Log.e(TAG, "EncryptedSharedPreferences corrupted, recreating...", e)
                // 刪除損毀的加密檔案並重新建立
                context.getSharedPreferences("ocularnode_secrets", Context.MODE_PRIVATE).edit().clear().apply()
                try {
                    val file = java.io.File(context.filesDir.parent, "shared_prefs/ocularnode_secrets.xml")
                    if (file.exists()) file.delete()
                } catch (_: Exception) { }
                EncryptedSharedPreferences.create(
                    context,
                    "ocularnode_secrets",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            }
        } catch (e: Throwable) {
            Log.w(TAG, "EncryptedSharedPreferences unavailable (e.g. Test environment or missing KeyStore), falling back to standard SharedPreferences: ${e.message}")
            context.getSharedPreferences("ocularnode_secrets_fallback", Context.MODE_PRIVATE)
        }
    }

    /**
     * 一次性遷移：將舊版明文 SharedPreferences 中的敏感欄位
     * 搬移至 EncryptedSharedPreferences，然後從明文中刪除。
     */
    private fun migrateSecretsIfNeeded() {
        if (prefs.getBoolean(KEY_SECRETS_MIGRATED, false)) return

        val oldToken = prefs.getString(KEY_TG_BOT_TOKEN, null)
        val oldChatId = prefs.getString(KEY_TG_CHAT_ID, null)

        val secretEditor = secretPrefs.edit()
        val plainEditor = prefs.edit()

        if (!oldToken.isNullOrBlank()) {
            secretEditor.putString(KEY_TG_BOT_TOKEN, oldToken)
            plainEditor.remove(KEY_TG_BOT_TOKEN)
        }
        if (!oldChatId.isNullOrBlank()) {
            secretEditor.putString(KEY_TG_CHAT_ID, oldChatId)
            plainEditor.remove(KEY_TG_CHAT_ID)
        }

        secretEditor.apply()
        plainEditor.putBoolean(KEY_SECRETS_MIGRATED, true).apply()
        Log.i(TAG, "Sensitive credentials migrated to EncryptedSharedPreferences")
    }

    // ===== 敏感憑證（加密存儲）=====

    var telegramBotToken: String
        get() = secretPrefs.getString(KEY_TG_BOT_TOKEN, "") ?: ""
        set(value) = secretPrefs.edit().putString(KEY_TG_BOT_TOKEN, value).apply()

    var telegramChatId: String
        get() = secretPrefs.getString(KEY_TG_CHAT_ID, "") ?: ""
        set(value) = secretPrefs.edit().putString(KEY_TG_CHAT_ID, value).apply()

    var customTurnPassword: String
        get() = secretPrefs.getString(KEY_CUSTOM_TURN_PASSWORD, "") ?: ""
        set(value) = secretPrefs.edit().putString(KEY_CUSTOM_TURN_PASSWORD, value).apply()

    // ===== 一般設定（明文存儲）=====

    var customTurnServerUrl: String
        get() = prefs.getString(KEY_CUSTOM_TURN_SERVER_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_TURN_SERVER_URL, value).apply()

    var customTurnUsername: String
        get() = prefs.getString(KEY_CUSTOM_TURN_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_TURN_USERNAME, value).apply()

    var deviceRoleMode: String // "UNSET", "CAMERA", "VIEWER"
        get() = prefs.getString(KEY_DEVICE_ROLE_MODE, "UNSET") ?: "UNSET"
        set(value) = prefs.edit().putString(KEY_DEVICE_ROLE_MODE, value).apply()

    var serverPort: Int
        get() = prefs.getInt(KEY_PORT, 8080)
        set(value) = prefs.edit().putInt(KEY_PORT, value).apply()

    var isMjpegStreamEnabled: Boolean
        get() = prefs.getBoolean(KEY_MJPEG_STREAM_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_MJPEG_STREAM_ENABLED, value).apply()

    var cameraDeviceName: String
        get() = prefs.getString(KEY_DEVICE_NAME, android.os.Build.MODEL) ?: "Android Camera"
        set(value) = prefs.edit().putString(KEY_DEVICE_NAME, value).apply()

    var telegramSendMediaType: String // "photo", "video", or "both"
        get() = prefs.getString(KEY_TG_SEND_MEDIA_TYPE, "photo") ?: "photo"
        set(value) = prefs.edit().putString(KEY_TG_SEND_MEDIA_TYPE, value).apply()

    var motionDetectionEnabled: Boolean
        get() = prefs.getBoolean(KEY_MOTION_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_MOTION_ENABLED, value).apply()

    var motionScheduleEnabled: Boolean
        get() = prefs.getBoolean(KEY_MOTION_SCHEDULE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_MOTION_SCHEDULE_ENABLED, value).apply()

    var motionScheduleStartTime: String // "HH:mm" format
        get() = prefs.getString(KEY_MOTION_SCHEDULE_START, "22:00") ?: "22:00"
        set(value) = prefs.edit().putString(KEY_MOTION_SCHEDULE_START, value).apply()

    var motionScheduleEndTime: String // "HH:mm" format
        get() = prefs.getString(KEY_MOTION_SCHEDULE_END, "06:00") ?: "06:00"
        set(value) = prefs.edit().putString(KEY_MOTION_SCHEDULE_END, value).apply()

    var notificationScheduleEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_SCHEDULE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATION_SCHEDULE_ENABLED, value).apply()

    var notificationScheduleStartTime: String // "HH:mm" format
        get() = prefs.getString(KEY_NOTIFICATION_SCHEDULE_START, "22:00") ?: "22:00"
        set(value) = prefs.edit().putString(KEY_NOTIFICATION_SCHEDULE_START, value).apply()

    var notificationScheduleEndTime: String // "HH:mm" format
        get() = prefs.getString(KEY_NOTIFICATION_SCHEDULE_END, "06:00") ?: "06:00"
        set(value) = prefs.edit().putString(KEY_NOTIFICATION_SCHEDULE_END, value).apply()

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

    var autoNightVisionHysteresis: Float // Hysteresis margin in Luma units to prevent flickering
        get() = prefs.getFloat(KEY_NIGHT_VISION_HYSTERESIS, 8.0f)
        set(value) = prefs.edit().putFloat(KEY_NIGHT_VISION_HYSTERESIS, value).apply()

    var streamRotation: Int
        get() = prefs.getInt(KEY_STREAM_ROTATION, 0)
        set(value) = prefs.edit().putInt(KEY_STREAM_ROTATION, value).apply()

    var defaultQuality: Int // JPEG compression 30..90
        get() = prefs.getInt(KEY_DEFAULT_QUALITY, 30)
        set(value) = prefs.edit().putInt(KEY_DEFAULT_QUALITY, value).apply()

    var defaultResolution: String // "720p", "960p", "480p", "360p", "1080p"
        get() {
            val res = prefs.getString(KEY_DEFAULT_RESOLUTION, "360p") ?: "360p"
            return if (res.equals("Max", ignoreCase = true)) "360p" else res
        }
        set(value) = prefs.edit().putString(KEY_DEFAULT_RESOLUTION, value).apply()

    var operatingMode: String // "monitor" (監看模式) or "detection" (自動偵測模式)
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

    var autoStartOnBoot: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START_BOOT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START_BOOT, value).apply()

    var powerCutAlertEnabled: Boolean
        get() = prefs.getBoolean(KEY_POWER_CUT_ALERT, true)
        set(value) = prefs.edit().putBoolean(KEY_POWER_CUT_ALERT, value).apply()

    var eventVideoRecordingEnabled: Boolean
        get() = prefs.getBoolean(KEY_EVENT_VIDEO_RECORDING_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_EVENT_VIDEO_RECORDING_ENABLED, value).apply()

    var systemLogEnabled: Boolean
        get() = prefs.getBoolean(KEY_SYSTEM_LOG_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SYSTEM_LOG_ENABLED, value).apply()

    var dynamicFpsAdjustmentEnabled: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_FPS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_DYNAMIC_FPS_ENABLED, value).apply()

    var lowBatteryAlertThreshold: Int
        get() = prefs.getInt(KEY_LOW_BATTERY_THRESHOLD, 60)
        set(value) = prefs.edit().putInt(KEY_LOW_BATTERY_THRESHOLD, value).apply()

    var isKioskModeActive: Boolean
        get() = prefs.getBoolean(KEY_KIOSK_MODE_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_KIOSK_MODE_ACTIVE, value).apply()

    var httpAuthEnabled: Boolean
        get() = prefs.getBoolean(KEY_HTTP_AUTH_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_HTTP_AUTH_ENABLED, value).apply()

    var httpPinCode: String
        get() = secretPrefs.getString(KEY_HTTP_PIN_CODE, "1234") ?: "1234"
        set(value) = secretPrefs.edit().putString(KEY_HTTP_PIN_CODE, value).apply()

    var scheduledRebootEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCHEDULED_REBOOT_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SCHEDULED_REBOOT_ENABLED, value).apply()

    var scheduledRebootTime: String
        get() = prefs.getString(KEY_SCHEDULED_REBOOT_TIME, "04:00") ?: "04:00"
        set(value) = prefs.edit().putString(KEY_SCHEDULED_REBOOT_TIME, value).apply()

    var lastScheduledRebootDate: String
        get() = prefs.getString(KEY_LAST_SCHEDULED_REBOOT_DATE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_SCHEDULED_REBOOT_DATE, value).apply()
}
