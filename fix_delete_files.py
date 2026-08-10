import re

with open("app/src/main/java/com/example/server/MjpegHttpServer.kt", "r") as f:
    text = f.read()

old = """                        if (id != null) {
                            AppDatabase.getDatabase(context).motionEventDao().deleteEventById(id)
                            sendJsonResponse(output, 200, "{\\"status\\":\\"deleted\\"}")
                        } else {"""

new = """                        if (id != null) {
                            val eventDao = AppDatabase.getDatabase(context).motionEventDao()
                            val events = eventDao.getEventsListOnce()
                            val event = events.find { it.id == id }
                            event?.snapshotPath?.let { java.io.File(it).delete() }
                            event?.videoPath?.let { java.io.File(it).delete() }
                            eventDao.deleteEventById(id)
                            sendJsonResponse(output, 200, "{\\"status\\":\\"deleted\\"}")
                        } else {"""

text = text.replace(old, new)

with open("app/src/main/java/com/example/server/MjpegHttpServer.kt", "w") as f:
    f.write(text)
