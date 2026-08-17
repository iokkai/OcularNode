package io.github.iokkai.ocularnode.ui.events

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.iokkai.ocularnode.data.AppDatabase
import io.github.iokkai.ocularnode.data.CameraDevice
import io.github.iokkai.ocularnode.data.MotionEvent
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
    val settingsManager = io.github.iokkai.ocularnode.data.SettingsManager.getInstance(application)

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
            event.snapshotPath?.let { java.io.File(it).delete() }
            event.videoPath?.let { java.io.File(it).delete() }
            eventDao.deleteEventById(event.id)
            
            if (event.remoteId != null && event.cameraIp != "127.0.0.1") {
                try {
                    val cameraList = cameraDao.getCamerasListOnce()
                    val camera = cameraList.find { it.ipAddress == event.cameraIp }
                    if (camera != null) {
                        val client = OkHttpClient.Builder()
                            .connectTimeout(5, TimeUnit.SECONDS)
                            .readTimeout(5, TimeUnit.SECONDS)
                            .build()
                        val url = "http://${camera.ipAddress}:${camera.port}/events/delete?id=${event.remoteId}"
                        val request = Request.Builder().url(url).get().build()
                        client.newCall(request).execute().close()
                    }
                } catch (e: Exception) {
                    Log.e("EventLogsViewModel", "Failed to delete remote event: ${e.message}")
                }
            }
        }
    }

    fun clearAllEvents() {
        viewModelScope.launch(Dispatchers.IO) {
            val events = eventDao.getEventsListOnce()
            
            // Delete local files referenced by events
            for (ev in events) {
                ev.snapshotPath?.let { try { java.io.File(it).delete() } catch (_: Exception) {} }
                ev.videoPath?.let { try { java.io.File(it).delete() } catch (_: Exception) {} }
            }

            // Wipe all media files in media directories
            val mediaDirs = listOfNotNull(
                getApplication<Application>().getExternalFilesDir(null)?.let { java.io.File(it, "media") },
                getApplication<Application>().getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)?.let { java.io.File(it, "OcularNode") }
            )
            for (dir in mediaDirs) {
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            try { file.delete() } catch (_: Exception) {}
                        }
                    }
                }
            }
            
            // Find remote cameras that have events
            val remoteIps = events.mapNotNull { if (it.remoteId != null && it.cameraIp != "127.0.0.1") it.cameraIp else null }.distinct()
            
            // Delete local DB records
            eventDao.clearAllEvents()
            
            // Delete remote events
            if (remoteIps.isNotEmpty()) {
                val cameraList = cameraDao.getCamerasListOnce()
                for (ip in remoteIps) {
                    val camera = cameraList.find { it.ipAddress == ip }
                    if (camera != null) {
                        try {
                            val client = OkHttpClient.Builder()
                                .connectTimeout(5, TimeUnit.SECONDS)
                                .readTimeout(5, TimeUnit.SECONDS)
                                .build()
                            val url = "http://${camera.ipAddress}:${camera.port}/events/clear"
                            val request = Request.Builder().url(url).get().build()
                            client.newCall(request).execute().close()
                        } catch (e: Exception) {
                            Log.e("EventLogsViewModel", "Failed to clear remote events for $ip: ${e.message}")
                        }
                    }
                }
            }
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
                    val newEvents = mutableListOf<MotionEvent>()

                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONObject(i)
                        val remoteId = item.optLong("id", 0L).let { if (it > 0) it else null }
                        val timestamp = item.optLong("timestamp", System.currentTimeMillis())
                        val motionPercentage = item.optDouble("motionPercentage", 0.0).toFloat()
                        val thumbnailBase64 = item.optString("thumbnailBase64", "")
                        val aiSummary = item.optString("aiSummary", "")
                        val aiFiltered = item.optBoolean("aiFiltered", false)
                        val camName = item.optString("cameraName", camera.name)

                        val event = MotionEvent(
                            timestamp = timestamp,
                            cameraName = camName,
                            cameraIp = camera.ipAddress,
                            motionPercentage = motionPercentage,
                            thumbnailBase64 = if (thumbnailBase64.isNotBlank()) thumbnailBase64 else null,
                            isRead = true,
                            telegramSentSuccess = false,
                            aiSummary = aiSummary,
                            aiFiltered = aiFiltered,
                            remoteId = remoteId
                        )
                        newEvents.add(event)
                    }

                    if (newEvents.isNotEmpty()) {
                        eventDao.insertEvents(newEvents)
                    }
                    _syncMessage.value = "✅ Synced ${newEvents.size} records from [${camera.name}]"
                } else {
                    _syncMessage.value = "⚠️ Cannot connect to ${camera.name} (HTTP ${response.code})"
                }
                response.close()
            } catch (e: Exception) {
                Log.e("EventLogsViewModel", "Error syncing remote events from ${camera.name}", e)
                _syncMessage.value = "❌ Sync failed: ${e.localizedMessage ?: "Cannot connect to camera"}"
            } finally {
                _isSyncingRemote.value = false
            }
        }
    }
}

