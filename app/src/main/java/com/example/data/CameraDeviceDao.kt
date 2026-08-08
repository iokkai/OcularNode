package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CameraDeviceDao {
    @Query("SELECT * FROM camera_devices ORDER BY isDefault DESC, name ASC")
    fun getAllCameras(): Flow<List<CameraDevice>>

    @Query("SELECT * FROM camera_devices")
    suspend fun getCamerasListOnce(): List<CameraDevice>

    @Query("SELECT * FROM camera_devices WHERE id = :id")
    suspend fun getCameraById(id: Long): CameraDevice?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCamera(camera: CameraDevice): Long

    @Update
    suspend fun updateCamera(camera: CameraDevice)

    @Delete
    suspend fun deleteCamera(camera: CameraDevice)

    @Query("UPDATE camera_devices SET isDefault = 0")
    suspend fun clearDefaultStatus()

    @Query("UPDATE camera_devices SET isOnline = :isOnline, batteryLevel = :battery, lastOnlineTimestamp = :timestamp WHERE id = :id")
    suspend fun updateCameraStatus(id: Long, isOnline: Boolean, battery: Int, timestamp: Long)
}
