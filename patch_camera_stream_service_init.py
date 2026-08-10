with open("app/src/main/java/com/example/service/CameraStreamService.kt", "r") as f:
    content = f.read()

old_init = """        cameraHelper.isMotionDetectionEnabled = settingsManager.motionDetectionEnabled
        cameraHelper.motionSensitivity = settingsManager.motionSensitivity
        cameraHelper.motionCooldownSeconds = settingsManager.motionCooldownSeconds"""

new_init = """        cameraHelper.isMotionDetectionEnabled = settingsManager.motionDetectionEnabled
        cameraHelper.motionSensitivity = settingsManager.motionSensitivity
        cameraHelper.motionCooldownSeconds = settingsManager.motionCooldownSeconds
        cameraHelper.dynamicFpsAdjustmentEnabled = settingsManager.dynamicFpsAdjustmentEnabled
        cameraHelper.defaultJpegQuality = settingsManager.defaultQuality"""

content = content.replace(old_init, new_init)

with open("app/src/main/java/com/example/service/CameraStreamService.kt", "w") as f:
    f.write(content)
