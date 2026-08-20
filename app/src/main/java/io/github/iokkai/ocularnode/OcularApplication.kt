package io.github.iokkai.ocularnode

import android.app.Application
import io.github.iokkai.ocularnode.di.AppContainer

/**
 * 應用程式 Application 類別，持有全域單例 AppContainer 容器。
 */
class OcularApplication : Application() {

    val appContainer: AppContainer by lazy {
        AppContainer(this)
    }

    override fun onCreate() {
        super.onCreate()
        
        // 設定全域崩潰攔截器
        val defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashMsg = "FATAL EXCEPTION in thread ${thread.name}\n${throwable.stackTraceToString()}"
                io.github.iokkai.ocularnode.util.AppLogger.e("CrashHandler", crashMsg, throwable)
                
                // 寫入本地檔案，方便測試機直接用檔案總管查看
                val logFile = java.io.File(getExternalFilesDir(null), "crash_log.txt")
                val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                logFile.appendText("[$timestamp]\n$crashMsg\n\n-----------------\n\n")
            } catch (e: Exception) {
                android.util.Log.e("CrashHandler", "Error writing crash log", e)
            } finally {
                // 將崩潰交還給系統處理 (讓 App 正常閃退)
                defaultExceptionHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
