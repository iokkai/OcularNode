package com.example.util

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

data class IpInfo(
    val tailscaleIp: String?,
    val localIp: String?,
    val allIps: List<String>,
    val isTailscaleConnected: Boolean = !tailscaleIp.isNullOrBlank(),
    val isVpnActive: Boolean = false,
    val isTailscaleInstalled: Boolean = false
)

object NetworkUtils {

    /**
     * 檢查系統是否已安裝 Tailscale App (com.tailscale.ipn)
     */
    fun isTailscaleInstalled(context: Context): Boolean {
        val tailscalePackage = "com.tailscale.ipn"
        return try {
            context.packageManager.getPackageInfo(tailscalePackage, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 精確檢查 IPv4 是否在 Tailscale CGNAT 預留網段 100.64.0.0/10 (100.64.0.0 - 100.127.255.255)
     */
    fun isTailscaleCgnatIp(ip: String): Boolean {
        if (!ip.startsWith("100.")) return false
        val parts = ip.split(".")
        if (parts.size == 4) {
            val second = parts[1].toIntOrNull()
            if (second != null && second in 64..127) {
                return true
            }
        }
        return false
    }

    fun isVpnActive(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    fun getIpAddresses(context: Context? = null): IpInfo {
        var tailscaleIp: String? = null
        var localIp: String? = null
        val allIps = mutableListOf<String>()

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return IpInfo(null, null, emptyList())
            for (networkInterface in interfaces) {
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                val ifName = networkInterface.name.lowercase()
                val isTunOrTailscaleInterface = ifName.contains("tailscale") || ifName.contains("tun") || ifName.contains("ts")

                val addresses = networkInterface.inetAddresses.asSequence().toList()
                var interfaceHasTailscaleIpv6 = false

                // 檢查該介面是否有 Tailscale 專屬 IPv6 前綴 (fd7a:115c:a1e0:)
                for (address in addresses) {
                    val hostAddress = address.hostAddress ?: continue
                    if (hostAddress.lowercase().startsWith(TailscaleDetector.TAILSCALE_IPV6_PREFIX)) {
                        interfaceHasTailscaleIpv6 = true
                        break
                    }
                }

                for (address in addresses) {
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        val hostAddress = address.hostAddress ?: continue
                        allIps.add(hostAddress)

                        val isCgnat = isTailscaleCgnatIp(hostAddress)

                        // 精確判定 Tailscale IP:
                        // 必須符合 100.64.0.0/10 網段，且 (介面名稱包含 tailscale/tun/ts 或 擁有專屬 IPv6 前綴)
                        if (isCgnat && (isTunOrTailscaleInterface || interfaceHasTailscaleIpv6)) {
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

        val tsActive = if (context != null) TailscaleDetector.isTailscaleActive(context) else !tailscaleIp.isNullOrBlank()
        val vpnActive = if (context != null) isVpnActive(context) else false
        val installed = if (context != null) isTailscaleInstalled(context) else false

        val finalTailscaleIp = if (context != null) {
            if (tsActive) tailscaleIp else null
        } else {
            tailscaleIp
        }
        val isConnected = if (context != null) tsActive else !tailscaleIp.isNullOrBlank()

        return IpInfo(
            tailscaleIp = finalTailscaleIp,
            localIp = localIp,
            allIps = allIps,
            isTailscaleConnected = isConnected,
            isVpnActive = vpnActive,
            isTailscaleInstalled = installed
        )
    }

    /**
     * 動態 Flow 監聽網路連線與 VPN 變更，自動即時回傳 Tailscale 狀態
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

        // 啟動背景協程每 2 秒自動輪詢網路介面，確保 Tailscale App 在背景啟動/關閉時可即時偵測
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

    /**
     * 嘗試開啟系統已安裝的 Tailscale App 或引導至商店
     */
    fun openTailscaleApp(context: Context) {
        val tailscalePackage = "com.tailscale.ipn"
        val pm = context.packageManager

        // 1. 先透過 launchIntent 嘗試開啟已安裝的 Tailscale
        val launchIntent = pm.getLaunchIntentForPackage(tailscalePackage)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(launchIntent)
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. 嘗試使用 CATEGORY_LAUNCHER Intent 搜尋
        try {
            val directIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(tailscalePackage)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (directIntent.resolveActivity(pm) != null) {
                context.startActivity(directIntent)
                return
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. 檢查套件是否已安裝但未回傳預設 Launcher Activity
        if (isTailscaleInstalled(context)) {
            try {
                val intent = Intent().apply {
                    setClassName(tailscalePackage, "com.tailscale.ipn.IPNActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 4. 若確實未安裝 Tailscale App，提示使用者並開啟商店
        Toast.makeText(context, "未偵測到 Tailscale App，即將開啟商店頁面下載", Toast.LENGTH_SHORT).show()
        try {
            val storeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$tailscalePackage")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(storeIntent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$tailscalePackage")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }
}

