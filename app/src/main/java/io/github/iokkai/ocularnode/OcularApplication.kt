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
    }
}
