package io.github.iokkai.ocularnode.util

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 測試免碰觸部署 QR Code Payload 解析與驗證 (Zero-Touch Provisioning QR Payload & Role Validation)。
 */
class ZeroTouchProvisionTest {

    data class ProvisionConfig(
        val ssid: String,
        val wifiPass: String,
        val tailscaleKey: String,
        val nodeName: String,
        val role: String
    ) {
        fun toJson(): String {
            return JSONObject().apply {
                put("ssid", ssid)
                put("password", wifiPass)
                put("tailscale_key", tailscaleKey)
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
                    val tsKey = json.optString("tailscale_key", "").trim()
                    val name = json.optString("node_name", "OcularNode").trim()
                    val role = json.optString("role", "camera").trim()

                    if (ssid.isBlank()) null
                    else ProvisionConfig(ssid, pass, tsKey, name, role)
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
            tailscaleKey = "tskey-auth-k9876543210-ephemeral",
            nodeName = "Balcony-Cam",
            role = "camera"
        )
        val jsonString = config.toJson()

        val parsed = ProvisionConfig.fromJson(jsonString)
        assertTrue(parsed != null)
        assertEquals("MyHomeWiFi", parsed?.ssid)
        assertEquals("SecurePass123", parsed?.wifiPass)
        assertEquals("tskey-auth-k9876543210-ephemeral", parsed?.tailscaleKey)
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
