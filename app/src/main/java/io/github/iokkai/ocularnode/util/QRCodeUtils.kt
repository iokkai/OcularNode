package io.github.iokkai.ocularnode.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject
import java.net.URLDecoder

data class ScannedCameraInfo(
    val name: String,
    val ipAddress: String,
    val port: Int,
    val deviceSecret: String? = null,
    val deviceId: String? = null,
    val mqttTopic: String? = null,
    val ipv6Address: String? = null
)

object QRCodeUtils {
    fun generateQRCodeBitmap(content: String, sizePx: Int = 512): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Generates standard WebRTC pairing JSON content for camera QR code.
     */
    fun generateWebRtcQrContent(
        name: String,
        ipAddress: String,
        port: Int,
        deviceId: String,
        deviceSecret: String,
        ipv6Address: String? = null
    ): String {
        val json = JSONObject().apply {
            put("name", name)
            put("ip", ipAddress)
            if (!ipv6Address.isNullOrBlank()) {
                put("ipv6", ipv6Address)
            }
            put("port", port)
            put("deviceId", deviceId)
            put("secret", deviceSecret)
            put("protocol", "webrtc_v1")
        }
        return json.toString()
    }

    fun parseScannedQrCode(raw: String): ScannedCameraInfo? {
        val text = raw.trim()
        if (text.isBlank()) return null

        // Case 1: JSON Format {"name":"Living Room","ip":"192.168.1.100","ipv6":"2001:...","port":8080,"secret":"...","deviceId":"..."}
        if (text.startsWith("{") && text.endsWith("}")) {
            try {
                val json = JSONObject(text)
                val name = json.optString("name", "Camera Device")
                val ip = json.optString("ip", json.optString("ipAddress", "")).trim()
                val ipv6 = json.optString("ipv6", "").ifBlank { null }
                val port = json.optInt("port", 8080)
                val secret = json.optString("secret", json.optString("deviceSecret", "")).ifBlank { null }
                val deviceId = json.optString("deviceId", "").ifBlank { null }
                val mqttTopic = json.optString("mqttTopic", "").ifBlank { null }

                if (ip.isNotBlank() || !ipv6.isNullOrBlank() || !deviceId.isNullOrBlank()) {
                    return ScannedCameraInfo(
                        name = name,
                        ipAddress = ip.ifBlank { "0.0.0.0" },
                        port = port,
                        deviceSecret = secret,
                        deviceId = deviceId,
                        mqttTopic = mqttTopic,
                        ipv6Address = ipv6
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Case 2: URL format: http://100.64.1.2:8080?name=...&secret=... or http://[2001:...]:8080/
        var clean = text
        var queryName: String? = null
        var querySecret: String? = null
        var queryDeviceId: String? = null
        var queryIpv6: String? = null

        if (clean.contains("?")) {
            val queryPart = clean.substringAfter("?")
            clean = clean.substringBefore("?")
            queryPart.split("&").forEach { param ->
                if (param.startsWith("name=")) {
                    try {
                        queryName = URLDecoder.decode(param.substringAfter("name="), "UTF-8")
                    } catch (e: Exception) {
                        queryName = param.substringAfter("name=")
                    }
                } else if (param.startsWith("secret=") || param.startsWith("deviceSecret=")) {
                    try {
                        querySecret = URLDecoder.decode(param.substringAfter("="), "UTF-8")
                    } catch (e: Exception) {
                        querySecret = param.substringAfter("=")
                    }
                } else if (param.startsWith("deviceId=")) {
                    try {
                        queryDeviceId = URLDecoder.decode(param.substringAfter("deviceId="), "UTF-8")
                    } catch (e: Exception) {
                        queryDeviceId = param.substringAfter("deviceId=")
                    }
                } else if (param.startsWith("ipv6=")) {
                    try {
                        queryIpv6 = URLDecoder.decode(param.substringAfter("ipv6="), "UTF-8")
                    } catch (e: Exception) {
                        queryIpv6 = param.substringAfter("ipv6=")
                    }
                }
            }
        }

        clean = clean.removePrefix("http://").removePrefix("https://").trimEnd('/')

        var ip = clean
        var port = 8080

        if (clean.startsWith("[")) {
            // IPv6 address with brackets: [2001:db8::1]:8080
            val bracketedIp = clean.substringAfter("[").substringBefore("]")
            val afterBracket = clean.substringAfter("]", "")
            ip = bracketedIp
            if (afterBracket.startsWith(":")) {
                port = afterBracket.substringAfter(":").takeWhile { it.isDigit() }.toIntOrNull() ?: 8080
            }
        } else if (clean.contains(":") && clean.count { it == ':' } == 1) {
            // IPv4 address with port: 192.168.1.100:8080
            ip = clean.substringBefore(":")
            val portStr = clean.substringAfter(":").takeWhile { it.isDigit() }
            port = portStr.toIntOrNull() ?: 8080
        }

        if (ip.isNotBlank()) {
            val name = queryName?.ifBlank { null } ?: "Camera ($ip)"
            val ipv6Addr = queryIpv6 ?: if (ip.contains(":")) ip else null
            return ScannedCameraInfo(
                name = name,
                ipAddress = ip,
                port = port,
                deviceSecret = querySecret?.ifBlank { null },
                deviceId = queryDeviceId?.ifBlank { null },
                ipv6Address = ipv6Addr
            )
        }

        return null
    }
}
