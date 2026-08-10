import re

with open("app/src/main/java/com/example/ui/events/EventLogsViewModel.kt", "r") as f:
    text = f.read()

old_clear = """    fun clearAllEvents() {
        viewModelScope.launch(Dispatchers.IO) {
            val events = eventDao.getEventsListOnce()
            for (ev in events) {
                ev.snapshotPath?.let { java.io.File(it).delete() }
                ev.videoPath?.let { java.io.File(it).delete() }
            }
            eventDao.clearAllEvents()
        }
    }"""
new_clear = """    fun clearAllEvents() {
        viewModelScope.launch(Dispatchers.IO) {
            val events = eventDao.getEventsListOnce()
            
            // Delete local files
            for (ev in events) {
                ev.snapshotPath?.let { java.io.File(it).delete() }
                ev.videoPath?.let { java.io.File(it).delete() }
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
    }"""
text = text.replace(old_clear, new_clear)

with open("app/src/main/java/com/example/ui/events/EventLogsViewModel.kt", "w") as f:
    f.write(text)
