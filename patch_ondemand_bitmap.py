with open("app/src/main/java/com/example/camera/CameraManagerHelper.kt", "r") as f:
    content = f.read()

import re

old_motion = """                    try {
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

new_motion = """                    try {
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

# I won't change it for now, no snapshot is fine if no one is recording/watching.
# Actually, if I pass imageProxy to analyzeMotion, I can do toBitmap() on demand.

