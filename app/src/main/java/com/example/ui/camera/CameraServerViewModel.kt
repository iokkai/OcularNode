package com.example.ui.camera

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SettingsManager
import com.example.service.CameraStreamService
import com.example.util.NetworkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CameraServerViewModel(application: Application) : AndroidViewModel(application) {

    val settingsManager = SettingsManager(application)

    private var cameraService: CameraStreamService? = null
    private var isBound = false

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _isBlackScreenActive = MutableStateFlow(false)
    val isBlackScreenActive: StateFlow<Boolean> = _isBlackScreenActive.asStateFlow()

    private val _serverUrl = MutableStateFlow("未啟動")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _tailscaleIp = MutableStateFlow<String?>(null)
    val tailscaleIp: StateFlow<String?> = _tailscaleIp.asStateFlow()

    private val _localIp = MutableStateFlow<String?>(null)
    val localIp: StateFlow<String?> = _localIp.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as CameraStreamService.LocalBinder
            cameraService = localBinder.getService()
            isBound = true
            _isServiceRunning.value = true
            refreshNetworkInfo()
            cameraService?.startServer()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            cameraService = null
            isBound = false
            _isServiceRunning.value = false
        }
    }

    fun startStreamService() {
        val context = getApplication<Application>()
        refreshNetworkInfo()
        val intent = Intent(context, CameraStreamService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun stopStreamService() {
        val context = getApplication<Application>()
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
        }
        val intent = Intent(context, CameraStreamService::class.java)
        context.stopService(intent)
        _isServiceRunning.value = false
    }

    fun refreshNetworkInfo() {
        val ipInfo = NetworkUtils.getIpAddresses()
        _tailscaleIp.value = ipInfo.tailscaleIp
        _localIp.value = ipInfo.localIp

        val activeIp = ipInfo.tailscaleIp ?: ipInfo.localIp ?: "127.0.0.1"
        _serverUrl.value = "http://$activeIp:${settingsManager.serverPort}"
    }

    fun toggleBlackScreen(active: Boolean) {
        _isBlackScreenActive.value = active
    }

    fun toggleCameraLens() {
        cameraService?.let {
            it.cameraHelper.switchCamera(it)
        }
    }

    fun toggleTorch() {
        cameraService?.let {
            it.cameraHelper.setTorch(!it.cameraHelper.isTorchOn)
        }
    }

    private val _currentResolution = MutableStateFlow(settingsManager.defaultResolution)
    val currentResolution: StateFlow<String> = _currentResolution.asStateFlow()

    private val _currentQuality = MutableStateFlow(settingsManager.defaultQuality)
    val currentQuality: StateFlow<Int> = _currentQuality.asStateFlow()

    private val _isMotionEnabled = MutableStateFlow(settingsManager.motionDetectionEnabled)
    val isMotionEnabled: StateFlow<Boolean> = _isMotionEnabled.asStateFlow()

    private val _isMlKitFilterEnabled = MutableStateFlow(settingsManager.mlKitFilterEnabled)
    val isMlKitFilterEnabled: StateFlow<Boolean> = _isMlKitFilterEnabled.asStateFlow()

    private val _operatingMode = MutableStateFlow(settingsManager.operatingMode)
    val operatingMode: StateFlow<String> = _operatingMode.asStateFlow()

    fun setOperatingMode(mode: String) {
        settingsManager.operatingMode = mode
        _operatingMode.value = mode
        cameraService?.updateOperatingMode(mode)
    }

    fun setQuality(quality: Int) {
        settingsManager.defaultQuality = quality
        _currentQuality.value = quality
        cameraService?.let {
            it.cameraHelper.jpegQuality = quality
        }
    }

    fun setResolution(resolution: String) {
        settingsManager.defaultResolution = resolution
        _currentResolution.value = resolution
        cameraService?.let {
            it.cameraHelper.setResolution(resolution)
        }
    }

    fun setNightVisionMode(mode: String) {
        settingsManager.nightVisionMode = mode
        cameraService?.let {
            it.cameraHelper.nightVisionMode = mode
        }
    }

    fun toggleMotionDetection(enable: Boolean) {
        settingsManager.motionDetectionEnabled = enable
        _isMotionEnabled.value = enable
        cameraService?.let {
            it.cameraHelper.isMotionDetectionEnabled = enable
        }
    }

    fun toggleMlKitFilter(enable: Boolean) {
        settingsManager.mlKitFilterEnabled = enable
        _isMlKitFilterEnabled.value = enable
    }

    fun getCameraService(): CameraStreamService? = cameraService

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            try {
                getApplication<Application>().unbindService(serviceConnection)
            } catch (_: Exception) {}
        }
    }
}
