package io.github.iokkai.ocularnode.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "camera_devices")
data class CameraDevice(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val ipAddress: String, // e.g., "192.168.1.100" (LAN IP)
    val port: Int = 8080,
    val isDefault: Boolean = false,
    val lastOnlineTimestamp: Long = 0L,
    val batteryLevel: Int = -1,
    val isOnline: Boolean = false,
    val modelInfo: String = "",
    val deviceSecret: String? = null,
    val deviceId: String? = null,
    val ipv6Address: String? = null
) {
    fun getBaseUrl(): String = "http://$ipAddress:$port"
    fun getMjpegUrl(): String = "${getBaseUrl()}/mjpeg"
    fun getStatusUrl(): String = "${getBaseUrl()}/status"
    fun getControlUrl(): String = "${getBaseUrl()}/control"
    fun getAudioUrl(): String = "${getBaseUrl()}/audio"
    fun getSpeakUrl(): String = "${getBaseUrl()}/speak"

    /**
     * Generates a decentralized, zero-server Web Viewer URL with credentials stored in URL Hash fragment (#).
     * The Hash fragment is never transmitted to HTTP servers, ensuring 100% client-side privacy.
     */
    fun getWebViewerUrl(baseUrl: String = "https://iokkai.github.io/OcularNode/viewer/"): String {
        val params = mutableListOf<String>()
        if (!deviceId.isNullOrBlank()) {
            params.add("id=" + java.net.URLEncoder.encode(deviceId, "UTF-8"))
        }
        if (!deviceSecret.isNullOrBlank()) {
            params.add("secret=" + java.net.URLEncoder.encode(deviceSecret, "UTF-8"))
        }
        if (name.isNotBlank()) {
            params.add("name=" + java.net.URLEncoder.encode(name, "UTF-8"))
        }
        if (ipAddress.isNotBlank()) {
            params.add("ip=" + java.net.URLEncoder.encode(ipAddress, "UTF-8"))
        }
        if (!ipv6Address.isNullOrBlank()) {
            params.add("ipv6=" + java.net.URLEncoder.encode(ipv6Address, "UTF-8"))
        }
        params.add("port=$port")
        return "$baseUrl#${params.joinToString("&")}"
    }
}
