import re

with open("app/src/main/java/com/example/server/MjpegHttpServer.kt", "r") as f:
    text = f.read()

old = """                path == "/events" || path.startsWith("/events?") -> {"""
new = """                path == "/events/clear" -> {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val events = AppDatabase.getDatabase(context).motionEventDao().getEventsListOnce()
                            for (ev in events) {
                                ev.snapshotPath?.let { java.io.File(it).delete() }
                                ev.videoPath?.let { java.io.File(it).delete() }
                            }
                            AppDatabase.getDatabase(context).motionEventDao().clearAllEvents()
                            sendJsonResponse(output, 200, "{\"status\":\"cleared\"}")
                        } catch (e: Exception) {
                            sendJsonResponse(output, 500, "{\"error\":\"Internal Server Error\"}")
                        }
                        try { socket.close() } catch (_: Exception) {}
                    }
                    return
                }

                path == "/events" || path.startsWith("/events?") -> {"""

text = text.replace(old, new)

with open("app/src/main/java/com/example/server/MjpegHttpServer.kt", "w") as f:
    f.write(text)
