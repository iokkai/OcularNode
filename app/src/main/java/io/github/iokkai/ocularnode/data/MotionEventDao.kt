package io.github.iokkai.ocularnode.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MotionEventDao {
    @Query("SELECT * FROM motion_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<MotionEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: MotionEvent): Long

    @Query("DELETE FROM motion_events WHERE id = :id")
    suspend fun deleteEventById(id: Long)

    @Query("DELETE FROM motion_events")
    suspend fun clearAllEvents()

    @Query("SELECT COUNT(*) FROM motion_events WHERE isRead = 0")
    fun getUnreadCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM motion_events")
    suspend fun getEventCount(): Int

    @Query("DELETE FROM motion_events WHERE id IN (SELECT id FROM motion_events ORDER BY timestamp ASC LIMIT :count)")
    suspend fun deleteOldestEvents(count: Int)

    @Query("SELECT * FROM motion_events ORDER BY timestamp ASC LIMIT :count")
    suspend fun getOldestEvents(count: Int): List<MotionEvent>

    @Query("UPDATE motion_events SET videoPath = :videoPath WHERE id = :id")
    suspend fun updateVideoPath(id: Long, videoPath: String?)

    @Query("UPDATE motion_events SET snapshotPath = :snapshotPath WHERE id = :id")
    suspend fun updateSnapshotPath(id: Long, snapshotPath: String?)

    @Query("SELECT * FROM motion_events ORDER BY timestamp DESC")
    suspend fun getEventsListOnce(): List<MotionEvent>

    @Query("SELECT * FROM motion_events WHERE id = :id")
    suspend fun getEventById(id: Long): MotionEvent?

    @Query("UPDATE motion_events SET isRead = 1")
    suspend fun markAllAsRead()
}
