package io.github.iokkai.ocularnode.util

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 測試免碰觸部署 QR Code Payload 解析與驗證 (Zero-Touch Provisioning QR Payload & Role Validation)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ZeroTouchProvisionTest {

    data class ProvisionConfig(
        val ssid: String,
        val wifiPass: String,
        val mqttDeviceSecret: String,
        val nodeName: String,
        val role: String
    ) {
        fun toJson(): String {
            return JSONObject().apply {
                put("ssid", ssid)
                put("password", wifiPass)
                put("mqtt_device_secret", mqttDeviceSecret)
                put("node_name", nodeName)
                put("role", role)
            }.toString()
        }

        companion object {
            fun fromJson(jsonStr: String): ProvisionConfig? {
                return try {
                    val json = JSONObject(jsonStr)
                    val ssid = json.optString("ssid", "").trim()
                    val pass = json.optString("password", "").trim()
                    val secret = json.optString("mqtt_device_secret", "").trim()
                    val name = json.optString("node_name", "OcularNode").trim()
                    val role = json.optString("role", "camera").trim()

                    if (ssid.isBlank()) null
                    else ProvisionConfig(ssid, pass, secret, name, role)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    @Test
    fun `parses valid provisioning payload successfully`() {
        val config = ProvisionConfig(
            ssid = "MyHomeWiFi",
            wifiPass = "SecurePass123",
            mqttDeviceSecret = "e2ee_secret_abcdef123456",
            nodeName = "Balcony-Cam",
            role = "camera"
        )
        val jsonString = config.toJson()

        val parsed = ProvisionConfig.fromJson(jsonString)
        assertTrue(parsed != null)
        assertEquals("MyHomeWiFi", parsed?.ssid)
        assertEquals("SecurePass123", parsed?.wifiPass)
        assertEquals("e2ee_secret_abcdef123456", parsed?.mqttDeviceSecret)
        assertEquals("Balcony-Cam", parsed?.nodeName)
        assertEquals("camera", parsed?.role)
    }

    @Test
    fun `rejects invalid or empty ssid payload safely`() {
        val invalidJson = "{\"ssid\":\"\", \"password\":\"123\"}"
        val parsed = ProvisionConfig.fromJson(invalidJson)
        assertFalse(parsed != null)

        val malformedJson = "{not_a_valid_json}"
        val parsedMalformed = ProvisionConfig.fromJson(malformedJson)
        assertFalse(parsedMalformed != null)
    }
}
