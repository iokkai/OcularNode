package io.github.iokkai.ocularnode.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "motion_events",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["isRead"]),
        Index(value = ["cameraIp"])
    ]
)
data class MotionEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val cameraName: String = "Local Camera",
    val cameraIp: String = "127.0.0.1",
    val motionPercentage: Float = 0f,
    val thumbnailBase64: String? = null,
    val isRead: Boolean = false,
    val telegramSentSuccess: Boolean = false,
    val aiSummary: String = "",
    val aiFiltered: Boolean = false,
    val snapshotPath: String? = null,
    val videoPath: String? = null,
    val remoteId: Long? = null
)
