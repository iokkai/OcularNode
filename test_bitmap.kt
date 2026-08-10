import androidx.camera.core.ImageProxy

fun test(imageProxy: ImageProxy) {
    val bitmap = imageProxy.toBitmap()
}
