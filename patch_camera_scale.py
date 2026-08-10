with open("app/src/main/java/com/example/camera/CameraManagerHelper.kt", "r") as f:
    content = f.read()

import re

# Add dynamicScaleFactor variable
if "var dynamicScaleFactor = 1.0f" not in content:
    vars_insert = """    var dynamicFpsAdjustmentEnabled: Boolean = false
    var dynamicScaleFactor = 1.0f"""
    content = content.replace("    var dynamicFpsAdjustmentEnabled: Boolean = false", vars_insert)

# Update toBitmap drawing part
old_draw = """                // We must handle rotation! If rotated 90/270, the output width/height are swapped!
                val rotatedWidth = if (rotationDegrees % 180 != 0) bitmap.height else bitmap.width
                val rotatedHeight = if (rotationDegrees % 180 != 0) bitmap.width else bitmap.height

                // 1. Initialize reusable bitmap
                if (reusableBitmap == null || reusableBitmap!!.width != rotatedWidth || reusableBitmap!!.height != rotatedHeight) {
                    reusableBitmap?.recycle()
                    reusableBitmap = Bitmap.createBitmap(rotatedWidth, rotatedHeight, Bitmap.Config.ARGB_8888)
                    renderCanvas = Canvas(reusableBitmap!!)
                }"""

new_draw = """                // We must handle rotation! If rotated 90/270, the output width/height are swapped!
                val baseRotatedWidth = if (rotationDegrees % 180 != 0) bitmap.height else bitmap.width
                val baseRotatedHeight = if (rotationDegrees % 180 != 0) bitmap.width else bitmap.height

                val rotatedWidth = (baseRotatedWidth * dynamicScaleFactor).toInt()
                val rotatedHeight = (baseRotatedHeight * dynamicScaleFactor).toInt()

                // 1. Initialize reusable bitmap
                if (reusableBitmap == null || reusableBitmap!!.width != rotatedWidth || reusableBitmap!!.height != rotatedHeight) {
                    reusableBitmap?.recycle()
                    reusableBitmap = Bitmap.createBitmap(rotatedWidth, rotatedHeight, Bitmap.Config.ARGB_8888)
                    renderCanvas = Canvas(reusableBitmap!!)
                }"""

content = content.replace(old_draw, new_draw)

old_transform = """                // 2. Clear / Draw proxyBitmap to targetBitmap with necessary transformations
                canvas.save()
                
                // Translate to center, apply rotation, translate back
                canvas.translate(targetBitmap.width / 2f, targetBitmap.height / 2f)
                if (rotationDegrees != 0) {
                    canvas.rotate(rotationDegrees.toFloat())
                }
                if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    canvas.scale(-1f, 1f) // Mirror front camera
                }
                // Translate back to top-left of the original image
                canvas.translate(-bitmap.width / 2f, -bitmap.height / 2f)"""

new_transform = """                // 2. Clear / Draw proxyBitmap to targetBitmap with necessary transformations
                canvas.save()
                
                // Apply dynamic scaling
                canvas.scale(dynamicScaleFactor, dynamicScaleFactor)
                
                // Translate to center, apply rotation, translate back
                canvas.translate(baseRotatedWidth / 2f, baseRotatedHeight / 2f)
                if (rotationDegrees != 0) {
                    canvas.rotate(rotationDegrees.toFloat())
                }
                if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    canvas.scale(-1f, 1f) // Mirror front camera
                }
                // Translate back to top-left of the original image
                canvas.translate(-bitmap.width / 2f, -bitmap.height / 2f)"""

content = content.replace(old_transform, new_transform)

old_fps = """                    if (avgProcessTime > 80) { // Device struggling
                        dynamicTargetFps = (dynamicTargetFps - 2).coerceAtLeast(5)
                        jpegQuality = (jpegQuality - 10).coerceAtLeast(20)
                    } else if (avgProcessTime < 40) { // Device has headroom
                        dynamicTargetFps = (dynamicTargetFps + 1).coerceAtMost(30)
                        jpegQuality = (jpegQuality + 5).coerceAtMost(defaultJpegQuality)
                    }"""

new_fps = """                    if (avgProcessTime > 80) { // Device struggling
                        dynamicTargetFps = (dynamicTargetFps - 2).coerceAtLeast(10)
                        jpegQuality = (jpegQuality - 10).coerceAtLeast(30)
                        dynamicScaleFactor = (dynamicScaleFactor - 0.1f).coerceAtLeast(0.4f)
                    } else if (avgProcessTime < 40) { // Device has headroom
                        dynamicTargetFps = (dynamicTargetFps + 1).coerceAtMost(30)
                        jpegQuality = (jpegQuality + 5).coerceAtMost(defaultJpegQuality)
                        dynamicScaleFactor = (dynamicScaleFactor + 0.1f).coerceAtMost(1.0f)
                    }"""

content = content.replace(old_fps, new_fps)

with open("app/src/main/java/com/example/camera/CameraManagerHelper.kt", "w") as f:
    f.write(content)

