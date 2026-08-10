with open("app/src/main/java/com/example/camera/CameraManagerHelper.kt", "r") as f:
    content = f.read()

old_motion = """                    try {
                        val scaled = Bitmap.createScaledBitmap(bitmap, 320, (320f * height / width).toInt(), true)
                        val matrix = Matrix()
                        if (rotation != 0) matrix.postRotate(rotation.toFloat())
                        val rotated = Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, matrix, true)
                        
                        val thumbStream = ByteArrayOutputStream()
                        rotated.compress(Bitmap.CompressFormat.JPEG, 50, thumbStream)
                        val thumbnailBytes = thumbStream.toByteArray()
                        
                        onMotionDetected?.invoke(motionRatio, thumbnailBytes, rotated)
                    } catch (e: Exception) {
                        Log.e("CameraManagerHelper", "Error generating thumbnail", e)
                    }"""

new_motion = """                    try {
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

content = content.replace(old_motion, new_motion)

with open("app/src/main/java/com/example/camera/CameraManagerHelper.kt", "w") as f:
    f.write(content)
