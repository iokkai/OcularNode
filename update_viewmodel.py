import re

with open("app/src/main/java/com/example/ui/events/EventLogsViewModel.kt", "r") as f:
    text = f.read()

# Update parsing
old_parse = """                        val camName = item.optString("cameraName", camera.name)
                        val event = MotionEvent(
                            timestamp = timestamp,
                            cameraName = camName,"""
new_parse = """                        val remoteEventId = item.optLong("id", 0L)
                        val camName = item.optString("cameraName", camera.name)
                        val event = MotionEvent(
                            timestamp = timestamp,
                            cameraName = camName,
                            cameraIp = camera.ipAddress,
                            remoteId = if (remoteEventId > 0) remoteEventId else null,"""
text = text.replace(old_parse, new_parse)

# Update deleteEvent
old_delete = """    fun deleteEvent(event: MotionEvent) {
        viewModelScope.launch(Dispatchers.IO) {
            event.snapshotPath?.let { java.io.File(it).delete() }
            event.videoPath?.let { java.io.File(it).delete() }
            eventDao.deleteEventById(event.id)
        }
    }"""
new_delete = """    fun deleteEvent(event: MotionEvent) {
        viewModelScope.launch(Dispatchers.IO) {
            event.snapshotPath?.let { java.io.File(it).delete() }
            event.videoPath?.let { java.io.File(it).delete() }
            eventDao.deleteEventById(event.id)
            
            if (event.remoteId != null && event.cameraIp != "127.0.0.1") {
                try {
                    val camera = cameraDao.getCameraByIp(event.cameraIp)
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
    }"""
text = text.replace(old_delete, new_delete)

with open("app/src/main/java/com/example/ui/events/EventLogsViewModel.kt", "w") as f:
    f.write(text)
