package io.github.iokkai.ocularnode.webrtc.signaling

import android.util.Log
import io.github.iokkai.ocularnode.webrtc.crypto.AesGcmCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Plan C: Local Area Network (LAN) Signaling Channel (supports IPv4 and IPv6 dual-stack).
 * Features ultra-fast 150ms TCP probe to immediately detect if camera is on the same Wi-Fi.
 */
class LocalSocketSignalingChannel(
    private val localIp: String,
    private val port: Int = 8080
) : SignalingChannel {

    companion object {
        private const val TAG = "LocalSocketSignaling"
        private const val LAN_PROBE_TIMEOUT_MS = 150
    }

    override val channelType: SignalingChannelType = SignalingChannelType.LOCAL_SOCKET

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(LAN_PROBE_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(2000, TimeUnit.MILLISECONDS)
        .writeTimeout(2000, TimeUnit.MILLISECONDS)
        .build()

    /**
     * Probes if the camera is directly reachable over the local network within 150ms.
     */
    suspend fun isReachable(): Boolean = withContext(Dispatchers.IO) {
        if (localIp.isBlank()) return@withContext false
        try {
            val cleanHost = localIp.removePrefix("[").removeSuffix("]")
            Socket().use { socket ->
                socket.connect(InetSocketAddress(cleanHost, port), LAN_PROBE_TIMEOUT_MS)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun startListening(
        channelKey: String,
        secret: String,
        onMessage: (SignalingPayload) -> Unit
    ) {
        // In local HTTP server mode, messages are received via HTTP POST endpoints on Ktor/ServerSocket
        Log.d(TAG, "Local socket signaling listener attached for key $channelKey")
    }

    override suspend fun sendMessage(
        channelKey: String,
        secret: String,
        message: SignalingPayload
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val host = if (localIp.contains(":") && !localIp.startsWith("[")) "[$localIp]" else localIp
            val url = "http://$host:$port/api/webrtc/signal"
            val rawJson = message.toJson()
            val encryptedPayload = AesGcmCipher.encrypt(rawJson, secret)

            val body = encryptedPayload.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send local socket signal to $localIp:$port: ${e.message}")
            false
        }
    }

    override fun close() {
        // OkHttpClient resources
    }
}
