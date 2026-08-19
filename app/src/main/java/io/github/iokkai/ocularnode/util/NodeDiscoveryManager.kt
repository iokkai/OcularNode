package io.github.iokkai.ocularnode.util

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import io.github.iokkai.ocularnode.data.AppDatabase
import io.github.iokkai.ocularnode.data.CameraDevice
import io.github.iokkai.ocularnode.data.CameraDeviceDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.URL

object NodeDiscoveryManager {
    private const val TAG = "NodeDiscoveryManager"
    const val UDP_PORT = 8888

    /** IPv6 multicast port uses a distinct port to avoid conflicts with the IPv4 DatagramSocket */
    private const val UDP_PORT_IPV6_MULTICAST = 8889

    /** Syncthing-inspired link-local IPv6 multicast group for OcularNode local discovery */
    private const val IPV6_MULTICAST_GROUP = "ff02::8384"

    const val DISCOVERY_REQUEST = "DISCOVER_OCULAR_NODE_REQUEST"
    const val ANNOUNCE_PREFIX = "OCULAR_NODE_ANNOUNCE|"
    const val RESPONSE_PREFIX = "OCULAR_NODE_RESPONSE|"

    private var discoveryJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _discoveredNodesCount = MutableStateFlow(0)
    val discoveredNodesCount: StateFlow<Int> = _discoveredNodesCount.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun startDiscovery(context: Context) {
        if (discoveryJob?.isActive == true) return
        _isScanning.value = true

        discoveryJob = scope.launch {
            val db = AppDatabase.getDatabase(context)
            val dao = db.cameraDeviceDao()

            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val multicastLock = wifiManager?.createMulticastLock("OcularNodeMulticastLock")?.apply {
                setReferenceCounted(true)
                try { acquire() } catch (e: Exception) { Log.e(TAG, "MulticastLock error", e) }
            }

            // 1. Listen for IPv4 UDP Broadcasts
            launch {
                listenForUdpPackets(dao)
            }

            // 2. Listen for IPv6 Multicast Announcements (fills Mesh Wi-Fi / Client Isolation blind spot)
            launch {
                listenForIpv6Multicast(dao)
            }

            // 3. Periodic dual-stack broadcast & HTTP status scan loop
            while (isActive) {
                try {
                    // Fire both transports concurrently
                    val broadcastJob = launch { sendUdpBroadcast(context) }
                    val multicastJob = launch { sendIpv6Multicast() }
                    broadcastJob.join()
                    multicastJob.join()
                    checkKnownAndSubnetCameras(context, dao)
                } catch (e: Exception) {
                    Log.e(TAG, "Discovery cycle error", e)
                }
                delay(4000)
            }

            multicastLock?.let {
                if (it.isHeld) try { it.release() } catch (_: Exception) {}
            }
            _isScanning.value = false
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        _isScanning.value = false
    }

    // -------------------------------------------------------------------------
    // IPv4 Broadcast (existing)
    // -------------------------------------------------------------------------

    private suspend fun sendUdpBroadcast(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    val data = DISCOVERY_REQUEST.toByteArray()

                    // Broadcast to standard LAN broadcast
                    val broadcastAddress = InetAddress.getByName("255.255.255.255")
                    val packet = DatagramPacket(data, data.size, broadcastAddress, UDP_PORT)
                    socket.send(packet)
                }
            } catch (e: Exception) {
                Log.w(TAG, "UDP broadcast send error: ${e.message}")
            }
        }
    }

    private suspend fun listenForUdpPackets(dao: CameraDeviceDao) {
        withContext(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(UDP_PORT))
                    soTimeout = 2500
                }
                val buffer = ByteArray(2048)

                while (scope.isActive) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val message = String(packet.data, 0, packet.length).trim()
                        val senderIp = packet.address.hostAddress ?: continue

                        handleDiscoveryMessage(message, senderIp, dao)
                    } catch (e: java.net.SocketTimeoutException) {
                        // Regular timeout for coroutine loop check
                    } catch (e: Exception) {
                        delay(1000)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "UDP Listener socket error: ${e.message}")
            } finally {
                socket?.close()
            }
        }
    }

    // -------------------------------------------------------------------------
    // IPv6 Link-Local Multicast (new — Syncthing-inspired ff02::8384)
    // -------------------------------------------------------------------------

    /**
     * Sends a discovery announcement to the IPv6 link-local multicast group [ff02::8384].
     * This reaches peers on the same network segment even when IPv4 UDP Broadcast is
     * blocked by Mesh Wi-Fi Client Isolation or enterprise APs.
     */
    private suspend fun sendIpv6Multicast() {
        withContext(Dispatchers.IO) {
            try {
                val group = InetAddress.getByName(IPV6_MULTICAST_GROUP)
                MulticastSocket().use { socket ->
                    socket.timeToLive = 1 // Link-local scope only — never leaves the subnet
                    val data = DISCOVERY_REQUEST.toByteArray()
                    val packet = DatagramPacket(data, data.size, group, UDP_PORT_IPV6_MULTICAST)
                    socket.send(packet)
                    Log.v(TAG, "Sent IPv6 multicast discovery to $IPV6_MULTICAST_GROUP")
                }
            } catch (e: Exception) {
                // IPv6 may not be available on all networks — fail silently
                Log.v(TAG, "IPv6 multicast send skipped: ${e.message}")
            }
        }
    }

    /**
     * Listens for IPv6 multicast discovery packets on [ff02::8384]:8889.
     * Joins the group on every available network interface to maximize reach.
     */
    private suspend fun listenForIpv6Multicast(dao: CameraDeviceDao) {
        withContext(Dispatchers.IO) {
            var socket: MulticastSocket? = null
            try {
                val group = InetAddress.getByName(IPV6_MULTICAST_GROUP)
                socket = MulticastSocket(UDP_PORT_IPV6_MULTICAST).apply {
                    reuseAddress = true
                    soTimeout = 2500
                    // Join multicast group on every network interface that supports multicast
                    try {
                        NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { iface ->
                            if (iface.supportsMulticast() && !iface.isLoopback) {
                                try {
                                    joinGroup(InetSocketAddress(group, UDP_PORT_IPV6_MULTICAST), iface)
                                    Log.v(TAG, "Joined IPv6 multicast group on ${iface.name}")
                                } catch (_: Exception) {}
                            }
                        }
                    } catch (_: Exception) {}
                }

                val buffer = ByteArray(2048)
                Log.i(TAG, "IPv6 multicast listener started on [$IPV6_MULTICAST_GROUP]:$UDP_PORT_IPV6_MULTICAST")

                while (scope.isActive) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val message = String(packet.data, 0, packet.length).trim()
                        val senderIp = packet.address.hostAddress ?: continue

                        handleDiscoveryMessage(message, senderIp, dao)
                    } catch (e: java.net.SocketTimeoutException) {
                        // Regular timeout for coroutine loop check
                    } catch (e: Exception) {
                        delay(1000)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "IPv6 multicast listener error (IPv6 may not be available): ${e.message}")
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }
    }

    // -------------------------------------------------------------------------
    // Shared message parsing
    // -------------------------------------------------------------------------

    private suspend fun handleDiscoveryMessage(message: String, senderIp: String, dao: CameraDeviceDao) {
        if (message.startsWith(ANNOUNCE_PREFIX) || message.startsWith(RESPONSE_PREFIX)) {
            val parts = message.split("|")
            if (parts.size >= 3) {
                val nodeName = parts[1]
                val port = parts[2].toIntOrNull() ?: 8080
                registerOrUpdateNode(dao, nodeName, senderIp, port)
            }
        } else if (message.startsWith("{")) {
            try {
                val json = JSONObject(message)
                if (json.optString("type") == "ocular_node") {
                    val name = json.optString("name", "OcularNode")
                    val port = json.optInt("port", 8080)
                    val ip = json.optString("ip", senderIp)
                    registerOrUpdateNode(dao, name, ip, port)
                }
            } catch (_: Exception) {}
        }
    }

    // -------------------------------------------------------------------------
    // HTTP status probing (existing)
    // -------------------------------------------------------------------------

    private suspend fun checkKnownAndSubnetCameras(context: Context, dao: CameraDeviceDao) = coroutineScope {
        val cameras = dao.getCamerasListOnce()
        if (cameras.isEmpty()) {
            _discoveredNodesCount.value = 0
            return@coroutineScope
        }

        val results = cameras.map { cam ->
            async(Dispatchers.IO) {
                val isOnline = probeCameraStatus(cam.ipAddress, cam.port)
                cam to isOnline
            }
        }.awaitAll()

        var onlineCount = 0
        for ((cam, isOnline) in results) {
            if (isOnline) {
                onlineCount++
                dao.updateCameraStatus(
                    id = cam.id,
                    isOnline = true,
                    battery = cam.batteryLevel,
                    timestamp = System.currentTimeMillis()
                )
            } else {
                if (System.currentTimeMillis() - cam.lastOnlineTimestamp > 12000) {
                    dao.updateCameraStatus(
                        id = cam.id,
                        isOnline = false,
                        battery = -1,
                        timestamp = cam.lastOnlineTimestamp
                    )
                }
            }
        }

        _discoveredNodesCount.value = onlineCount
    }

    private fun probeCameraStatus(ip: String, port: Int): Boolean {
        return try {
            val url = URL("http://$ip:$port/status")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 1000
            conn.readTimeout = 1000
            conn.requestMethod = "GET"
            val code = conn.responseCode
            conn.disconnect()
            code == 200
        } catch (_: Exception) {
            false
        }
    }

    suspend fun registerOrUpdateNode(
        dao: CameraDeviceDao,
        name: String,
        ip: String,
        port: Int
    ) {
        val existingList = dao.getCamerasListOnce()
        val matched = existingList.find { it.ipAddress == ip && it.port == port }

        if (matched != null) {
            val updated = matched.copy(
                name = if (matched.name.isBlank() || matched.name.startsWith("Camera") || matched.name.startsWith("鏡頭")) name else matched.name,
                isOnline = true,
                lastOnlineTimestamp = System.currentTimeMillis(),
                modelInfo = "OcularNode (Auto-Discovered)"
            )
            dao.updateCamera(updated)
        } else {
            val newNode = CameraDevice(
                name = if (name.isBlank()) "OcularNode ($ip)" else name,
                ipAddress = ip,
                port = port,
                isOnline = true,
                lastOnlineTimestamp = System.currentTimeMillis(),
                modelInfo = "OcularNode (Auto-Discovered)"
            )
            dao.insertCamera(newNode)
        }
    }
}
