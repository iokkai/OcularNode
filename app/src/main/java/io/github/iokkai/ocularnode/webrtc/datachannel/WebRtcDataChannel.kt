package io.github.iokkai.ocularnode.webrtc.datachannel

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.PeerConnection
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

data class DataChannelCommand(
    val action: String,
    val params: Map<String, Any?> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("action", action)
        val paramsJson = JSONObject()
        params.forEach { (k, v) -> paramsJson.put(k, v) }
        json.put("params", paramsJson)
        json.put("timestamp", timestamp)
        return json.toString()
    }

    /**
     * Converts to legacy (command, value) pair for RemoteCommandHandler compatibility.
     */
    fun toLegacyPair(): Pair<String, String> {
        val valueStr = params["value"]?.toString() ?: ""
        return when (action.uppercase()) {
            ACTION_TORCH_TOGGLE, "TORCH" -> Pair("torch", valueStr.ifBlank { "toggle" })
            ACTION_SWITCH_CAMERA, "CAMERA" -> Pair("camera", valueStr)
            ACTION_NIGHT_VISION, "NIGHT_VISION" -> Pair("night_vision", valueStr)
            ACTION_QUALITY, "QUALITY" -> Pair("quality", valueStr)
            ACTION_RESOLUTION, "RESOLUTION" -> Pair("resolution", valueStr)
            ACTION_FPS, "FPS" -> Pair("fps", valueStr)
            ACTION_ROTATION, "ROTATION" -> Pair("rotation", valueStr)
            ACTION_MOTION_TOGGLE, "MOTION" -> Pair("motion", valueStr)
            ACTION_SENSITIVITY, "SENSITIVITY" -> Pair("sensitivity", valueStr)
            ACTION_COOLDOWN, "COOLDOWN" -> Pair("cooldown", valueStr)
            ACTION_PLAY_ALARM, "ALARM" -> Pair("alarm", valueStr)
            ACTION_PTZ_ZOOM, "ZOOM" -> Pair("zoom", valueStr)
            else -> Pair(action.lowercase(), valueStr)
        }
    }

    companion object {
        fun fromJson(jsonStr: String): DataChannelCommand {
            val json = JSONObject(jsonStr)
            val action = json.getString("action")
            val paramsJson = json.optJSONObject("params")
            val params = mutableMapOf<String, Any?>()
            paramsJson?.keys()?.forEach { k ->
                params[k] = paramsJson.get(k)
            }
            val timestamp = json.optLong("timestamp", System.currentTimeMillis())
            return DataChannelCommand(action, params, timestamp)
        }

        fun fromLegacy(command: String, value: String): DataChannelCommand {
            val action = when (command.lowercase()) {
                "torch" -> ACTION_TORCH_TOGGLE
                "camera" -> ACTION_SWITCH_CAMERA
                "night_vision" -> ACTION_NIGHT_VISION
                "quality" -> ACTION_QUALITY
                "resolution" -> ACTION_RESOLUTION
                "fps" -> ACTION_FPS
                "rotation" -> ACTION_ROTATION
                "motion" -> ACTION_MOTION_TOGGLE
                "sensitivity" -> ACTION_SENSITIVITY
                "cooldown" -> ACTION_COOLDOWN
                "alarm" -> ACTION_PLAY_ALARM
                "zoom", "ptz_zoom" -> ACTION_PTZ_ZOOM
                else -> command.uppercase()
            }
            return DataChannelCommand(action, mapOf("value" to value))
        }

        const val ACTION_TORCH_TOGGLE = "TORCH_TOGGLE"
        const val ACTION_SWITCH_CAMERA = "SWITCH_CAMERA"
        const val ACTION_NIGHT_VISION = "NIGHT_VISION"
        const val ACTION_QUALITY = "QUALITY"
        const val ACTION_RESOLUTION = "RESOLUTION"
        const val ACTION_FPS = "FPS"
        const val ACTION_ROTATION = "ROTATION"
        const val ACTION_MOTION_TOGGLE = "MOTION_TOGGLE"
        const val ACTION_SENSITIVITY = "SENSITIVITY"
        const val ACTION_COOLDOWN = "COOLDOWN"
        const val ACTION_PLAY_ALARM = "PLAY_ALARM"
        const val ACTION_PTZ_ZOOM = "PTZ_ZOOM"
        const val ACTION_MOTION_ALERT = "MOTION_ALERT"
        const val ACTION_BATTERY_STATUS = "BATTERY_STATUS"
        const val ACTION_PING = "PING"
        const val ACTION_PONG = "PONG"
    }
}

/**
 * Manages WebRTC DataChannel for ultra-low-latency (< 10ms), peer-to-peer control commands
 * and telemetry without hitting any external signaling servers during streaming.
 */
class WebRtcDataChannel(
    private val peerConnection: PeerConnection,
    private val isInitiator: Boolean,
    private val label: String = "control"
) {

    companion object {
        private const val TAG = "WebRtcDataChannel"
    }

    private val _incomingCommands = MutableSharedFlow<DataChannelCommand>(extraBufferCapacity = 64)
    val incomingCommands: SharedFlow<DataChannelCommand> = _incomingCommands.asSharedFlow()

    private var dataChannel: DataChannel? = null

    val isOpen: Boolean
        get() = dataChannel?.state() == DataChannel.State.OPEN

    private val observer = object : DataChannel.Observer {
        override fun onBufferedAmountChange(previousAmount: Long) {}

        override fun onStateChange() {
            val state = dataChannel?.state()
            Log.i(TAG, "DataChannel [$label] state changed: $state")
        }

        override fun onMessage(buffer: DataChannel.Buffer?) {
            if (buffer != null && !buffer.binary) {
                try {
                    val bytes = ByteArray(buffer.data.remaining())
                    buffer.data.get(bytes)
                    val text = String(bytes, StandardCharsets.UTF_8)
                    val cmd = DataChannelCommand.fromJson(text)
                    Log.d(TAG, "Received DataChannel command: ${cmd.action} (params: ${cmd.params})")
                    _incomingCommands.tryEmit(cmd)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing DataChannel command", e)
                }
            }
        }
    }

    init {
        if (isInitiator) {
            val init = DataChannel.Init().apply {
                ordered = true
                maxRetransmits = 3
            }
            val channel = peerConnection.createDataChannel(label, init)
            channel.registerObserver(observer)
            dataChannel = channel
            Log.i(TAG, "Initiator created DataChannel: $label")
        }
    }

    fun attachRemoteDataChannel(remoteChannel: DataChannel) {
        dataChannel?.dispose()
        remoteChannel.registerObserver(observer)
        dataChannel = remoteChannel
        Log.i(TAG, "Attached remote DataChannel: ${remoteChannel.label()}")
    }

    fun sendCommand(command: DataChannelCommand): Boolean {
        val channel = dataChannel
        if (channel == null || channel.state() != DataChannel.State.OPEN) {
            Log.w(TAG, "Cannot send command: DataChannel is not open (state: ${channel?.state()})")
            return false
        }

        return try {
            val jsonBytes = command.toJson().toByteArray(StandardCharsets.UTF_8)
            val byteBuffer = ByteBuffer.wrap(jsonBytes)
            val buffer = DataChannel.Buffer(byteBuffer, false)
            val success = channel.send(buffer)
            Log.d(TAG, "Sent DataChannel command: ${command.action} (success: $success)")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error sending DataChannel command", e)
            false
        }
    }

    fun close() {
        try {
            dataChannel?.unregisterObserver()
            dataChannel?.dispose()
            dataChannel = null
            Log.i(TAG, "WebRtcDataChannel closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing WebRtcDataChannel", e)
        }
    }
}
