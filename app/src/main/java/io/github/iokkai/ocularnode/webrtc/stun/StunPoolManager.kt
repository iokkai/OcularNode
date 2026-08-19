package io.github.iokkai.ocularnode.webrtc.stun

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.PeerConnection
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.SecureRandom

data class StunEndpoint(
    val uri: String,
    val host: String,
    val port: Int
)

data class StunProbeResult(
    val endpoint: StunEndpoint,
    val latencyMs: Long,
    val isReachable: Boolean
)

/**
 * Manages the STUN Server Pool, conducts asynchronous RFC 5389 UDP pre-checks,
 * measures round-trip latency, and dynamically ranks ICE servers for optimal P2P punch-through speed.
 */
class StunPoolManager {

    companion object {
        private const val TAG = "StunPoolManager"

        // RFC 5389 Magic Cookie: 0x2112A442
        private val MAGIC_COOKIE = byteArrayOf(0x21.toByte(), 0x12.toByte(), 0xA4.toByte(), 0x42.toByte())

        val DEFAULT_STUN_ENDPOINTS = listOf(
            StunEndpoint("stun:stun.l.google.com:19302", "stun.l.google.com", 19302),
            StunEndpoint("stun:stun1.l.google.com:19302", "stun1.l.google.com", 19302),
            StunEndpoint("stun:stun2.l.google.com:19302", "stun2.l.google.com", 19302),
            StunEndpoint("stun:stun.cloudflare.com:3478", "stun.cloudflare.com", 3478),
            StunEndpoint("stun:stun.nextcloud.com:443", "stun.nextcloud.com", 443),
            StunEndpoint("stun:stun.services.mozilla.com:3478", "stun.services.mozilla.com", 3478)
        )
    }

    private val secureRandom = SecureRandom()

    private val _rankedIceServers = MutableStateFlow<List<PeerConnection.IceServer>>(
        DEFAULT_STUN_ENDPOINTS.map { PeerConnection.IceServer.builder(it.uri).createIceServer() }
    )
    val rankedIceServers: StateFlow<List<PeerConnection.IceServer>> = _rankedIceServers.asStateFlow()

    private val _lastProbeResults = MutableStateFlow<List<StunProbeResult>>(emptyList())
    val lastProbeResults: StateFlow<List<StunProbeResult>> = _lastProbeResults.asStateFlow()

    /**
     * Starts an asynchronous background probe across all STUN servers.
     */
    fun startAsyncProbing(scope: CoroutineScope, timeoutMs: Int = 1500) {
        scope.launch(Dispatchers.IO) {
            probeAndRank(timeoutMs = timeoutMs)
        }
    }

    /**
     * Probes all default STUN endpoints in parallel, measures latency, and ranks them.
     */
    suspend fun probeAndRank(
        endpoints: List<StunEndpoint> = DEFAULT_STUN_ENDPOINTS,
        timeoutMs: Int = 1500
    ): List<PeerConnection.IceServer> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting parallel pre-check on ${endpoints.size} STUN servers...")

        val deferredResults = endpoints.map { endpoint ->
            async { probeSingleEndpoint(endpoint, timeoutMs) }
        }

        val results = deferredResults.awaitAll()
        _lastProbeResults.value = results

        val (reachable, unreachable) = results.partition { it.isReachable }
        val sortedReachable = reachable.sortedBy { it.latencyMs }

        val rankedEndpoints = (sortedReachable + unreachable).map { it.endpoint }
        val iceServerList = rankedEndpoints.map {
            PeerConnection.IceServer.builder(it.uri).createIceServer()
        }

        _rankedIceServers.value = iceServerList

        Log.i(
            TAG,
            "STUN probing completed: ${sortedReachable.size}/${endpoints.size} reachable. Fastest: ${sortedReachable.firstOrNull()?.endpoint?.host} (${sortedReachable.firstOrNull()?.latencyMs}ms)"
        )
        iceServerList
    }

    /**
     * Sends an RFC 5389 STUN Binding Request over UDP and awaits response.
     */
    fun probeSingleEndpoint(endpoint: StunEndpoint, timeoutMs: Int): StunProbeResult {
        val startTime = System.currentTimeMillis()
        var socket: DatagramSocket? = null

        try {
            val address = InetAddress.getByName(endpoint.host)
            socket = DatagramSocket().apply {
                soTimeout = timeoutMs
            }

            // Build 20-byte RFC 5389 STUN Binding Request
            // Byte 0-1: 0x0001 (Binding Request)
            // Byte 2-3: 0x0000 (Message Length)
            // Byte 4-7: 0x2112A442 (Magic Cookie)
            // Byte 8-19: 12-byte Transaction ID
            val requestBytes = ByteArray(20)
            requestBytes[0] = 0x00
            requestBytes[1] = 0x01
            requestBytes[2] = 0x00
            requestBytes[3] = 0x00
            System.arraycopy(MAGIC_COOKIE, 0, requestBytes, 4, 4)

            val transactionId = ByteArray(12)
            secureRandom.nextBytes(transactionId)
            System.arraycopy(transactionId, 0, requestBytes, 8, 12)

            val requestPacket = DatagramPacket(requestBytes, requestBytes.size, address, endpoint.port)
            socket.send(requestPacket)

            val responseBuffer = ByteArray(512)
            val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.receive(responsePacket)

            val latency = (System.currentTimeMillis() - startTime).coerceAtLeast(1)

            // Validate STUN response: Message type 0x0101 (Binding Success) and matching magic cookie
            val isSuccessResponse = responseBuffer[0] == 0x01.toByte() && responseBuffer[1] == 0x01.toByte()
            val magicMatches = responseBuffer[4] == MAGIC_COOKIE[0] &&
                    responseBuffer[5] == MAGIC_COOKIE[1] &&
                    responseBuffer[6] == MAGIC_COOKIE[2] &&
                    responseBuffer[7] == MAGIC_COOKIE[3]

            val isReachable = isSuccessResponse || magicMatches
            Log.d(TAG, "Probed ${endpoint.host}:${endpoint.port} -> reachable: $isReachable, latency: ${latency}ms")
            return StunProbeResult(endpoint, latency, isReachable)
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            Log.d(TAG, "Probed ${endpoint.host}:${endpoint.port} -> failed (${e.javaClass.simpleName}: ${e.message})")
            return StunProbeResult(endpoint, latency, false)
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Returns the currently ranked ICE servers.
     */
    fun getRankedIceServers(): List<PeerConnection.IceServer> {
        return _rankedIceServers.value
    }
}
