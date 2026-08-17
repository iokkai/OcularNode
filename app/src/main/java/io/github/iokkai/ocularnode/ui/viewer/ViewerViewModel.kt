package io.github.iokkai.ocularnode.ui.viewer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.iokkai.ocularnode.audio.AudioEngine
import io.github.iokkai.ocularnode.client.CameraStreamClient
import io.github.iokkai.ocularnode.data.AppDatabase
import io.github.iokkai.ocularnode.data.CameraDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AdaptiveModeState(
    val isEnabled: Boolean = true,
    val isDowngraded: Boolean = false,
    val currentResolution: String = "720p",
    val currentQuality: Int = 60,
    val targetFps: Int = 30,
    val labelText: String = "⚡ Adaptive: Auto",
    val reasonText: String = "",
    val pingMs: Int = -1,
    val fps: Int = 0
)

class ViewerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val cameraDao = db.cameraDeviceDao()
    val settingsManager = io.github.iokkai.ocularnode.data.SettingsManager.getInstance(application)

    val audioEngine = AudioEngine()
    val streamClient = CameraStreamClient(audioEngine)

    private val _adaptiveState = MutableStateFlow(AdaptiveModeState())
    val adaptiveState: StateFlow<AdaptiveModeState> = _adaptiveState.asStateFlow()

    private var adaptiveJob: Job? = null

    private val _isTailscaleConnected = MutableStateFlow(false)
    val isTailscaleConnected: StateFlow<Boolean> = _isTailscaleConnected.asStateFlow()

    private val _isVpnActive = MutableStateFlow(false)
    val isVpnActive: StateFlow<Boolean> = _isVpnActive.asStateFlow()

    private val _tailscaleIp = MutableStateFlow<String?>(null)
    val tailscaleIp: StateFlow<String?> = _tailscaleIp.asStateFlow()

    init {
        viewModelScope.launch {
            io.github.iokkai.ocularnode.util.NetworkUtils.observeNetworkStatus(application).collect { ipInfo ->
                _isTailscaleConnected.value = ipInfo.isTailscaleConnected
                _isVpnActive.value = ipInfo.isVpnActive
                _tailscaleIp.value = ipInfo.tailscaleIp
            }
        }
    }

    fun refreshNetworkInfo() {
        val ipInfo = io.github.iokkai.ocularnode.util.NetworkUtils.getIpAddresses(getApplication())
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
                name = name.ifBlank { "Camera Node" },
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
        if (_adaptiveState.value.isEnabled) {
            startAdaptiveResolutionMonitor()
        }
    }

    fun setAdaptiveModeEnabled(enabled: Boolean) {
        _adaptiveState.value = _adaptiveState.value.copy(
            isEnabled = enabled,
            labelText = if (enabled) "⚡ Adaptive Mode: Auto" else "⚡ Adaptive: Off"
        )
        if (enabled) {
            startAdaptiveResolutionMonitor()
        } else {
            adaptiveJob?.cancel()
            adaptiveJob = null
        }
    }

    private fun startAdaptiveResolutionMonitor() {
        adaptiveJob?.cancel()
        adaptiveJob = viewModelScope.launch(Dispatchers.IO) {
            var lagConsecutiveCount = 0
            var severeConsecutiveCount = 0
            var recoveryConsecutiveCount = 0
            var lastDowngradeTime = 0L

            while (isActive) {
                delay(2000L)

                val camera = _selectedCamera.value ?: continue
                val isConnected = streamClient.isConnected.value
                val isAdaptiveEnabled = _adaptiveState.value.isEnabled

                if (!isConnected || !isAdaptiveEnabled) {
                    recoveryConsecutiveCount = 0
                    lagConsecutiveCount = 0
                    severeConsecutiveCount = 0
                    continue
                }

                val statusJson = streamClient.cameraStatusJson.value
                val pingMs = statusJson?.optInt("pingMs", -1) ?: -1
                val currentFps = streamClient.fps.value
                val lastFrameTs = streamClient.lastFrameTimestamp.value
                val now = System.currentTimeMillis()
                val timeSinceLastFrame = if (lastFrameTs > 0) now - lastFrameTs else 0L

                val serverRes = statusJson?.optString("resolution", "720p") ?: "720p"
                val serverQual = statusJson?.optInt("quality", 60) ?: 60

                // Dynamic Adaptive FPS & Resolution Trigger Conditions
                val isSevereLowFps = (currentFps in 1..5 && lastFrameTs > 0) || (currentFps == 0 && isConnected && now - lastDowngradeTime > 3000)
                val isModerateLowFps = currentFps in 6..12 && lastFrameTs > 0
                val isHighPing = pingMs > 280
                val isSeverePing = pingMs > 450
                val isFrameLag = lastFrameTs > 0 && timeSinceLastFrame > 1500
                val isSevereFrameLag = lastFrameTs > 0 && timeSinceLastFrame > 2500

                val needsLagAction = isModerateLowFps || isHighPing || isFrameLag
                val needsSevereAction = isSevereLowFps || isSeverePing || isSevereFrameLag

                if (needsSevereAction) {
                    severeConsecutiveCount++
                    lagConsecutiveCount++
                    recoveryConsecutiveCount = 0
                } else if (needsLagAction) {
                    lagConsecutiveCount++
                    severeConsecutiveCount = 0
                    recoveryConsecutiveCount = 0
                } else {
                    lagConsecutiveCount = 0
                    severeConsecutiveCount = 0
                    if (pingMs in 1..200 && currentFps >= 15 && timeSinceLastFrame < 1000) {
                        recoveryConsecutiveCount++
                    } else {
                        recoveryConsecutiveCount = 0
                    }
                }

                // Severe Downgrade: FPS <= 5 or High Ping > 450ms -> Switch to 360p Quality 20% & Target 15 FPS
                if (severeConsecutiveCount >= 1 && (serverRes != "360p" || serverQual > 20) && (now - lastDowngradeTime > 3000)) {
                    lastDowngradeTime = now
                    streamClient.sendControlCommand(camera, "resolution", "360p")
                    streamClient.sendControlCommand(camera, "quality", "20")
                    streamClient.sendControlCommand(camera, "fps", "15")

                    val reason = when {
                        isSevereLowFps -> "FPS critically low ($currentFps FPS)"
                        isSeverePing -> "High Ping (${pingMs}ms)"
                        isSevereFrameLag -> "Stuttering (${timeSinceLastFrame}ms)"
                        else -> "Network & FPS loss"
                    }
                    _adaptiveState.value = AdaptiveModeState(
                        isEnabled = true,
                        isDowngraded = true,
                        currentResolution = "360p",
                        currentQuality = 20,
                        targetFps = 15,
                        labelText = "⚡ Adaptive: 360p ($reason / Quality 20%)",
                        reasonText = reason,
                        pingMs = pingMs,
                        fps = currentFps
                    )
                }
                // Moderate Downgrade: FPS 6..12 or Ping > 280ms -> Switch to 480p Quality 30% & Target 20 FPS
                else if (lagConsecutiveCount >= 2 && !_adaptiveState.value.isDowngraded && (now - lastDowngradeTime > 3000)) {
                    lastDowngradeTime = now
                    streamClient.sendControlCommand(camera, "resolution", "480p")
                    streamClient.sendControlCommand(camera, "quality", "30")
                    streamClient.sendControlCommand(camera, "fps", "20")

                    val reason = when {
                        isModerateLowFps -> "FPS low ($currentFps FPS)"
                        isHighPing -> "High Ping (${pingMs}ms)"
                        isFrameLag -> "Frame lag"
                        else -> "Network & FPS instability"
                    }
                    _adaptiveState.value = AdaptiveModeState(
                        isEnabled = true,
                        isDowngraded = true,
                        currentResolution = "480p",
                        currentQuality = 30,
                        targetFps = 20,
                        labelText = "⚡ Adaptive: 480p ($reason / Quality 30%)",
                        reasonText = reason,
                        pingMs = pingMs,
                        fps = currentFps
                    )
                }
                // Auto Recovery: Restore to 720p Quality 60% & 30 FPS after 5 consecutive stable checks (10s)
                else if (recoveryConsecutiveCount >= 5 && _adaptiveState.value.isDowngraded) {
                    recoveryConsecutiveCount = 0
                    lastDowngradeTime = now
                    streamClient.sendControlCommand(camera, "resolution", "720p")
                    streamClient.sendControlCommand(camera, "quality", "60")
                    streamClient.sendControlCommand(camera, "fps", "30")

                    _adaptiveState.value = AdaptiveModeState(
                        isEnabled = true,
                        isDowngraded = false,
                        currentResolution = "720p",
                        currentQuality = 60,
                        targetFps = 30,
                        labelText = "⚡ Adaptive: Auto (${serverRes} / $currentFps FPS)",
                        reasonText = "Smooth connection restored",
                        pingMs = pingMs,
                        fps = currentFps
                    )
                } else {
                    val cur = _adaptiveState.value
                    val label = if (cur.isDowngraded) {
                        "⚡ Adaptive: ${serverRes} (${cur.reasonText})"
                    } else {
                        "⚡ Adaptive: Auto (${serverRes} / ${currentFps} FPS)"
                    }
                    _adaptiveState.value = cur.copy(
                        currentResolution = serverRes,
                        currentQuality = serverQual,
                        labelText = label,
                        pingMs = pingMs,
                        fps = currentFps
                    )
                }
            }
        }
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
        adaptiveJob?.cancel()
        adaptiveJob = null
        streamClient.disconnect()
        _selectedCamera.value = null
    }

    override fun onCleared() {
        super.onCleared()
        adaptiveJob?.cancel()
        adaptiveJob = null
        streamClient.disconnect()
        audioEngine.release()
    }
}
