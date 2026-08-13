package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.NetworkInterface

/**
 * Tailscale 精準連線檢測工具 Singleton
 * 採用嚴格特徵比對 (Strict Fingerprint Matching) 機制，
 * 結合 VPN 傳輸層、專屬 IPv6 (fd7a:115c:a1e0:)、MagicDNS (100.100.100.100)
 * 以及 tun / tun0 / tailscale0 / ts0 介面檢查，防止與 Google VPN 或其他 CGNAT 網路產生誤判。
 */
object TailscaleDetector {

    /** Tailscale 專屬 IPv6 網段前綴 */
    const val TAILSCALE_IPV6_PREFIX = "fd7a:115c:a1e0:"

    /** Tailscale 專屬 MagicDNS IP */
    const val TAILSCALE_MAGIC_DNS = "100.100.100.100"

    /**
     * 檢測當前設備是否已建立活躍的 Tailscale 連線
     *
     * @param context Context
     * @return Boolean 當前是否確定為 Tailscale 活躍連線
     */
    fun isTailscaleActive(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        if (connectivityManager != null) {
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork != null) {
                val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    val linkProperties = connectivityManager.getLinkProperties(activeNetwork)
                    if (linkProperties != null) {
                        // 1. 專屬 IPv6 特徵 (絕對命中)
                        val hasTailscaleIpv6 = linkProperties.linkAddresses.any { linkAddress ->
                            val hostAddress = linkAddress.address?.hostAddress ?: ""
                            hostAddress.lowercase().startsWith(TAILSCALE_IPV6_PREFIX)
                        }
                        if (hasTailscaleIpv6) return true

                        // 2. 專屬 MagicDNS 特徵 (絕對命中)
                        val hasMagicDns = linkProperties.dnsServers.any { inetAddress ->
                            inetAddress?.hostAddress == TAILSCALE_MAGIC_DNS
                        }
                        if (hasMagicDns) return true

                        // 3. 特殊介面名稱 (tun/tun0/tailscale0/ts0/ts) + 100.64.0.0/10 CGNAT IPv4
                        val ifName = linkProperties.interfaceName?.lowercase() ?: ""
                        val isTailscaleInterface = ifName.contains("tailscale") || ifName.contains("tun") || ifName.contains("ts")
                        val hasCgnatIpv4 = linkProperties.linkAddresses.any { linkAddress ->
                            val hostAddress = linkAddress.address?.hostAddress ?: ""
                            NetworkUtils.isTailscaleCgnatIp(hostAddress)
                        }

                        if (isTailscaleInterface && hasCgnatIpv4) return true
                    }
                }
            }
        }

        // 備用機制 (NetworkInterface 介面遍歷)：當系統 VPN 狀態無法直接從 activeNetwork 獲取時備用檢測
        return hasTailscaleNetworkInterface()
    }

    /**
     * 備用機制：檢測系統 NetworkInterface 介面是否包含 Tailscale 專屬特徵
     */
    fun hasTailscaleNetworkInterface(): Boolean {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return false
            for (networkInterface in interfaces) {
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                val ifName = networkInterface.name.lowercase()
                val isTunOrTailscale = ifName.contains("tailscale") || ifName.contains("tun") || ifName.contains("ts")

                val addresses = networkInterface.inetAddresses.asSequence().toList()
                var hasIpv6Prefix = false
                var hasCgnatIpv4 = false

                for (address in addresses) {
                    val hostAddress = address.hostAddress ?: continue
                    if (hostAddress.lowercase().startsWith(TAILSCALE_IPV6_PREFIX)) {
                        hasIpv6Prefix = true
                    }
                    if (NetworkUtils.isTailscaleCgnatIp(hostAddress)) {
                        hasCgnatIpv4 = true
                    }
                }

                if (hasIpv6Prefix || (isTunOrTailscale && hasCgnatIpv4)) {
                    return true
                }
            }
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
