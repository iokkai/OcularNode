with open("app/src/main/java/com/example/camera/CameraManagerHelper.kt", "r") as f:
    content = f.read()

content = content.replace(
    "private fun analyzeMotion(yBuffer: ByteBuffer, yRowStride: Int, width: Int, height: Int, avgLuma: Float, bitmap: Bitmap, rotation: Int)",
    "private fun analyzeMotion(yBuffer: ByteBuffer, yRowStride: Int, width: Int, height: Int, avgLuma: Float, bitmap: Bitmap?, rotation: Int)"
)

with open("app/src/main/java/com/example/camera/CameraManagerHelper.kt", "w") as f:
    f.write(content)
