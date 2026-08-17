package io.github.iokkai.ocularnode.di

import android.content.Context
import io.github.iokkai.ocularnode.audio.AudioEngine
import io.github.iokkai.ocularnode.data.AppDatabase
import io.github.iokkai.ocularnode.data.CameraDeviceDao
import io.github.iokkai.ocularnode.data.MotionEventDao
import io.github.iokkai.ocularnode.data.SettingsDataStore
import io.github.iokkai.ocularnode.data.SettingsManager

/**
 * 應用程式核心依賴注入容器與模組定義。
 * 統一管理 SettingsManager、SettingsDataStore、Room 資料庫與 AudioEngine 單例生命週期。
 */
class AppContainer(private val context: Context) {

    val settingsManager: SettingsManager by lazy {
        SettingsManager.getInstance(context)
    }

    val settingsDataStore: SettingsDataStore by lazy {
        SettingsDataStore.getInstance(context)
    }

    val appDatabase: AppDatabase by lazy {
        AppDatabase.getDatabase(context)
    }

    val cameraDeviceDao: CameraDeviceDao by lazy {
        appDatabase.cameraDeviceDao()
    }

    val motionEventDao: MotionEventDao by lazy {
        appDatabase.motionEventDao()
    }

    val audioEngine: AudioEngine by lazy {
        AudioEngine()
    }
}
