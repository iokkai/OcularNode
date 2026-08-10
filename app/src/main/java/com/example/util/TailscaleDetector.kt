package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Tailscale 精準連線檢測工具 Singleton
 * 採用嚴格特徵比對 (Strict Fingerprint Matching) 機制，
 * 捨棄通用 IPv4 CGNAT 網段檢查，僅依賴 Tailscale 獨有的絕對特徵判定。
 */
object TailscaleDetector {

    /**
     * 檢測當前設備是否已建立活躍的 Tailscale 連線
     *
     * @param context Context
     * @return Boolean 當前是否確定為 Tailscale 活躍連線
     */
    fun isTailscaleActive(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        // 第一關 (VPN 傳輸層檢測)：檢查當前活躍網路是否包含 TRANSPORT_VPN 屬性
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            return false
        }

        // 取得活躍網路的 LinkProperties
        val linkProperties = connectivityManager.getLinkProperties(activeNetwork) ?: return false

        // 第二關 (專屬 IPv6 特徵 - 絕對命中)：檢查 linkAddresses 是否有 IP 以 fd7a:115c:a1e0: 開頭
        val linkAddresses = linkProperties.linkAddresses
        val hasTailscaleIpv6 = linkAddresses.any { linkAddress ->
            val hostAddress = linkAddress.address?.hostAddress ?: ""
            hostAddress.lowercase().startsWith("fd7a:115c:a1e0:")
        }
        if (hasTailscaleIpv6) {
            return true
        }

        // 第三關 (專屬 MagicDNS 特徵 - 絕對命中)：檢查 dnsServers 是否包含 100.100.100.100
        val dnsServers = linkProperties.dnsServers
        val hasMagicDns = dnsServers.any { inetAddress ->
            inetAddress?.hostAddress == "100.100.100.100"
        }
        if (hasMagicDns) {
            return true
        }

        // 終極拒絕 (防堵 Google VPN 與其他 VPN)：
        // 若上述第二關與第三關皆未命中，代表雖然是 VPN 連線，但絕對不是 Tailscale。
        return false
    }
}
