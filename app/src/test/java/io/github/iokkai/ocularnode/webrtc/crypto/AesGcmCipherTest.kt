package io.github.iokkai.ocularnode.webrtc.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AesGcmCipherTest {

    @Test
    fun testEncryptionAndDecryption() {
        val secret = "my-secure-camera-secret-12345"
        val originalText = "{\"sdp\":\"v=0...\",\"type\":\"offer\"}"

        val encryptedJson = AesGcmCipher.encrypt(originalText, secret)
        assertNotNull(encryptedJson)
        assertTrue(encryptedJson.contains("iv"))
        assertTrue(encryptedJson.contains("data"))
        assertNotEquals(originalText, encryptedJson)

        val decryptedText = AesGcmCipher.decrypt(encryptedJson, secret)
        assertEquals(originalText, decryptedText)
    }

    @Test(expected = Exception::class)
    fun testDecryptionFailsWithWrongSecret() {
        val secret = "correct-secret"
        val wrongSecret = "wrong-secret"
        val originalText = "Secret WebRTC SDP Payload"

        val encryptedJson = AesGcmCipher.encrypt(originalText, secret)
        AesGcmCipher.decrypt(encryptedJson, wrongSecret)
    }

    @Test
    fun testRandomSecretGeneration() {
        val secret1 = AesGcmCipher.generateRandomDeviceSecret()
        val secret2 = AesGcmCipher.generateRandomDeviceSecret()

        assertEquals(32, secret1.length) // 16 bytes = 32 hex chars
        assertEquals(32, secret2.length)
        assertNotEquals(secret1, secret2)
    }
}
