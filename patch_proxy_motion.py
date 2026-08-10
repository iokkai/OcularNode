with open("app/src/main/java/com/example/camera/CameraManagerHelper.kt", "r") as f:
    content = f.read()

content = content.replace(
    "private fun analyzeMotion(yBuffer: ByteBuffer, yRowStride: Int, width: Int, height: Int, avgLuma: Float, bitmap: Bitmap?, rotation: Int)",
    "private fun analyzeMotion(imageProxy: androidx.camera.core.ImageProxy, yBuffer: ByteBuffer, yRowStride: Int, width: Int, height: Int, avgLuma: Float, bitmap: Bitmap?, rotation: Int)"
)

content = content.replace(
    "analyzeMotion(yBuffer, yRowStride, width, height, avgLuma, null, rotationDegrees)",
    "analyzeMotion(imageProxy, yBuffer, yRowStride, width, height, avgLuma, null, rotationDegrees)"
)

content = content.replace(
    "analyzeMotion(yBuffer, yRowStride, width, height, avgLuma, bitmap, rotationDegrees)",
    "analyzeMotion(imageProxy, yBuffer, yRowStride, width, height, avgLuma, bitmap, rotationDegrees)"
)

old_motion_body = """                    try {
                        var actualBitmap = bitmap
                        // Wait! The passed bitmap is null, but we need one for the snapshot!
                        // In Java/Kotlin we can't easily grab imageProxy here because it's not passed in.
                        // But we can check if bitmap is null. 
                        // I will fix this in a different way if needed, but for now we just skip the thumbnail.
                        if (actualBitmap != null) {
                            val scaled = Bitmap.createScaledBitmap(actualBitmap, 320, (320f * height / width).toInt(), true)
                            val matrix = Matrix()
                            if (rotation != 0) matrix.postRotate(rotation.toFloat())
                            val rotated = Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, matrix, true)
                            
                            val thumbStream = ByteArrayOutputStream()
                            rotated.compress(Bitmap.CompressFormat.JPEG, 50, thumbStream)
                            val thumbnailBytes = thumbStream.toByteArray()
                            
                            onMotionDetected?.invoke(motionRatio, thumbnailBytes, rotated)
                        } else {
                            onMotionDetected?.invoke(motionRatio, ByteArray(0), null)
                        }
                    } catch (e: Exception) {
                        Log.e("CameraManagerHelper", "Error generating thumbnail", e)
                    }"""

# Wait, `patch_ondemand_bitmap.py` changed it, let me just match the generic part
old_motion_body2 = """                    try {
                        if (bitmap != null) {
                            val scaled = Bitmap.createScaledBitmap(bitmap, 320, (320f * height / width).toInt(), true)
                            val matrix = Matrix()
                            if (rotation != 0) matrix.postRotate(rotation.toFloat())
                            val rotated = Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, matrix, true)
                            
                            val thumbStream = ByteArrayOutputStream()
                            rotated.compress(Bitmap.CompressFormat.JPEG, 50, thumbStream)
                            val thumbnailBytes = thumbStream.toByteArray()
                            
                            onMotionDetected?.invoke(motionRatio, thumbnailBytes, rotated)
                        } else {
                            onMotionDetected?.invoke(motionRatio, ByteArray(0), null)
                        }
                    } catch (e: Exception) {
                        Log.e("CameraManagerHelper", "Error generating thumbnail", e)
                    }"""

new_motion_body = """                    try {
                        var activeBitmap = bitmap ?: try { imageProxy.toBitmap() } catch(e: Exception) { null }
                        if (activeBitmap != null) {
                            val scaled = Bitmap.createScaledBitmap(activeBitmap, 320, (320f * height / width).toInt(), true)
                            val matrix = Matrix()
                            if (rotation != 0) matrix.postRotate(rotation.toFloat())
                            val rotated = Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, matrix, true)
                            
                            val thumbStream = ByteArrayOutputStream()
                            rotated.compress(Bitmap.CompressFormat.JPEG, 50, thumbStream)
                            val thumbnailBytes = thumbStream.toByteArray()
                            
                            onMotionDetected?.invoke(motionRatio, thumbnailBytes, rotated)
                        } else {
                            onMotionDetected?.invoke(motionRatio, ByteArray(0), null)
                        }
                    } catch (e: Exception) {
                        Log.e("CameraManagerHelper", "Error generating thumbnail", e)
                    }"""
content = content.replace(old_motion_body2, new_motion_body)

# Just in case `patch_ondemand_bitmap.py` DID apply, I will also match that one:
old_motion_body3 = """                    try {
                        var actualBitmap = bitmap
                        // Wait! The passed bitmap is null, but we need one for the snapshot!
                        // In Java/Kotlin we can't easily grab imageProxy here because it's not passed in.
                        // But we can check if bitmap is null. 
                        // I will fix this in a different way if needed, but for now we just skip the thumbnail.
                        if (actualBitmap != null) {
                            val scaled = Bitmap.createScaledBitmap(actualBitmap, 320, (320f * height / width).toInt(), true)
                            val matrix = Matrix()
                            if (rotation != 0) matrix.postRotate(rotation.toFloat())
                            val rotated = Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, matrix, true)
                            
                            val thumbStream = ByteArrayOutputStream()
                            rotated.compress(Bitmap.CompressFormat.JPEG, 50, thumbStream)
                            val thumbnailBytes = thumbStream.toByteArray()
                            
                            onMotionDetected?.invoke(motionRatio, thumbnailBytes, rotated)
                        } else {
                            onMotionDetected?.invoke(motionRatio, ByteArray(0), null)
                        }
                    } catch (e: Exception) {
                        Log.e("CameraManagerHelper", "Error generating thumbnail", e)
                    }"""
content = content.replace(old_motion_body3, new_motion_body)

with open("app/src/main/java/com/example/camera/CameraManagerHelper.kt", "w") as f:
    f.write(content)
