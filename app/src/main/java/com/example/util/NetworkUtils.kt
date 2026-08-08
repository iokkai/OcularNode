package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.text.format.Formatter
import java.net.Inet4Address
import java.net.NetworkInterface

data class IpInfo(
    val tailscaleIp: String?,
    val localIp: String?,
    val allIps: List<String>
)

object NetworkUtils {

    fun getIpAddresses(): IpInfo {
        var tailscaleIp: String? = null
        var localIp: String? = null
        val allIps = mutableListOf<String>()

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return IpInfo(null, null, emptyList())
            for (networkInterface in interfaces) {
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                val addresses = networkInterface.inetAddresses
                for (address in addresses) {
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        val hostAddress = address.hostAddress ?: continue
                        allIps.add(hostAddress)
                        
                        // Check for Tailscale IP range 100.64.0.0/10 (100.64.x.x - 100.127.x.x) or interface name 'tailscale'
                        if (hostAddress.startsWith("100.") || networkInterface.name.lowercase().contains("tailscale") || networkInterface.name.lowercase().contains("tun")) {
                            tailscaleIp = hostAddress
                        } else if (localIp == null && (hostAddress.startsWith("192.168.") || hostAddress.startsWith("10.") || hostAddress.startsWith("172."))) {
                            localIp = hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return IpInfo(tailscaleIp, localIp, allIps)
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun registerNetworkCallback(context: Context, onNetworkStatusChanged: (Boolean) -> Unit): ConnectivityManager.NetworkCallback? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                onNetworkStatusChanged(true)
            }

            override fun onLost(network: Network) {
                onNetworkStatusChanged(false)
            }
        }

        try {
            connectivityManager.registerNetworkCallback(request, callback)
            return callback
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun unregisterNetworkCallback(context: Context, callback: ConnectivityManager.NetworkCallback) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        try {
            connectivityManager?.unregisterNetworkCallback(callback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
