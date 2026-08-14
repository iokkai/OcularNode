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
                val remoteVersionCode = tagName.replace("v", "").replace(".", "").filter { it.isDigit() }.toLongOrNull() ?: 0L
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

                val isNewer = if (remoteVersionCode > 0 && currentVersionCode > 0) {
                    remoteVersionCode > currentVersionCode
                } else {
                    val cleanTag = tagName.removePrefix("v").trim()
                    val cleanCurrent = currentVersionName.removePrefix("v").trim()
                    cleanTag.isNotBlank() && cleanTag != cleanCurrent
                }

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
}
