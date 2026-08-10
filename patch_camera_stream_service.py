with open("app/src/main/java/com/example/service/CameraStreamService.kt", "r") as f:
    content = f.read()

# Add to init
old_init = """        cameraHelper.motionCooldownSeconds = settingsManager.motionCooldownSeconds
        cameraHelper.autoNightVisionThreshold = settingsManager.autoNightVisionThreshold"""

new_init = """        cameraHelper.motionCooldownSeconds = settingsManager.motionCooldownSeconds
        cameraHelper.autoNightVisionThreshold = settingsManager.autoNightVisionThreshold
        cameraHelper.dynamicFpsAdjustmentEnabled = settingsManager.dynamicFpsAdjustmentEnabled
        cameraHelper.defaultJpegQuality = settingsManager.defaultQuality"""

content = content.replace(old_init, new_init)

# Add to prefsListener
old_prefs = """            } else if (key == "motion_enabled") {
                cameraHelper.isMotionDetectionEnabled = settingsManager.motionDetectionEnabled
            }
        }"""

new_prefs = """            } else if (key == "motion_enabled") {
                cameraHelper.isMotionDetectionEnabled = settingsManager.motionDetectionEnabled
            } else if (key == "dynamic_fps_enabled") {
                cameraHelper.dynamicFpsAdjustmentEnabled = settingsManager.dynamicFpsAdjustmentEnabled
            } else if (key == "default_quality") {
                cameraHelper.defaultJpegQuality = settingsManager.defaultQuality
            }
        }"""

content = content.replace(old_prefs, new_prefs)

with open("app/src/main/java/com/example/service/CameraStreamService.kt", "w") as f:
    f.write(content)
