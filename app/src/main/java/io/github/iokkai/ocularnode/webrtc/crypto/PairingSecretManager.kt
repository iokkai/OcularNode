@file:Suppress("DEPRECATION")
package io.github.iokkai.ocularnode.webrtc.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

/**
 * Manages device pairing secrets and unique channel keys using Android KeyStore-backed
 * EncryptedSharedPreferences (AES-256-GCM / MasterKey).
 */
class PairingSecretManager private constructor(context: Context) {

    companion object {
        private const val TAG = "PairingSecretManager"
        private const val PREFS_FILE_NAME = "ocular_pairing_secrets_secure"
        private const val KEY_DEVICE_SECRET = "device_pairing_secret"
        private const val KEY_DEVICE_ID = "device_unique_id"

        @Volatile
        private var instance: PairingSecretManager? = null

        fun getInstance(context: Context): PairingSecretManager {
            return instance ?: synchronized(this) {
                instance ?: PairingSecretManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val securePrefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing EncryptedSharedPreferences, falling back to standard private prefs", e)
            context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Gets or generates the permanent 128-bit device secret for E2EE signaling encryption.
     */
    fun getOrCreateDeviceSecret(): String {
        var secret = securePrefs.getString(KEY_DEVICE_SECRET, null)
        if (secret.isNullOrBlank()) {
            secret = AesGcmCipher.generateRandomDeviceSecret()
            securePrefs.edit().putString(KEY_DEVICE_SECRET, secret).apply()
            Log.i(TAG, "Generated new permanent device pairing secret")
        }
        return secret
    }

    /**
     * Sets or overrides the device pairing secret (e.g. from Zero-Touch DO Provisioning).
     */
    fun setDeviceSecret(secret: String) {
        if (secret.isNotBlank()) {
            securePrefs.edit().putString(KEY_DEVICE_SECRET, secret).apply()
            Log.i(TAG, "Configured permanent device pairing secret from external provision")
        }
    }

    /**
     * Returns the current device secret if set.
     */
    fun getDeviceSecret(): String? {
        return securePrefs.getString(KEY_DEVICE_SECRET, null)
    }

    /**
     * Gets or generates the unique device ID for WebRTC node identification.
     */
    fun getOrCreateDeviceId(): String {
        var deviceId = securePrefs.getString(KEY_DEVICE_ID, null)
        if (deviceId.isNullOrBlank()) {
            deviceId = "ocular-" + java.util.UUID.randomUUID().toString().take(12)
            securePrefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
            Log.i(TAG, "Generated new permanent device ID: $deviceId")
        }
        return deviceId
    }

    /**
     * Computes the public channel key (SHA-256 hash of device secret) used as MQTT topic name.
     */
    fun getChannelKey(): String {
        val secret = getOrCreateDeviceSecret()
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(secret.toByteArray(Charsets.UTF_8))
        return hashBytes.take(16).joinToString("") { "%02x".format(it) }
    }

    /**
     * Saves a known camera pairing secret (on Viewer side).
     */
    fun saveCameraSecret(cameraDeviceId: String, secret: String) {
        securePrefs.edit().putString("cam_secret_$cameraDeviceId", secret).apply()
    }

    /**
     * Retrieves a stored camera pairing secret (on Viewer side).
     */
    fun getCameraSecret(cameraDeviceId: String): String? {
        return securePrefs.getString("cam_secret_$cameraDeviceId", null)
    }
}
