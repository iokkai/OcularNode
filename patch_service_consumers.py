with open("app/src/main/java/com/example/service/CameraStreamService.kt", "r") as f:
    content = f.read()

old_init = """        cameraHelper.onLumaMeasured = { luma ->
            _measuredLuma.value = luma
        }"""

new_init = """        cameraHelper.onLumaMeasured = { luma ->
            _measuredLuma.value = luma
        }
        
        cameraHelper.hasActiveConsumers = {
            httpServer.connectedClientsCount.get() > 0 || settingsManager.eventVideoRecordingEnabled
        }"""

content = content.replace(old_init, new_init)

with open("app/src/main/java/com/example/service/CameraStreamService.kt", "w") as f:
    f.write(content)
