package io.github.iokkai.ocularnode.util

import android.app.Activity
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.pm.PackageInfoCompat
import io.github.iokkai.ocularnode.data.SettingsManager
import io.github.iokkai.ocularnode.receiver.AdminReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object ZeroTouchProvisionManager {

    private const val TAG = "ZeroTouchProvision"
    private const val TAILSCALE_PACKAGE = "com.tailscale.ipn"
    private val DEFAULT_TAILSCALE_APK_URL = io.github.iokkai.ocularnode.BuildConfig.TAILSCALE_APK_URL.ifBlank { "https://pkgs.tailscale.com/stable/tailscale-v1.80.0-arm64.apk" }

    fun getAdminComponent(context: Context): ComponentName {
        return ComponentName(context, AdminReceiver::class.java)
    }

    /**
     * 動態從 GitHub Releases API 取得最新版 APK 的真實下載網址
     * @param githubOwner GitHub 擁有者
     * @param githubRepo GitHub 儲存庫名稱
     * @return 最新 APK 的下載網址字串
     * @throws Exception 當網路連線失敗或找不到 APK 時拋出異常
     */
    suspend fun getLatestReleaseApkUrl(githubOwner: String, githubRepo: String): String = withContext(Dispatchers.IO) {
        if (githubOwner.isBlank() || githubRepo.isBlank()) {
            throw Exception("GitHub Owner 或 Repo 未設定，無法動態獲取更新網址")
        }

        val apiUrl = "https://api.github.com/repos/$githubOwner/$githubRepo/releases/latest"
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(apiUrl)
            .header("User-Agent", "OcularNode-App")
            .header("Accept", "application/vnd.github.v3+json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("GitHub Releases API 請求失敗，HTTP Code: ${response.code}")
            }

            val jsonStr = response.body?.string() ?: throw Exception("回傳內容為空")
            val jsonObject = JSONObject(jsonStr)

            val assets = jsonObject.optJSONArray("assets")
            if (assets == null || assets.length() == 0) {
                throw Exception("該 Release 內沒有包含任何可下載的檔案 (Assets)")
            }

            // 尋找第一個以 .apk 結尾的檔案
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val downloadUrl = asset.optString("browser_download_url", "")
                if (downloadUrl.endsWith(".apk", ignoreCase = true)) {
                    Log.i(TAG, "成功取得動態 APK 網址: $downloadUrl")
                    return@withContext downloadUrl
                }
            }
            throw Exception("在 Release Assets 中找不到副檔名為 .apk 的檔案")
        }
    }

    /**
     * 動態下載檔案並計算其 SHA-256 Checksum (URL-Safe Base64 編碼，無 padding)
     * 在 Android 9+ (API 28+) 的 QR Code 部署中，下載 APK 時必須提供 Checksum
     */
    suspend fun getApkSha256Checksum(downloadUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "開始計算 APK Checksum: $downloadUrl")
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder().url(downloadUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "計算 Checksum 失敗 HTTP ${response.code}")
                    return@withContext null
                }
                
                val md = java.security.MessageDigest.getInstance("SHA-256")
                response.body?.byteStream()?.use { input ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        md.update(buffer, 0, read)
                    }
                }
                val hashBytes = md.digest()
                // Android 部署要求 URL-Safe Base64 (建議無 Padding 與無換行)
                val checksum = android.util.Base64.encodeToString(
                    hashBytes, 
                    android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
                )
                Log.i(TAG, "成功取得 APK Checksum: $checksum")
                return@withContext checksum
            }
        } catch (e: Exception) {
            Log.e(TAG, "計算 APK Checksum 異常", e)
            null
        }
    }
    /**
     * 檢查並將 OcularNode (自身 Package) 以及 Tailscale (com.tailscale.ipn) 加入電池最佳化豁免白名單。
     * 防止舊手機進入 Doze 模式時，系統殺死相機串流背景服務與 VPN 網路通道。
     */
    fun exemptFromBatteryOptimizations(context: Context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager == null) {
            Log.e(TAG, "無法取得 PowerManager 服務")
            return
        }

        val packagesToCheck = listOf(context.packageName, TAILSCALE_PACKAGE)

        for (pkg in packagesToCheck) {
            try {
                // 利用 PowerManager.isIgnoringBatteryOptimizations 判斷是否已在白名單內
                val isIgnoring = powerManager.isIgnoringBatteryOptimizations(pkg)
                Log.i(TAG, "檢查電池最佳化豁免狀態 [$pkg]: isIgnoring=$isIgnoring")

                if (!isIgnoring) {
                    Log.w(TAG, "應用程式 [$pkg] 未在電池最佳化白名單內，嘗試進行豁免請求...")
                    if (pkg == context.packageName) {
                        // 自身 App：利用 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 專用 Intent 請求加入白名單
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$pkg")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            context.startActivity(intent)
                            Log.i(TAG, "已發送自身 [$pkg] 電池最佳化豁免請求 Intent")
                        } catch (e: Exception) {
                            Log.e(TAG, "發送自身電池豁免 Intent 失敗，改為開啟設定頁面", e)
                            openBatteryOptimizationSettingsPage(context)
                        }
                    } else {
                        // 第三方 App (Tailscale)：引導至系統電池最佳化設定頁面
                        Log.i(TAG, "為第三方應用 [$pkg] 開啟系統電池最佳化設定頁面")
                        openBatteryOptimizationSettingsPage(context)
                    }
                } else {
                    Log.i(TAG, "應用程式 [$pkg] 已在電池最佳化豁免白名單內")
                }
            } catch (e: Exception) {
                Log.e(TAG, "處理 [$pkg] 電池最佳化豁免時發生例外狀況: ${e.message}", e)
            }
        }
    }

    private fun openBatteryOptimizationSettingsPage(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.i(TAG, "已開啟系統電池最佳化設定頁面")
        } catch (e: Exception) {
            Log.e(TAG, "開啟系統電池最佳化設定頁面失敗", e)
        }
    }

    /**
     * 啟動完整的零接觸部署管道 (下載 APK -> 靜默安裝 -> 政策注入 -> VPN -> Kiosk)
     */
    fun startZeroTouchPipeline(context: Context, authKey: String, apkUrl: String = DEFAULT_TAILSCALE_APK_URL) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val admin = getAdminComponent(context)
        val isDO = dpm?.isDeviceOwnerApp(context.packageName) == true

        Log.i(TAG, "Starting Zero-Touch Pipeline. Is Device Owner: $isDO")

        // 執行電池最佳化豁免 (OcularNode + Tailscale)
        exemptFromBatteryOptimizations(context)

        if (!isDO) {
            Log.w(TAG, "Not Device Owner. Skipping Device Owner特權步驟.")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. 下載 Tailscale APK
                val apkFile = downloadTailscaleApk(context, apkUrl)
                if (apkFile != null && apkFile.exists()) {
                    // 2. 靜默安裝
                    installTailscaleApkSilently(context, apkFile)
                } else {
                    Log.e(TAG, "Tailscale APK 下載失敗，嘗試直接進行 MDM 政策注入")
                    injectTailscaleRestrictionsAndEnableVpn(context, authKey)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Zero Touch Pipeline Exception: ${e.message}", e)
            }
        }
    }

    /**
     * 背景靜默下載 Tailscale APK
     */
    suspend fun downloadTailscaleApk(context: Context, downloadUrl: String): File? = withContext(Dispatchers.IO) {
        try {
            val url = downloadUrl.ifBlank { DEFAULT_TAILSCALE_APK_URL }
            Log.i(TAG, "開始下載 Tailscale APK: $url")

            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "下載 APK 失敗 HTTP ${response.code}")
                    return@withContext null
                }

                val destFile = File(context.cacheDir, "tailscale_download.apk")
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "Tailscale APK 下載成功: ${destFile.absolutePath} (${destFile.length()} bytes)")
                destFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "下載 Tailscale APK 異常", e)
            null
        }
    }

    /**
     * 靜默安裝 APK (使用 PackageInstaller + DO 權限)
     */
    fun installTailscaleApkSilently(context: Context, apkFile: File) {
        try {
            Log.i(TAG, "開始呼叫 PackageInstaller 靜默安裝 Tailscale")
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(TAILSCALE_PACKAGE)
            }

            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            apkFile.inputStream().use { input ->
                session.openWrite("tailscale_install_session", 0, apkFile.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }

            val intent = Intent(context, AdminReceiver::class.java).apply {
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
            Log.i(TAG, "PackageInstaller session 提交完畢，等待安裝結果...")
        } catch (e: Exception) {
            Log.e(TAG, "靜默安裝 Tailscale 異常", e)
        }
    }

    /**
     * MDM 政策注入 (將 Auth Key 注入 Tailscale 的 Application Restrictions) 並啟用 Always-On VPN
     */
    fun injectTailscaleRestrictionsAndEnableVpn(context: Context, authKey: String) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return
        val admin = getAdminComponent(context)

        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.w(TAG, "非 Device Owner，無法注入 ApplicationRestrictions")
            return
        }

        try {
            // 1. 注入 Restriction (Auth Key)
            if (authKey.isNotBlank()) {
                val restrictions = Bundle().apply {
                    putString("authkey", authKey)
                }
                dpm.setApplicationRestrictions(admin, TAILSCALE_PACKAGE, restrictions)
                Log.i(TAG, "成功注入 Tailscale Restriction (AuthKey: ${authKey.take(10)}...)")
            }

            // 2. 強制啟動 Always-On VPN
            try {
                dpm.setAlwaysOnVpnPackage(admin, TAILSCALE_PACKAGE, true)
                Log.i(TAG, "成功設置 Tailscale 為 Always-On VPN")
            } catch (e: Exception) {
                Log.e(TAG, "設置 Always-On VPN 失敗 (Tailscale 可能尚未安裝完成)", e)
            }

        } catch (e: Exception) {
            Log.e(TAG, "MDM 政策注入失敗", e)
        }
    }

    /**
     * 啟動 Kiosk 螢幕死鎖模式
     */
    fun enableKioskMode(activity: Activity) {
        val context = activity.applicationContext
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return
        val admin = getAdminComponent(context)

        try {
            if (dpm.isDeviceOwnerApp(context.packageName)) {
                dpm.setLockTaskPackages(admin, arrayOf(context.packageName))
            }

            if (dpm.isLockTaskPermitted(context.packageName)) {
                activity.startLockTask()
                val settings = SettingsManager(context)
                settings.isKioskModeActive = true
                Log.i(TAG, "已成功啟動 Kiosk 死鎖模式 (Lock Task)")
            } else {
                Log.w(TAG, "未取得 Lock Task 權限，無法鎖定螢幕")
            }
        } catch (e: Exception) {
            Log.e(TAG, "啟用 Kiosk 死鎖模式異常", e)
        }
    }

    /**
     * 停用 Kiosk 螢幕死鎖模式 (逃生門)
     */
    fun disableKioskMode(activity: Activity) {
        try {
            activity.stopLockTask()
            val settings = SettingsManager(activity.applicationContext)
            settings.isKioskModeActive = false
            Toast.makeText(activity, "已順利解除 Kiosk 死鎖模式！", Toast.LENGTH_LONG).show()
            Log.i(TAG, "已解除 Kiosk 死鎖模式 (Lock Task Ended)")
        } catch (e: Exception) {
            Log.e(TAG, "解除 Kiosk 死鎖模式異常", e)
        }
    }

    /**
     * 不依賴額外伺服器、直接讀取 GitHub Releases 的靜默更新機制 (Silent OTA)。
     * 必須在 Coroutine Dispatchers.IO 中執行。
     *
     * @param context 應用程式 Context
     * @param githubOwner GitHub 使用者/組織名稱
     * @param githubRepo GitHub 儲存庫名稱
     */
    suspend fun checkAndSilentUpdate(
        context: Context,
        githubOwner: String,
        githubRepo: String
    ) = withContext(Dispatchers.IO) {
        if (githubOwner.isBlank() || githubRepo.isBlank()) {
            Log.w(TAG, "GitHub Owner 或 Repo 未設定，跳過靜默更新檢查")
            return@withContext
        }

        try {
            Log.i(TAG, "開始檢查 GitHub Releases 靜默更新 ($githubOwner/$githubRepo)...")

            // 1. 檢查版本：發送 GET 請求至 GitHub Releases API
            val apiUrl = "https://api.github.com/repos/$githubOwner/$githubRepo/releases/latest"
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "OcularNode-App")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "GitHub Releases API 請求失敗，HTTP Code: ${response.code}")
                    return@withContext
                }

                val jsonStr = response.body?.string() ?: return@withContext
                val jsonObject = JSONObject(jsonStr)

                val tagName = jsonObject.optString("tag_name", "")
                Log.i(TAG, "取得 GitHub 最新 Release Tag Name: $tagName")

                // 解析遠端版本號 (移除 'v' 與非數字字元後轉為數字)
                val remoteVersionCode = tagName.replace("v", "").replace(".", "").filter { it.isDigit() }.toLongOrNull() ?: 0L

                // 取得本機當前 versionCode (透過 PackageInfoCompat)
                val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
                val currentVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo)

                Log.i(TAG, "當前本機 versionCode: $currentVersionCode, GitHub 最新 versionCode: $remoteVersionCode")

                if (remoteVersionCode > currentVersionCode) {
                    Log.i(TAG, "發現新版本 ($tagName)！準備背景下載 APK 並執行特權靜默更新...")

                    // 2. 背景下載：取得 assets[0].browser_download_url
                    val assets = jsonObject.optJSONArray("assets")
                    if (assets == null || assets.length() == 0) {
                        Log.e(TAG, "Release 中未包含可供下載的 assets APK 檔案")
                        return@withContext
                    }

                    val downloadUrl = assets.getJSONObject(0).optString("browser_download_url", "")
                    if (downloadUrl.isBlank()) {
                        Log.e(TAG, "無法取得 APK 下載 URL")
                        return@withContext
                    }

                    val apkFile = downloadAppApk(context, downloadUrl)
                    if (apkFile != null && apkFile.exists()) {
                        Log.i(TAG, "APK 下載完成，開始執行特權靜默安裝...")

                        // 3. 特權靜默安裝：利用 PackageInstaller 建立 MODE_FULL_INSTALL Session
                        installAppApkSilently(context, apkFile)
                    } else {
                        Log.e(TAG, "APK 下載失敗，取消靜默更新")
                    }
                } else {
                    Log.i(TAG, "當前已是最新版本，無需更新")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "靜默更新檢查過程中發生異常", e)
        }
    }

    /**
     * 背景下載 App 最新 APK 至 cacheDir
     */
    private fun downloadAppApk(context: Context, downloadUrl: String): File? {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder().url(downloadUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "下載更新 APK 失敗 HTTP ${response.code}")
                    return null
                }

                val destFile = File(context.cacheDir, "ocularnode_update.apk")
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "更新 APK 下載完成: ${destFile.absolutePath} (${destFile.length()} bytes)")
                destFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "下載更新 APK 異常", e)
            null
        }
    }

    /**
     * 特權靜默安裝 App APK
     */
    private fun installAppApkSilently(context: Context, apkFile: File) {
        try {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
            }

            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            apkFile.inputStream().use { input ->
                session.openWrite("ocularnode_self_update_session", 0, apkFile.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }

            val intent = Intent(context, AdminReceiver::class.java).apply {
                action = "io.github.iokkai.ocularnode.ACTION_INSTALL_COMPLETE"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            // 安裝完成後系統會自動觸發 MY_PACKAGE_REPLACED 廣播，將由 BootAndPowerReceiver 接管並無縫重啟進入 Kiosk 模式。
            session.commit(pendingIntent.intentSender)
            session.close()
            Log.i(TAG, "自身 PackageInstaller session 提交完畢！安裝完成後系統會自動觸發 MY_PACKAGE_REPLACED 廣播...")
        } catch (e: Exception) {
            Log.e(TAG, "靜默安裝自身 APK 異常", e)
        }
    }
}
