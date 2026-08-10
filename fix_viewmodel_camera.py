import re

with open("app/src/main/java/com/example/ui/events/EventLogsViewModel.kt", "r") as f:
    text = f.read()

old_delete = """                    val camera = cameraDao.getCameraByIp(event.cameraIp)
                    if (camera != null) {"""
new_delete = """                    val cameraList = cameraDao.getCamerasListOnce()
                    val camera = cameraList.find { it.ipAddress == event.cameraIp }
                    if (camera != null) {"""

text = text.replace(old_delete, new_delete)

with open("app/src/main/java/com/example/ui/events/EventLogsViewModel.kt", "w") as f:
    f.write(text)
