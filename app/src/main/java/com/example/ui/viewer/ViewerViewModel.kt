package com.example.ui.viewer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioEngine
import com.example.client.CameraStreamClient
import com.example.data.AppDatabase
import com.example.data.CameraDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ViewerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val cameraDao = db.cameraDeviceDao()
    val settingsManager = com.example.data.SettingsManager(application)

    val audioEngine = AudioEngine()
    val streamClient = CameraStreamClient(audioEngine)

    private val _isTailscaleConnected = MutableStateFlow(false)
    val isTailscaleConnected: StateFlow<Boolean> = _isTailscaleConnected.asStateFlow()

    private val _isVpnActive = MutableStateFlow(false)
    val isVpnActive: StateFlow<Boolean> = _isVpnActive.asStateFlow()

    private val _tailscaleIp = MutableStateFlow<String?>(null)
    val tailscaleIp: StateFlow<String?> = _tailscaleIp.asStateFlow()

    init {
        viewModelScope.launch {
            com.example.util.NetworkUtils.observeNetworkStatus(application).collect { ipInfo ->
                _isTailscaleConnected.value = ipInfo.isTailscaleConnected
                _isVpnActive.value = ipInfo.isVpnActive
                _tailscaleIp.value = ipInfo.tailscaleIp
            }
        }
    }

    fun refreshNetworkInfo() {
        val ipInfo = com.example.util.NetworkUtils.getIpAddresses(getApplication())
        _isTailscaleConnected.value = ipInfo.isTailscaleConnected
        _isVpnActive.value = ipInfo.isVpnActive
        _tailscaleIp.value = ipInfo.tailscaleIp
    }

    val cameraList: StateFlow<List<CameraDevice>> = cameraDao.getAllCameras()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedCamera = MutableStateFlow<CameraDevice?>(null)
    val selectedCamera: StateFlow<CameraDevice?> = _selectedCamera.asStateFlow()

    fun addCamera(name: String, ipAddress: String, port: Int = 8080) {
        viewModelScope.launch(Dispatchers.IO) {
            val camera = CameraDevice(
                name = name.ifBlank { "鏡頭裝置" },
                ipAddress = ipAddress.trim(),
                port = port
            )
            cameraDao.insertCamera(camera)
        }
    }

    fun updateCamera(camera: CameraDevice) {
        viewModelScope.launch(Dispatchers.IO) {
            cameraDao.updateCamera(camera)
        }
    }

    fun deleteCamera(camera: CameraDevice) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_selectedCamera.value?.id == camera.id) {
                disconnectCamera()
            }
            cameraDao.deleteCamera(camera)
        }
    }

    fun selectAndConnect(camera: CameraDevice) {
        _selectedCamera.value = camera
        val botToken = settingsManager.telegramBotToken
        val chatId = settingsManager.telegramChatId
        streamClient.connect(camera, viewModelScope, botToken, chatId)
    }

    fun syncTelegramToCurrentCamera() {
        val camera = _selectedCamera.value ?: return
        val botToken = settingsManager.telegramBotToken
        val chatId = settingsManager.telegramChatId
        viewModelScope.launch {
            streamClient.syncTelegramConfig(camera, botToken, chatId)
        }
    }

    suspend fun sendControlCommandSuspend(command: String, value: String): Boolean {
        val camera = _selectedCamera.value ?: return false
        return streamClient.sendControlCommand(camera, command, value)
    }

    fun sendControlCommand(command: String, value: String) {
        val camera = _selectedCamera.value ?: return
        viewModelScope.launch {
            streamClient.sendControlCommand(camera, command, value)
        }
    }

    suspend fun sendControlCommandToCameraSuspend(camera: CameraDevice, command: String, value: String): Boolean {
        return streamClient.sendControlCommand(camera, command, value)
    }

    fun sendControlCommandToCamera(camera: CameraDevice, command: String, value: String) {
        viewModelScope.launch {
            streamClient.sendControlCommand(camera, command, value)
        }
    }

    suspend fun saveRemoteConfig(camera: CameraDevice, draftConfigJson: String): Boolean {
        return streamClient.postRemoteConfig(camera, draftConfigJson)
    }

    suspend fun fetchRemoteConfig(camera: CameraDevice): org.json.JSONObject? {
        return streamClient.fetchRemoteConfig(camera)
    }

    suspend fun fetchCameraStatus(camera: CameraDevice): org.json.JSONObject? {
        return streamClient.fetchCameraStatus(camera)
    }

    suspend fun fetchRemoteLogs(camera: CameraDevice): List<String> {
        return streamClient.fetchRemoteLogs(camera)
    }

    fun toggleAudioListening() {
        val camera = _selectedCamera.value ?: return
        if (streamClient.isListeningAudio.value) {
            streamClient.stopListeningAudio()
        } else {
            streamClient.startListeningAudio(camera, viewModelScope, getApplication())
        }
    }

    fun toggleAudioSpeaking() {
        val camera = _selectedCamera.value ?: return
        if (streamClient.isSpeakingAudio.value) {
            streamClient.stopSpeakingAudio()
        } else {
            streamClient.startSpeakingAudio(camera, viewModelScope)
        }
    }

    fun onResume() {
        if (_selectedCamera.value != null) {
            streamClient.onResume()
        }
    }

    fun onPause() {
        if (_selectedCamera.value != null) {
            streamClient.onPause()
        }
    }

    fun disconnectCamera() {
        streamClient.disconnect()
        _selectedCamera.value = null
    }

    override fun onCleared() {
        super.onCleared()
        streamClient.disconnect()
        audioEngine.release()
    }
}
