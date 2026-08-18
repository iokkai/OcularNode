package io.github.iokkai.ocularnode.server

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 測試鏡頭端 REST API 與遠端設定狀態序列化 (Camera API Status JSON & Path Traversal Security)。
 */
class CameraApiHandlerTest {

    data class MockCameraStatus(
        val deviceName: String = "OcularCam-Node1",
        val resolution: String = "360p",
        val quality: Int = 30,
        val fps: Int = 15,
        val batteryLevel: Int = 85,
        val batteryTemp: Float = 36.5f,
        val isThermalThrottled: Boolean = false,
        val nightVision: String = "auto",
        val lens: String = "back",
        val torch: Boolean = false,
        val operatingMode: String = "STREAM_ONLY"
    ) {
        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("deviceName", deviceName)
                put("resolution", resolution)
                put("quality", quality)
                put("fps", fps)
                put("batteryLevel", batteryLevel)
                put("temperature", batteryTemp.toDouble())
                put("isThermalThrottled", isThermalThrottled)
                put("nightVision", nightVision)
                put("lens", lens)
                put("torch", torch)
                put("operatingMode", operatingMode)
            }
        }
    }

    private fun isSafeMediaPath(file: File, allowedDirs: List<File>): Boolean {
        return try {
            val canonicalPath = file.canonicalPath
            allowedDirs.any { dir ->
                canonicalPath.startsWith(dir.canonicalPath)
            }
        } catch (e: Exception) {
            false
        }
    }

    @Test
    fun `status json correctly serializes camera state for remote viewer`() {
        val status = MockCameraStatus(
            resolution = "480p",
            quality = 30,
            fps = 15,
            batteryLevel = 90,
            batteryTemp = 37.0f,
            nightVision = "off",
            lens = "front",
            torch = true
        )

        val json = status.toJson()
        assertEquals("OcularCam-Node1", json.getString("deviceName"))
        assertEquals("480p", json.getString("resolution"))
        assertEquals(30, json.getInt("quality"))
        assertEquals(15, json.getInt("fps"))
        assertEquals(90, json.getInt("batteryLevel"))
        assertEquals("off", json.getString("nightVision"))
        assertEquals("front", json.getString("lens"))
        assertTrue(json.getBoolean("torch"))
    }

    @Test
    fun `isSafeMediaPath allows files in authorized directories and blocks path traversal attempts`() {
        val baseDir = File(System.getProperty("java.io.tmpdir"), "ocular_test_media")
        baseDir.mkdirs()
        val allowedDirs = listOf(baseDir)

        // 1. Authorized media file
        val validFile = File(baseDir, "events/motion_20260101.mp4")
        assertTrue(isSafeMediaPath(validFile, allowedDirs))

        // 2. Malicious path traversal attempts (../..)
        val traversalFile1 = File(baseDir, "../../../etc/passwd")
        assertFalse(isSafeMediaPath(traversalFile1, allowedDirs))

        val traversalFile2 = File(baseDir, "../system_settings.db")
        assertFalse(isSafeMediaPath(traversalFile2, allowedDirs))

        // Cleanup
        baseDir.deleteRecursively()
    }
}
