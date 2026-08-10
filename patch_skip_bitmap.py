with open("app/src/main/java/com/example/camera/CameraManagerHelper.kt", "r") as f:
    content = f.read()

import re

# Add hasActiveConsumers
if "var hasActiveConsumers: () -> Boolean = { true }" not in content:
    content = content.replace("    var onLumaMeasured: ((Float) -> Unit)? = null", "    var onLumaMeasured: ((Float) -> Unit)? = null\n    var hasActiveConsumers: () -> Boolean = { true }")

old_process = """            // Determine Night Vision state
            isNightVisionActive = when (nightVisionMode) {
                "on" -> true
                "off" -> false
                else -> avgLuma < autoNightVisionThreshold
            }

            // 2. Optimized conversion: use CameraX toBitmap() directly
            var rawJpegBytes: ByteArray
            var bitmap = imageProxy.toBitmap()

            if (bitmap != null) {
                if (isMotionDetectionEnabled) {
                    analyzeMotion(yBuffer, yRowStride, width, height, avgLuma, bitmap, rotationDegrees)
                }"""

new_process = """            // Determine Night Vision state
            isNightVisionActive = when (nightVisionMode) {
                "on" -> true
                "off" -> false
                else -> avgLuma < autoNightVisionThreshold
            }

            // 1.5. If motion detection is enabled, we can run it on Y-buffer directly without Bitmap
            if (isMotionDetectionEnabled) {
                // Pass null for bitmap since analyzeMotion only uses yBuffer
                analyzeMotion(yBuffer, yRowStride, width, height, avgLuma, null, rotationDegrees)
            }

            // If no one is watching and we are not recording, skip the heavy Bitmap/JPEG processing!
            if (!hasActiveConsumers() && !isRecordingVideo) {
                onFrameEncoded?.invoke(ByteArray(0))
                imageProxy.close()
                isProcessingFrame.set(false)
                return
            }

            // 2. Optimized conversion: use CameraX toBitmap() directly
            var rawJpegBytes: ByteArray
            var bitmap = imageProxy.toBitmap()

            if (bitmap != null) {"""

content = content.replace(old_process, new_process)

with open("app/src/main/java/com/example/camera/CameraManagerHelper.kt", "w") as f:
    f.write(content)

