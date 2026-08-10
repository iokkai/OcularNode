with open("app/src/main/java/com/example/camera/CameraManagerHelper.kt", "r") as f:
    content = f.read()

old_draw = """                // We must handle rotation! If rotated 90/270, the output width/height are swapped!
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

new_draw = """                // toBitmap() already handles rotation in CameraX!
                val baseWidth = bitmap.width
                val baseHeight = bitmap.height

                val scaledWidth = (baseWidth * dynamicScaleFactor).toInt().coerceAtLeast(1)
                val scaledHeight = (baseHeight * dynamicScaleFactor).toInt().coerceAtLeast(1)

                // 1. Initialize reusable bitmap
                if (reusableBitmap == null || reusableBitmap!!.width != scaledWidth || reusableBitmap!!.height != scaledHeight) {
                    reusableBitmap?.recycle()
                    reusableBitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
                    renderCanvas = Canvas(reusableBitmap!!)
                }"""

content = content.replace(old_draw, new_draw)

old_transform = """                // 2. Clear / Draw proxyBitmap to targetBitmap with necessary transformations
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

new_transform = """                // 2. Clear / Draw proxyBitmap to targetBitmap with necessary transformations
                canvas.save()
                
                // Apply dynamic scaling
                canvas.scale(dynamicScaleFactor, dynamicScaleFactor)
                
                if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    canvas.translate(baseWidth.toFloat(), 0f)
                    canvas.scale(-1f, 1f) // Mirror front camera
                }"""

content = content.replace(old_transform, new_transform)

with open("app/src/main/java/com/example/camera/CameraManagerHelper.kt", "w") as f:
    f.write(content)

