package io.github.iokkai.ocularnode.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface

data class IpInfo(
    val localIp: String?,
    val allIps: List<String>,
    val ipv6GlobalAddress: String? = null
)

object NetworkUtils {

    /**
     * 取得當前已連線的 Wi-Fi SSID (若有)
     * 在 Android 8.1+ / 12+ 需要 ACCESS_FINE_LOCATION 權限以及開啟 GPS
     */
    fun getCurrentWifiSsid(context: Context): String? {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val activeNetwork = connectivityManager?.activeNetwork
                val capabilities = connectivityManager?.getNetworkCapabilities(activeNetwork)
                if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    val wifiInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        capabilities.transportInfo as? android.net.wifi.WifiInfo
                    } else {
                        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                        @Suppress("DEPRECATION")
                        wifiManager?.connectionInfo
                    }
                    val ssid = wifiInfo?.ssid
                    if (ssid != null && ssid != "<unknown ssid>" && ssid.isNotBlank()) {
                        return ssid.removePrefix("\"").removeSuffix("\"")
                    }
                }
            }
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            @Suppress("DEPRECATION")
            val wifiInfo = wifiManager?.connectionInfo
            val ssid = wifiInfo?.ssid
            if (ssid == null || ssid == "<unknown ssid>" || ssid.isBlank()) {
                null
            } else {
                ssid.removePrefix("\"").removeSuffix("\"")
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 取得系統本機所有 IP 地址（含區域網路 IPv4 與全球單播 IPv6）
     */
    fun getIpAddresses(context: Context? = null): IpInfo {
        var localIp: String? = null
        var ipv6GlobalAddress: String? = null
        val allIps = mutableListOf<String>()

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return IpInfo(null, emptyList())
            for (networkInterface in interfaces) {
                if (!networkInterface.isUp || networkInterface.isLoopback) continue

                val addresses = networkInterface.inetAddresses.asSequence().toList()
                for (address in addresses) {
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        val hostAddress = address.hostAddress ?: continue
                        allIps.add(hostAddress)

                        if (localIp == null && (hostAddress.startsWith("192.168.") || hostAddress.startsWith("10.") || hostAddress.startsWith("172."))) {
                            localIp = hostAddress
                        }
                    } else if (address is Inet6Address && !address.isLoopbackAddress) {
                        val rawHost = address.hostAddress ?: continue
                        val cleanHost = rawHost.substringBefore("%") // 移除介面 scope index (%wlan0 等)
                        allIps.add(cleanHost)

                        // 排除鏈路本地 (fe80::) 與私有 (fc/fd::)，僅保留全球單播地址 (2001:, 2404:, 2600: 等)
                        if (!address.isLinkLocalAddress &&
                            !address.isSiteLocalAddress &&
                            !cleanHost.lowercase().startsWith("fd") &&
                            !cleanHost.lowercase().startsWith("fc") &&
                            !cleanHost.lowercase().startsWith("fe80")
                        ) {
                            if (ipv6GlobalAddress == null) {
                                ipv6GlobalAddress = cleanHost
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return IpInfo(
            localIp = localIp,
            allIps = allIps,
            ipv6GlobalAddress = ipv6GlobalAddress
        )
    }

    /**
     * 動態 Flow 監聽網路連線變更，自動即時回傳網路 IP 狀態
     */
    fun observeNetworkStatus(context: Context): Flow<IpInfo> = callbackFlow {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        var lastIpInfo: IpInfo? = null
        fun checkAndEmit() {
            val current = getIpAddresses(context)
            if (current != lastIpInfo) {
                lastIpInfo = current
                trySend(current)
            }
        }

        // 發送當前初始狀態
        checkAndEmit()

        // 啟動背景協程每 2 秒自動輪詢網路介面
        val pollingJob = launch {
            while (isActive) {
                delay(2000)
                checkAndEmit()
            }
        }

        if (connectivityManager == null) {
            awaitClose { pollingJob.cancel() }
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                checkAndEmit()
            }

            override fun onLost(network: Network) {
                checkAndEmit()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                checkAndEmit()
            }
        }

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                connectivityManager.registerDefaultNetworkCallback(callback)
            } else {
                val request = NetworkRequest.Builder().build()
                connectivityManager.registerNetworkCallback(request, callback)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        awaitClose {
            pollingJob.cancel()
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
