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

        const val ACTION_TORCH_TOGGLE = "TORCH_TOGGLE"
        const val ACTION_SWITCH_CAMERA = "SWITCH_CAMERA"
        const val ACTION_NIGHT_VISION = "NIGHT_VISION"
        const val ACTION_MOTION_ALERT = "MOTION_ALERT"
        const val ACTION_BATTERY_STATUS = "BATTERY_STATUS"
        const val ACTION_PTZ_ZOOM = "PTZ_ZOOM"
    }
}

/**
 * Manages WebRTC DataChannel for low-latency (< 10ms), peer-to-peer control commands
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
                    Log.d(TAG, "Received DataChannel command: ${cmd.action}")
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
            val jsonText = command.toJson()
            val byteBuffer = ByteBuffer.wrap(jsonText.toByteArray(StandardCharsets.UTF_8))
            val buffer = DataChannel.Buffer(byteBuffer, false)
            channel.send(buffer)
            Log.d(TAG, "Sent DataChannel command: ${command.action}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending DataChannel command", e)
            false
        }
    }

    fun close() {
        try {
            dataChannel?.unregisterObserver()
            dataChannel?.close()
            dataChannel?.dispose()
            dataChannel = null
            Log.i(TAG, "DataChannel closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing DataChannel", e)
        }
    }
}
