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
    val isEnabled: Boolean = false,
    val isDowngraded: Boolean = false,
    val currentResolution: String = "360p",
    val currentQuality: Int = 30,
    val targetFps: Int = 15,
    val labelText: String = "⚡ 360p (手動模式)",
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

    private val _devicesExpiryMap = MutableStateFlow<Map<String, io.github.iokkai.ocularnode.util.TailscaleDeviceExpiryInfo>>(emptyMap())
    val devicesExpiryMap: StateFlow<Map<String, io.github.iokkai.ocularnode.util.TailscaleDeviceExpiryInfo>> = _devicesExpiryMap.asStateFlow()

    private val _isDisablingKeyExpiry = MutableStateFlow(false)
    val isDisablingKeyExpiry: StateFlow<Boolean> = _isDisablingKeyExpiry.asStateFlow()

    init {
        viewModelScope.launch {
            io.github.iokkai.ocularnode.util.NetworkUtils.observeNetworkStatus(application).collect { ipInfo ->
                _isTailscaleConnected.value = ipInfo.isTailscaleConnected
                _isVpnActive.value = ipInfo.isVpnActive
                _tailscaleIp.value = ipInfo.tailscaleIp
            }
        }
        viewModelScope.launch {
            streamClient.cameraStatusJson.collect { json ->
                if (!_adaptiveState.value.isEnabled && json != null) {
                    val res = json.optString("resolution", "360p")
                    val qual = json.optInt("quality", 30)
                    _adaptiveState.value = _adaptiveState.value.copy(
                        currentResolution = res,
                        currentQuality = qual,
                        labelText = "⚡ $res ($qual% 手動)"
                    )
                }
            }
        }
        fetchTailscaleDevicesStatus()
    }

    fun fetchTailscaleDevicesStatus() {
        val apiKey = settingsManager.tailscaleApiKey
        if (apiKey.isBlank() || apiKey.startsWith("tskey-auth")) return

        viewModelScope.launch {
            val result = io.github.iokkai.ocularnode.util.TailscaleApiHelper.fetchDevicesExpiryInfo(apiKey)
            result.onSuccess { devices ->
                val map = mutableMapOf<String, io.github.iokkai.ocularnode.util.TailscaleDeviceExpiryInfo>()
                for (device in devices) {
                    for (ip in device.ipAddresses) {
                        map[ip] = device
                    }
                }
                _devicesExpiryMap.value = map
            }
        }
    }

    fun disableKeyExpiry(deviceId: String, onComplete: (Boolean, String?) -> Unit) {
        val apiKey = settingsManager.tailscaleApiKey
        if (apiKey.isBlank() || apiKey.startsWith("tskey-auth")) {
            onComplete(false, "API Key required")
            return
        }

        viewModelScope.launch {
            _isDisablingKeyExpiry.value = true
            val result = io.github.iokkai.ocularnode.util.TailscaleApiHelper.disableDeviceKeyExpiry(apiKey, deviceId)
            _isDisablingKeyExpiry.value = false
            result.onSuccess {
                fetchTailscaleDevicesStatus()
                onComplete(true, null)
            }.onFailure { e ->
                onComplete(false, e.localizedMessage)
            }
        }
    }

    fun refreshNetworkInfo() {
        val ipInfo = io.github.iokkai.ocularnode.util.NetworkUtils.getIpAddresses(getApplication())
        _isTailscaleConnected.value = ipInfo.isTailscaleConnected
        _isVpnActive.value = ipInfo.isVpnActive
        _tailscaleIp.value = ipInfo.tailscaleIp
        fetchTailscaleDevicesStatus()
    }

    private val _cameras = MutableStateFlow<List<CameraDevice>>(emptyList())
    val cameras: StateFlow<List<CameraDevice>> = _cameras.asStateFlow()

    private val _selectedCamera = MutableStateFlow<CameraDevice?>(null)
    val selectedCamera: StateFlow<CameraDevice?> = _selectedCamera.asStateFlow()

    fun loadCameras() {
        viewModelScope.launch {
            cameraDao.getAllCameras().collect {
                _cameras.value = it
            }
        }
    }

    fun addCamera(name: String, ipAddress: String, port: Int = 8080) {
        viewModelScope.launch {
            val camera = CameraDevice(name = name, ipAddress = ipAddress, port = port)
            cameraDao.insertCamera(camera)
        }
    }

    fun updateCamera(camera: CameraDevice) {
        viewModelScope.launch {
            cameraDao.updateCamera(camera)
        }
    }

    fun deleteCamera(camera: CameraDevice) {
        viewModelScope.launch {
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
        val curRes = streamClient.cameraStatusJson.value?.optString("resolution", _adaptiveState.value.currentResolution) ?: _adaptiveState.value.currentResolution
        val curQual = streamClient.cameraStatusJson.value?.optInt("quality", _adaptiveState.value.currentQuality) ?: _adaptiveState.value.currentQuality
        _adaptiveState.value = _adaptiveState.value.copy(
            isEnabled = enabled,
            isDowngraded = false,
            currentResolution = curRes,
            currentQuality = curQual,
            labelText = if (enabled) "⚡ 自適應: $curRes" else "⚡ 解析度: $curRes ($curQual% 手動)"
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
            var lastUpgradeTime = 0L
            var flappingLockoutUntil = 0L

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

                val serverRes = statusJson?.optString("resolution", "360p") ?: "360p"
                val serverQual = statusJson?.optInt("quality", 30) ?: 30

                val currentLevel = when (serverRes) {
                    "360p" -> 0
                    "480p" -> 1
                    "720p" -> 2
                    "1080p" -> 3
                    else -> 0
                }

                val isSevereLowFps = (currentFps in 1..5 && lastFrameTs > 0) || (currentFps == 0 && isConnected && now - lastDowngradeTime > 5000)
                val isModerateLowFps = currentFps in 6..11 && lastFrameTs > 0
                val isHighPing = pingMs > 350
                val isSeverePing = pingMs > 600
                val isFrameLag = lastFrameTs > 0 && timeSinceLastFrame > 2000
                val isSevereFrameLag = lastFrameTs > 0 && timeSinceLastFrame > 3500

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
                    if (pingMs in 1..150 && currentFps >= 14 && timeSinceLastFrame < 800 && now > flappingLockoutUntil) {
                        recoveryConsecutiveCount++
                    } else {
                        recoveryConsecutiveCount = 0
                    }
                }

                if (severeConsecutiveCount >= 2 && currentLevel > 0 && (now - lastDowngradeTime > 5000)) {
                    if (now - lastUpgradeTime < 30_000L) {
                        flappingLockoutUntil = now + 120_000L
                    }
                    lastDowngradeTime = now
                    recoveryConsecutiveCount = 0
                    streamClient.sendControlCommand(camera, "resolution", "360p")
                    streamClient.sendControlCommand(camera, "quality", "20")
                    streamClient.sendControlCommand(camera, "fps", "15")

                    val reason = when {
                        isSevereLowFps -> "FPS 極低 ($currentFps FPS)"
                        isSeverePing -> "延遲過高 (${pingMs}ms)"
                        isSevereFrameLag -> "嚴重延遲 (${timeSinceLastFrame}ms)"
                        else -> "網路重度卡頓"
                    }
                    _adaptiveState.value = AdaptiveModeState(
                        isEnabled = true,
                        isDowngraded = true,
                        currentResolution = "360p",
                        currentQuality = 20,
                        targetFps = 15,
                        labelText = "⚡ 自適應: 360p ($reason)",
                        reasonText = reason,
                        pingMs = pingMs,
                        fps = currentFps
                    )
                }
                else if (lagConsecutiveCount >= 3 && currentLevel > 0 && (now - lastDowngradeTime > 6000)) {
                    if (now - lastUpgradeTime < 30_000L) {
                        flappingLockoutUntil = now + 120_000L
                    }
                    lastDowngradeTime = now
                    recoveryConsecutiveCount = 0
                    val nextLevel = (currentLevel - 1).coerceAtLeast(0)
                    val (nextRes, nextQual, nextFps) = when (nextLevel) {
                        0 -> Triple("360p", 25, 15)
                        1 -> Triple("480p", 30, 15)
                        else -> Triple("360p", 20, 15)
                    }
                    streamClient.sendControlCommand(camera, "resolution", nextRes)
                    streamClient.sendControlCommand(camera, "quality", nextQual.toString())
                    streamClient.sendControlCommand(camera, "fps", nextFps.toString())

                    val reason = when {
                        isModerateLowFps -> "FPS 偏低 ($currentFps FPS)"
                        isHighPing -> "延遲偏高 (${pingMs}ms)"
                        isFrameLag -> "影格延遲"
                        else -> "網路不穩"
                    }
                    _adaptiveState.value = AdaptiveModeState(
                        isEnabled = true,
                        isDowngraded = true,
                        currentResolution = nextRes,
                        currentQuality = nextQual,
                        targetFps = nextFps,
                        labelText = "⚡ 自適應: $nextRes ($reason)",
                        reasonText = reason,
                        pingMs = pingMs,
                        fps = currentFps
                    )
                }
                else if (recoveryConsecutiveCount >= 15 && currentLevel < 2 && now > flappingLockoutUntil && (now - lastDowngradeTime > 20000)) {
                    recoveryConsecutiveCount = 0
                    lastUpgradeTime = now
                    val nextLevel = currentLevel + 1
                    val (nextRes, nextQual, nextFps) = when (nextLevel) {
                        1 -> Triple("480p", 30, 15)
                        2 -> Triple("720p", 40, 20)
                        else -> Triple("480p", 30, 15)
                    }
                    streamClient.sendControlCommand(camera, "resolution", nextRes)
                    streamClient.sendControlCommand(camera, "quality", nextQual.toString())
                    streamClient.sendControlCommand(camera, "fps", nextFps.toString())

                    _adaptiveState.value = AdaptiveModeState(
                        isEnabled = true,
                        isDowngraded = false,
                        currentResolution = nextRes,
                        currentQuality = nextQual,
                        targetFps = nextFps,
                        labelText = "⚡ 自適應: $nextRes ($nextQual%)",
                        reasonText = "連線良好已升級",
                        pingMs = pingMs,
                        fps = currentFps
                    )
                } else {
                    val cur = _adaptiveState.value
                    val label = if (cur.isDowngraded) {
                        "⚡ 自適應: ${serverRes} (${cur.reasonText})"
                    } else {
                        "⚡ 自適應: ${serverRes} (${serverQual}%)"
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
        val success = streamClient.postRemoteConfig(camera, draftConfigJson)
        if (success) {
            fetchCameraStatus(camera)
        }
        return success
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
