package io.github.iokkai.ocularnode.util

import android.content.Context

/**
 * 靜默更新管理器 (UpdateManager)
 * 提供直接讀取 GitHub Releases 的 Silent OTA 靜默更新功能。
 */
object UpdateManager {

    /**
     * 向 GitHub Releases 檢查最新版本並進行靜默更新。
     *
     * @param context 應用程式 Context
     * @param githubOwner GitHub 使用者或組織名稱
     * @param githubRepo GitHub 儲存庫名稱
     */
    suspend fun checkAndSilentUpdate(
        context: Context,
        githubOwner: String = io.github.iokkai.ocularnode.BuildConfig.GITHUB_OWNER,
        githubRepo: String = io.github.iokkai.ocularnode.BuildConfig.GITHUB_REPO
    ) {
        ZeroTouchProvisionManager.checkAndSilentUpdate(context, githubOwner, githubRepo)
    }
}
