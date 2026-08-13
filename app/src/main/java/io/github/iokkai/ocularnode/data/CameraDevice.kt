package io.github.iokkai.ocularnode.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "camera_devices")
data class CameraDevice(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val ipAddress: String, // e.g., "100.110.120.130" (Tailscale IP)
    val port: Int = 8080,
    val isDefault: Boolean = false,
    val lastOnlineTimestamp: Long = 0L,
    val batteryLevel: Int = -1,
    val isOnline: Boolean = false,
    val modelInfo: String = ""
) {
    fun getBaseUrl(): String = "http://$ipAddress:$port"
    fun getMjpegUrl(): String = "${getBaseUrl()}/mjpeg"
    fun getStatusUrl(): String = "${getBaseUrl()}/status"
    fun getControlUrl(): String = "${getBaseUrl()}/control"
    fun getAudioUrl(): String = "${getBaseUrl()}/audio"
    fun getSpeakUrl(): String = "${getBaseUrl()}/speak"
}
