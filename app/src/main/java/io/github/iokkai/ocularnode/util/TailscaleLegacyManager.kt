package io.github.iokkai.ocularnode.util

import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Bundle
import android.util.Log
import io.github.iokkai.ocularnode.data.SettingsManager
import io.github.iokkai.ocularnode.receiver.AdminReceiver
import io.github.iokkai.ocularnode.receiver.PackageInstallReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * 獨立的 Tailscale Legacy 管理器。
 * 將 Tailscale APK 下載、靜默安裝、MDM 限制注入與 Always-On VPN 流程完全解耦隔離。
 * 預設停用 (WebRTC P2P 為主力)；僅在進階設定開啟 Tailscale 模式時執行。
 */
object TailscaleLegacyManager {

    private const val TAG = "TailscaleLegacyManager"
    const val TAILSCALE_PACKAGE = "com.tailscale.ipn"
    val DEFAULT_TAILSCALE_APK_URL = io.github.iokkai.ocularnode.BuildConfig.TAILSCALE_APK_URL.ifBlank {
        "https://pkgs.tailscale.com/stable/tailscale-android-universal-1.102.2.apk"
    }

    private val _tailscaleDownloadProgress = MutableStateFlow(TailscaleDownloadProgress())
    val tailscaleDownloadProgress = _tailscaleDownloadProgress.asStateFlow()

    fun isTailscaleLegacyEnabled(context: Context): Boolean {
        val settings = SettingsManager.getInstance(context)
        return settings.connectionMode == "TAILSCALE"
    }

    /**
     * 啟動 Tailscale 零接觸部署流程 (僅在明確啟用 Tailscale 模式時執行)
     */
    fun startZeroTouchPipeline(
        context: Context,
        authKey: String,
        apkUrl: String = DEFAULT_TAILSCALE_APK_URL,
        forceEnable: Boolean = false
    ) {
        if (!forceEnable && !isTailscaleLegacyEnabled(context)) {
            Log.i(TAG, "WebRTC P2P 為預設連線模式，已安全略過 Tailscale 下載與 VPN 安裝流程")
            return
        }

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val admin = ComponentName(context, AdminReceiver::class.java)
        val isDO = dpm?.isDeviceOwnerApp(context.packageName) == true

        Log.i(TAG, "啟動 Tailscale Legacy 部署管道 (Is DO: $isDO)")

        if (!isDO) {
            Log.w(TAG, "非 Device Owner，略過 Tailscale 靜默安裝特權操作")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (NetworkUtils.isTailscaleInstalled(context)) {
                    Log.i(TAG, "Tailscale 已安裝，直接進行 MDM 政策注入")
                    injectTailscaleRestrictionsAndEnableVpn(context, authKey)
                } else {
                    val apkFile = downloadTailscaleApk(context, apkUrl)
                    if (apkFile != null && apkFile.exists()) {
                        installTailscaleApkSilently(context, apkFile)
                    } else {
                        Log.e(TAG, "Tailscale APK 下載失敗，嘗試直接進行 MDM 政策注入")
                        injectTailscaleRestrictionsAndEnableVpn(context, authKey)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Tailscale Legacy Pipeline 異常: ${e.message}", e)
            }
        }
    }

    /**
     * 背景下載 Tailscale APK
     */
    suspend fun downloadTailscaleApk(context: Context, downloadUrl: String): File? = withContext(Dispatchers.IO) {
        try {
            val url = downloadUrl.ifBlank { DEFAULT_TAILSCALE_APK_URL }
            Log.i(TAG, "開始下載 Tailscale APK: $url")

            _tailscaleDownloadProgress.value = TailscaleDownloadProgress(
                isDownloading = true,
                progressPercent = 0,
                downloadedBytes = 0L,
                totalBytes = -1L,
                status = "Connecting to download Tailscale APK..."
            )

            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to download Tailscale APK HTTP ${response.code}")
                    _tailscaleDownloadProgress.value = TailscaleDownloadProgress(
                        isDownloading = false,
                        status = "Failed to download Tailscale APK (HTTP ${response.code})"
                    )
                    return@withContext null
                }

                val body = response.body
                val totalBytes = body?.contentLength() ?: -1L
                val destFile = File(context.cacheDir, "tailscale_download.apk")

                body?.byteStream()?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(16384)
                        var bytesRead = 0L
                        var read: Int
                        var lastEmitTime = System.currentTimeMillis()

                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesRead += read

                            val now = System.currentTimeMillis()
                            if (now - lastEmitTime > 200 || (totalBytes > 0 && bytesRead == totalBytes)) {
                                lastEmitTime = now
                                val percent = if (totalBytes > 0) ((bytesRead * 100) / totalBytes).toInt().coerceIn(0, 100) else -1
                                _tailscaleDownloadProgress.value = TailscaleDownloadProgress(
                                    isDownloading = true,
                                    progressPercent = percent,
                                    downloadedBytes = bytesRead,
                                    totalBytes = totalBytes,
                                    status = if (percent >= 0) "Downloading Tailscale APK ($percent%)..." else "Downloading Tailscale APK (${bytesRead / 1024 / 1024} MB)..."
                                )
                            }
                        }
                        output.flush()
                    }
                }

                _tailscaleDownloadProgress.value = TailscaleDownloadProgress(
                    isDownloading = false,
                    progressPercent = 100,
                    downloadedBytes = destFile.length(),
                    totalBytes = destFile.length(),
                    status = "Tailscale APK download completed, installing silently..."
                )
                Log.i(TAG, "Tailscale APK 下載成功: ${destFile.absolutePath} (${destFile.length()} bytes)")
                destFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "下載 Tailscale APK 發生錯誤", e)
            _tailscaleDownloadProgress.value = TailscaleDownloadProgress(
                isDownloading = false,
                status = "Error downloading Tailscale: ${e.localizedMessage}"
            )
            null
        }
    }

    /**
     * 靜默安裝 Tailscale APK
     */
    fun installTailscaleApkSilently(context: Context, apkFile: File) {
        try {
            Log.i(TAG, "呼叫 PackageInstaller 靜默安裝 Tailscale")
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(TAILSCALE_PACKAGE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }

            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            apkFile.inputStream().use { input ->
                session.openWrite("tailscale_install_session", 0, apkFile.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }

            val intent = Intent(context, PackageInstallReceiver::class.java).apply {
                action = "io.github.iokkai.ocularnode.ACTION_INSTALL_COMPLETE"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            session.commit(pendingIntent.intentSender)
            session.close()
            Log.i(TAG, "Tailscale PackageInstaller session 提交完畢")
        } catch (e: Exception) {
            Log.e(TAG, "靜默安裝 Tailscale 異常", e)
        }
    }

    /**
     * MDM 政策注入 AuthKey 並啟用 Always-On VPN
     */
    fun injectTailscaleRestrictionsAndEnableVpn(context: Context, authKey: String) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return
        val admin = ComponentName(context, AdminReceiver::class.java)

        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.w(TAG, "非 Device Owner，無法注入 Tailscale ApplicationRestrictions")
            return
        }

        try {
            if (authKey.isNotBlank()) {
                val restrictions = Bundle().apply {
                    putString("AuthKey", authKey)
                    putString("authkey", authKey)
                    putString("authKey", authKey)
                }
                dpm.setApplicationRestrictions(admin, TAILSCALE_PACKAGE, restrictions)
                Log.i(TAG, "成功注入 Tailscale Restriction (AuthKey: ${authKey.take(10)}...)")
            }

            try {
                dpm.setAlwaysOnVpnPackage(admin, TAILSCALE_PACKAGE, false)
                Log.i(TAG, "成功設置 Tailscale 為 Always-On VPN (非封鎖模式)")
            } catch (e: Exception) {
                Log.e(TAG, "設置 Always-On VPN 失敗 (Tailscale 可能尚未安裝完成)", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tailscale MDM 政策注入失敗", e)
        }
    }
}
