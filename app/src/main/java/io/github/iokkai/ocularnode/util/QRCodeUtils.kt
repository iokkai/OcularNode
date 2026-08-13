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
    val port: Int
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

    fun parseScannedQrCode(raw: String): ScannedCameraInfo? {
        val text = raw.trim()
        if (text.isBlank()) return null

        // Case 1: JSON Format {"name":"客廳鏡頭","ip":"100.64.1.2","port":8080}
        if (text.startsWith("{") && text.endsWith("}")) {
            try {
                val json = JSONObject(text)
                val name = json.optString("name", "鏡頭裝置")
                val ip = json.optString("ip", json.optString("ipAddress", "")).trim()
                val port = json.optInt("port", 8080)
                if (ip.isNotBlank()) {
                    return ScannedCameraInfo(name = name, ipAddress = ip, port = port)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Case 2: URL format: http://100.64.1.2:8080?name=... or http://100.64.1.2:8080/
        var clean = text
        var queryName: String? = null

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
                }
            }
        }

        clean = clean.removePrefix("http://").removePrefix("https://").trimEnd('/')

        var ip = clean
        var port = 8080

        if (clean.contains(":")) {
            ip = clean.substringBefore(":")
            val portStr = clean.substringAfter(":").takeWhile { it.isDigit() }
            port = portStr.toIntOrNull() ?: 8080
        }

        if (ip.isNotBlank()) {
            val name = queryName?.ifBlank { null } ?: "鏡頭端 ($ip)"
            return ScannedCameraInfo(name = name, ipAddress = ip, port = port)
        }

        return null
    }
}
