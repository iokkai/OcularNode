package io.github.iokkai.ocularnode.service

import android.os.StatFs
import android.util.Log
import io.github.iokkai.ocularnode.data.AppDatabase
import io.github.iokkai.ocularnode.data.SettingsManager
import java.io.File

/**
 * Handles FIFO disk storage management and database event cleanup.
 */
object StorageCleanupManager {

    suspend fun performCleanupIfNeeded(
        database: AppDatabase,
        settingsManager: SettingsManager,
        mediaDir: File,
        filesDir: File
    ) {
        if (!settingsManager.autoStorageCleanupEnabled) return

        try {
            val currentCount = database.motionEventDao().getEventCount()
            val maxCount = settingsManager.maxEventCountLimit
            val mediaFiles = mediaDir.listFiles() ?: emptyArray()
            val mediaTotalMB = mediaFiles.sumOf { it.length() } / (1024 * 1024)
            val limitMB = (settingsManager.storageLimitGB * 1024).toLong()

            val statFs = StatFs(filesDir.absolutePath)
            val freeMB = statFs.availableBytes / (1024 * 1024)

            if (currentCount >= maxCount || mediaTotalMB > limitMB || freeMB < 1000) { // Keep min 1GB free
                val purgeCount = (currentCount * 0.2).toInt().coerceAtLeast(5)
                Log.i("StorageCleanupManager", "Quota cleanup active (count=$currentCount, mediaMB=$mediaTotalMB, freeMB=$freeMB). Purging $purgeCount oldest events & media.")
                val oldestEvents = database.motionEventDao().getOldestEvents(purgeCount)
                for (oldEv in oldestEvents) {
                    oldEv.snapshotPath?.let { path -> File(path).delete() }
                    oldEv.videoPath?.let { path -> File(path).delete() }
                }
                database.motionEventDao().deleteOldestEvents(purgeCount)
            }
        } catch (e: Exception) {
            Log.e("StorageCleanupManager", "Error during loop storage cleanup", e)
        }
    }
}
