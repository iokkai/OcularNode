package io.github.iokkai.ocularnode.util

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.iokkai.ocularnode.MainActivity
import io.github.iokkai.ocularnode.R
import io.github.iokkai.ocularnode.service.CameraStreamService

/**
 * 負責在開機、OTA 更新完成或系統重啟時，突破 Android 鎖定畫面與背景 Activity 限制，
 * 強制點亮螢幕並無縫啟動/恢復 MainActivity 與相機前景串流服務。
 */
object AutoWakeAndLaunchManager {

    private const val TAG = "AutoWakeAndLaunch"
    private const val CHANNEL_ID_AUTO_START = "ocularnode_autostart_channel"
    private const val NOTIFICATION_ID_AUTO_START = 9988

    /**
     * 強制喚醒裝置並啟動 MainActivity 與 CameraStreamService。
     *
     * 策略採用多重防護機制：
     * 1. WakeLock 強制點亮螢幕 (ACQUIRE_CAUSES_WAKEUP)
     * 2. Android 14+ PendingIntent 背景啟動授權 (MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
     * 3. 全螢幕意圖通知 (Full-Screen Intent) 穿透螢幕鎖定畫面
     * 4. 備援直接呼叫 startActivity
     * 5. 前景服務備援拉起
     *
     * @param context 應用程式 Context
     * @param reason 啟動原因描述 (例如 "OTA Update Complete", "Boot Completed")
     */
    @SuppressLint("WakelockTimeout")
    fun wakeAndLaunchApp(context: Context, reason: String) {
        Log.i(TAG, "⚡ 觸發強制喚醒與自啟動程序，原因: $reason")

        // 1. 取得 WakeLock 強制喚醒 CPU 並點亮螢幕 (維持 10 秒供 Activity 載入)
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            @Suppress("DEPRECATION")
            val wakeLockFlags = PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE
            val wakeLock = powerManager?.newWakeLock(wakeLockFlags, "ocularnode:autowake_launch_lock")
            wakeLock?.acquire(10_000L)
            Log.i(TAG, "已成功取得 WakeLock (SCREEN_BRIGHT_WAKE_LOCK + ACQUIRE_CAUSES_WAKEUP)")
        } catch (e: Exception) {
            Log.e(TAG, "取得 WakeLock 點亮螢幕失敗", e)
        }

        // 2. 準備 MainActivity 的 Intent
        val activityIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
            putExtra("EXTRA_AUTO_STARTED_BY", reason)
        }

        // 3. 準備 PendingIntent (針對 Android 14+ 啟用背景啟動授權)
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getActivity(
                context,
                NOTIFICATION_ID_AUTO_START,
                activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getActivity(
                context,
                NOTIFICATION_ID_AUTO_START,
                activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        // 4. 嘗試使用 PendingIntent (含 Android 14 背景授權) 直接發送啟動
        var directLaunchSuccess = false
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val options = ActivityOptions.makeBasic()
                @Suppress("DEPRECATION")
                options.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
                pendingIntent.send(context, 0, null, null, null, null, options.toBundle())
            } else {
                pendingIntent.send()
            }
            directLaunchSuccess = true
            Log.i(TAG, "已透過 PendingIntent 派送 Activity 啟動請求")
        } catch (e: Exception) {
            Log.w(TAG, "PendingIntent 直接派送失敗，準備使用備援機制", e)
        }

        // 5. 使用 Full-Screen Intent 高優先級通知穿透鎖定畫面 (Android 10~15 官方標準穿透鎖定手段)
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        CHANNEL_ID_AUTO_START,
                        "OcularNode 自啟動喚醒通知",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "用於在開機或 OTA 更新後自動喚醒並拉起監控畫面"
                        setSound(null, null)
                        enableVibration(false)
                        lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    }
                    notificationManager.createNotificationChannel(channel)
                }

                val notification = NotificationCompat.Builder(context, CHANNEL_ID_AUTO_START)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText("正在自動恢復相機監控畫面...")
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setFullScreenIntent(pendingIntent, true) // 核心：鎖定時系統自動將 Activity 彈出至前景
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setTimeoutAfter(8000L) // 8 秒後自動清除該通知
                    .build()

                notificationManager.notify(NOTIFICATION_ID_AUTO_START, notification)
                Log.i(TAG, "已發送 Full-Screen Intent 高優先級通知穿透螢幕鎖定畫面")
            }
        } catch (e: Exception) {
            Log.e(TAG, "發送 Full-Screen Intent 通知失敗", e)
        }

        // 6. 備援一般 startActivity
        if (!directLaunchSuccess) {
            try {
                context.startActivity(activityIntent)
                Log.i(TAG, "備援 context.startActivity 已執行")
            } catch (e: Exception) {
                Log.e(TAG, "備援 context.startActivity 失敗 (可能受限於背景限制)", e)
            }
        }

        // 7. 啟動前景服務 CameraStreamService 作為背景串流保底
        try {
            val serviceIntent = Intent(context, CameraStreamService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.i(TAG, "已派發 CameraStreamService 啟動請求")
        } catch (e: Exception) {
            Log.w(TAG, "啟動 CameraStreamService 遭遇限制 (若 Activity 已成功拉起，將由 Activity 自動啟動服務)", e)
        }
    }
}
