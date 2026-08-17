package io.github.iokkai.ocularnode.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

import android.app.admin.DevicePolicyManager
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

enum class UpdateInstallStage {
    IDLE,
    DOWNLOADING,
    VERIFYING,
    INSTALLING_SILENT,
    PROMPTING_SYSTEM_INSTALL,
    COMPLETED,
    FAILED
}

data class UpdateCheckResult(
    val hasUpdate: Boolean,
    val latestVersionName: String = "",
    val releaseNotes: String = "",
    val downloadUrl: String = "",
    val htmlUrl: String = "",
    val publishedAt: String = "",
    val errorMessage: String? = null
)

/**
 * 靜默與互動式更新管理器 (UpdateManager)
 * 提供直接讀取 GitHub Releases 的檢查更新與 Silent OTA 功能。
 */
object UpdateManager {

    private const val TAG = "UpdateManager"

    /**
     * 檢查 GitHub Releases 最新版本資訊
     */
    suspend fun checkLatestUpdate(
        context: Context,
        githubOwner: String = io.github.iokkai.ocularnode.BuildConfig.GITHUB_OWNER,
        githubRepo: String = io.github.iokkai.ocularnode.BuildConfig.GITHUB_REPO
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        if (githubOwner.isBlank() || githubRepo.isBlank()) {
            return@withContext UpdateCheckResult(
                hasUpdate = false,
                errorMessage = "GitHub Owner or Repo is not configured"
            )
        }

        try {
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
                    return@withContext UpdateCheckResult(
                        hasUpdate = false,
                        errorMessage = "HTTP ${response.code}: ${response.message}"
                    )
                }

                val jsonStr = response.body?.string() ?: return@withContext UpdateCheckResult(
                    hasUpdate = false,
                    errorMessage = "Empty response from GitHub API"
                )

                val jsonObject = JSONObject(jsonStr)
                val tagName = jsonObject.optString("tag_name", "").ifBlank { jsonObject.optString("name", "") }
                val releaseNotes = jsonObject.optString("body", "")
                val htmlUrl = jsonObject.optString("html_url", "https://github.com/$githubOwner/$githubRepo/releases/latest")
                val publishedAt = jsonObject.optString("published_at", "")

                val assets = jsonObject.optJSONArray("assets")
                val downloadUrl = if (assets != null && assets.length() > 0) {
                    ZeroTouchProvisionManager.findBestMatchingApkUrl(assets) ?: htmlUrl
                } else {
                    htmlUrl
                }

                // 比對版本號
                val remoteVersionCode = parseVersionCodeFromTag(tagName)
                val currentVersionName = io.github.iokkai.ocularnode.BuildConfig.VERSION_NAME

                val packageInfo = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
                    } else {
                        context.packageManager.getPackageInfo(context.packageName, 0)
                    }
                } catch (e: Exception) {
                    null
                }
                val currentVersionCode = packageInfo?.let { PackageInfoCompat.getLongVersionCode(it) }
                    ?: io.github.iokkai.ocularnode.BuildConfig.VERSION_CODE.toLong()

                val isNewer = isRemoteNewer(
                    remoteTagName = tagName,
                    remoteVersionCode = remoteVersionCode,
                    currentVersionName = currentVersionName,
                    currentVersionCode = currentVersionCode
                )

                Log.i(TAG, "Current: $currentVersionName (Code: $currentVersionCode), Remote: $tagName (Code: $remoteVersionCode), isNewer=$isNewer")

                return@withContext UpdateCheckResult(
                    hasUpdate = isNewer,
                    latestVersionName = if (tagName.startsWith("v")) tagName else "v$tagName",
                    releaseNotes = releaseNotes,
                    downloadUrl = downloadUrl,
                    htmlUrl = htmlUrl,
                    publishedAt = publishedAt
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check update", e)
            return@withContext UpdateCheckResult(
                hasUpdate = false,
                errorMessage = e.localizedMessage ?: e.message ?: "Network connection error"
            )
        }
    }

    /**
     * 向 GitHub Releases 檢查最新版本並進行靜默更新。
     */
    suspend fun checkAndSilentUpdate(
        context: Context,
        githubOwner: String = io.github.iokkai.ocularnode.BuildConfig.GITHUB_OWNER,
        githubRepo: String = io.github.iokkai.ocularnode.BuildConfig.GITHUB_REPO
    ) {
        ZeroTouchProvisionManager.checkAndSilentUpdate(context, githubOwner, githubRepo)
    }

    /**
     * 在 App 內直接串流下載最新 APK、驗證簽名並依據權限自動進行靜默安裝或拉起系統安裝畫面。
     *
     * @param context 應用程式 Context
     * @param downloadUrl APK 直接下載 URL
     * @param onProgress 下載進度回呼 (進度 0.0f..1.0f, 已下載 byte 數, 總 byte 數)
     * @param onStageChange 階段變化回呼 (DOWNLOADING, VERIFYING, INSTALLING..., FAILED)
     */
    suspend fun downloadAndInstallApk(
        context: Context,
        downloadUrl: String,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _, _ -> },
        onStageChange: (stage: UpdateInstallStage, message: String?) -> Unit = { _, _ -> }
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (downloadUrl.isBlank()) {
                val err = "下載連結無效 (Download URL is invalid)"
                onStageChange(UpdateInstallStage.FAILED, err)
                return@withContext Result.failure(IllegalArgumentException(err))
            }

            Log.i(TAG, "開始在 App 內直接下載更新 APK: $downloadUrl")
            onStageChange(UpdateInstallStage.DOWNLOADING, null)

            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "OcularNode-App")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val err = "下載失敗，HTTP Code: ${response.code} (${response.message})"
                onStageChange(UpdateInstallStage.FAILED, err)
                return@withContext Result.failure(Exception(err))
            }

            val body = response.body ?: run {
                val err = "伺服器未回傳 APK 內容"
                onStageChange(UpdateInstallStage.FAILED, err)
                return@withContext Result.failure(Exception(err))
            }

            val totalBytes = body.contentLength()
            val destFile = File(context.cacheDir, "ocularnode_update.apk")
            if (destFile.exists()) destFile.delete()

            body.byteStream().use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalDownloaded = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalDownloaded += bytesRead
                        val progress = if (totalBytes > 0) totalDownloaded.toFloat() / totalBytes else -1f
                        onProgress(progress, totalDownloaded, totalBytes)
                    }
                    output.flush()
                }
            }

            Log.i(TAG, "APK 下載完成: ${destFile.absolutePath} (${destFile.length()} bytes)")

            // 2. 驗證簽名指紋 (S-5 安全防護)
            onStageChange(UpdateInstallStage.VERIFYING, null)
            val isValid = ZeroTouchProvisionManager.verifyApkSignature(context, destFile)
            if (!isValid) {
                destFile.delete()
                val err = "安全驗證失敗：下載之 APK 簽名證書與當前 App 不一致，已終止安裝！"
                Log.e(TAG, err)
                onStageChange(UpdateInstallStage.FAILED, err)
                return@withContext Result.failure(SecurityException(err))
            }

            // 3. 判斷權限並執行安裝
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val isDeviceOwner = dpm?.isDeviceOwnerApp(context.packageName) == true

            if (isDeviceOwner) {
                Log.i(TAG, "具備 Device Owner 特權，執行 PackageInstaller 靜默無感更新...")
                onStageChange(UpdateInstallStage.INSTALLING_SILENT, null)
                ZeroTouchProvisionManager.installAppApkSilently(context, destFile)
                onStageChange(UpdateInstallStage.COMPLETED, null)
            } else {
                Log.i(TAG, "一般模式，透過 FileProvider 拉起系統原生安裝畫面...")
                onStageChange(UpdateInstallStage.PROMPTING_SYSTEM_INSTALL, null)
                val apkUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    destFile
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                context.startActivity(intent)
                onStageChange(UpdateInstallStage.COMPLETED, null)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "下載與安裝流程發生異常", e)
            val errMsg = e.localizedMessage ?: e.message ?: "下載或安裝失敗"
            onStageChange(UpdateInstallStage.FAILED, errMsg)
            Result.failure(e)
        }
    }

    /**
     * 從 Release Tag 解析版本數字代碼 (例如 "v1.2.0" -> 120L)
     */
    fun parseVersionCodeFromTag(tagName: String): Long {
        return tagName.replace("v", "").replace(".", "").filter { it.isDigit() }.toLongOrNull() ?: 0L
    }

    /**
     * 比較遠端版本與本地版本，判斷是否為更新的版本
     */
    fun isRemoteNewer(
        remoteTagName: String,
        remoteVersionCode: Long = parseVersionCodeFromTag(remoteTagName),
        currentVersionName: String,
        currentVersionCode: Long
    ): Boolean {
        return if (remoteVersionCode > 0 && currentVersionCode > 0) {
            remoteVersionCode > currentVersionCode
        } else {
            val cleanTag = remoteTagName.removePrefix("v").trim()
            val cleanCurrent = currentVersionName.removePrefix("v").trim()
            cleanTag.isNotBlank() && cleanTag != cleanCurrent
        }
    }
}
