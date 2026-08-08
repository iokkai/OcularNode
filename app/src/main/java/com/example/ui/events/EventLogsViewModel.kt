package com.example.ui.events

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.CameraDevice
import com.example.data.MotionEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

class EventLogsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val eventDao = db.motionEventDao()
    private val cameraDao = db.cameraDeviceDao()
    val settingsManager = com.example.data.SettingsManager(application)

    val motionEvents: StateFlow<List<MotionEvent>> = eventDao.getAllEvents()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val cameraDevices: StateFlow<List<CameraDevice>> = cameraDao.getAllCameras()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val unreadCount: StateFlow<Int> = eventDao.getUnreadCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    private val _isSyncingRemote = MutableStateFlow(false)
    val isSyncingRemote: StateFlow<Boolean> = _isSyncingRemote.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    fun markAllAsRead() {
        viewModelScope.launch(Dispatchers.IO) {
            eventDao.markAllAsRead()
        }
    }

    fun deleteEvent(event: MotionEvent) {
        viewModelScope.launch(Dispatchers.IO) {
            eventDao.deleteEventById(event.id)
        }
    }

    fun clearAllEvents() {
        viewModelScope.launch(Dispatchers.IO) {
            eventDao.clearAllEvents()
        }
    }

    fun syncRemoteEventsFromCamera(camera: CameraDevice) {
        viewModelScope.launch(Dispatchers.IO) {
            _isSyncingRemote.value = true
            _syncMessage.value = null

            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()

            val url = "http://${camera.ipAddress}:${camera.port}/events"
            try {
                val request = Request.Builder().url(url).get().build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful && response.body != null) {
                    val bodyStr = response.body!!.string()
                    val jsonArray = JSONArray(bodyStr)
                    var newCount = 0

                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONObject(i)
                        val timestamp = item.optLong("timestamp", System.currentTimeMillis())
                        val motionPercentage = item.optDouble("motionPercentage", 0.0).toFloat()
                        val thumbnailBase64 = item.optString("thumbnailBase64", "")
                        val aiSummary = item.optString("aiSummary", "")
                        val aiFiltered = item.optBoolean("aiFiltered", false)
                        val camName = item.optString("cameraName", camera.name)

                        val event = MotionEvent(
                            timestamp = timestamp,
                            cameraName = camName,
                            motionPercentage = motionPercentage,
                            thumbnailBase64 = if (thumbnailBase64.isNotBlank()) thumbnailBase64 else null,
                            isRead = true,
                            telegramSentSuccess = false,
                            aiSummary = aiSummary,
                            aiFiltered = aiFiltered
                        )
                        eventDao.insertEvent(event)
                        newCount++
                    }
                    _syncMessage.value = "✅ 成功同步來自 [${camera.name}] 的 $newCount 筆偵測紀錄！"
                } else {
                    _syncMessage.value = "⚠️ 無法連線至 ${camera.name} (HTTP ${response.code})"
                }
                response.close()
            } catch (e: Exception) {
                Log.e("EventLogsViewModel", "Error syncing remote events from ${camera.name}", e)
                _syncMessage.value = "❌ 同步失敗: ${e.localizedMessage ?: "無法連線至鏡頭端"}"
            } finally {
                _isSyncingRemote.value = false
            }
        }
    }
}

