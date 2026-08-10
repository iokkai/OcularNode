with open("app/src/main/java/com/example/camera/CameraManagerHelper.kt", "r") as f:
    content = f.read()

import re

# Add imports if missing
if "import android.os.SystemClock" not in content:
    content = content.replace("import android.os.Build", "import android.os.Build\nimport android.os.SystemClock")

# Add variables
vars_insert = """    var dynamicFpsAdjustmentEnabled: Boolean = false
    var defaultJpegQuality: Int = 60
    private var lastProcessedFrameTime = 0L
    private var dynamicTargetFps = 15
    private var processTimesBuffer = mutableListOf<Long>()"""

content = content.replace("    var isMotionDetectionEnabled: Boolean = false", vars_insert + "\n    var isMotionDetectionEnabled: Boolean = false")

# Update processImageProxy
old_process = """    private fun processImageProxy(imageProxy: ImageProxy) {
        if (!isProcessingFrame.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        try {"""

new_process = """    private fun processImageProxy(imageProxy: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (dynamicFpsAdjustmentEnabled) {
            val minInterval = 1000L / dynamicTargetFps
            if (now - lastProcessedFrameTime < minInterval) {
                imageProxy.close()
                return
            }
        }

        if (!isProcessingFrame.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        lastProcessedFrameTime = SystemClock.elapsedRealtime()

        try {"""

content = content.replace(old_process, new_process)

# Update end of processImageProxy
old_end = """                // Pass to video recorder
                onFrameReadyForRecording?.invoke(rawJpegBytes, imageProxy.imageInfo.timestamp / 1000)
                
            } else {
                rawJpegBytes = ByteArray(0)
            }

            onFrameEncoded?.invoke(rawJpegBytes)

        } catch (e: Exception) {
            Log.e("CameraManagerHelper", "Error processing frame", e)
        } finally {
            imageProxy.close()
            isProcessingFrame.set(false)
        }
    }"""

new_end = """                // Pass to video recorder
                onFrameReadyForRecording?.invoke(rawJpegBytes, imageProxy.imageInfo.timestamp / 1000)
                
            } else {
                rawJpegBytes = ByteArray(0)
            }

            onFrameEncoded?.invoke(rawJpegBytes)

            if (dynamicFpsAdjustmentEnabled) {
                val processTime = SystemClock.elapsedRealtime() - now
                processTimesBuffer.add(processTime)
                if (processTimesBuffer.size >= 10) {
                    val avgProcessTime = processTimesBuffer.average().toLong()
                    processTimesBuffer.clear()
                    
                    if (avgProcessTime > 80) { // Device struggling
                        dynamicTargetFps = (dynamicTargetFps - 2).coerceAtLeast(5)
                        jpegQuality = (jpegQuality - 10).coerceAtLeast(20)
                    } else if (avgProcessTime < 40) { // Device has headroom
                        dynamicTargetFps = (dynamicTargetFps + 1).coerceAtMost(30)
                        jpegQuality = (jpegQuality + 5).coerceAtMost(defaultJpegQuality)
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("CameraManagerHelper", "Error processing frame", e)
        } finally {
            imageProxy.close()
            isProcessingFrame.set(false)
        }
    }"""

content = content.replace(old_end, new_end)

with open("app/src/main/java/com/example/camera/CameraManagerHelper.kt", "w") as f:
    f.write(content)

