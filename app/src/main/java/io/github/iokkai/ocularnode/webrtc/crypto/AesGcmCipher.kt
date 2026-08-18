package io.github.iokkai.ocularnode.webrtc.crypto

import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-End Encryption (E2EE) helper utilizing AES-256-GCM authenticated cipher.
 * All SDP, ICE candidate, and signaling control messages are encrypted locally
 * before being sent through public MQTT brokers or Telegram bots.
 */
object AesGcmCipher {

    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val IV_LENGTH_BYTES = 12

    private val secureRandom = SecureRandom()

    /**
     * Derives a 256-bit AES key from a passphrase or device secret using SHA-256.
     */
    fun deriveKey(secret: String): SecretKey {
        require(secret.isNotBlank()) { "Secret key passphrase must not be blank" }
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(secret.toByteArray(StandardCharsets.UTF_8))
        return SecretKeySpec(keyBytes, ALGORITHM)
    }

    /**
     * Encrypts plaintext string using AES-256-GCM and returns a JSON payload containing
     * base64-encoded IV and ciphertext.
     */
    fun encrypt(plaintext: String, secret: String): String {
        val key = deriveKey(secret)
        val iv = ByteArray(IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        val cipherBytes = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))

        val json = JSONObject()
        json.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
        json.put("data", Base64.encodeToString(cipherBytes, Base64.NO_WRAP))
        return json.toString()
    }

    /**
     * Decrypts an encrypted JSON payload (`{"iv":"...","data":"..."}`) using AES-256-GCM.
     */
    fun decrypt(encryptedJson: String, secret: String): String {
        val json = JSONObject(encryptedJson)
        val ivBase64 = json.getString("iv")
        val dataBase64 = json.getString("data")

        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val cipherBytes = Base64.decode(dataBase64, Base64.NO_WRAP)

        val key = deriveKey(secret)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        val decryptedBytes = cipher.doFinal(cipherBytes)
        return String(decryptedBytes, StandardCharsets.UTF_8)
    }

    /**
     * Generates a secure random 128-bit hex secret for device pairing.
     */
    fun generateRandomDeviceSecret(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
