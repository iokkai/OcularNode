package com.example.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import android.graphics.RectF
import android.graphics.Typeface
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class CameraManagerHelper(private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null

    // Video Capture (Default 720p HD)
    private val recorder = Recorder.Builder()
        .setQualitySelector(QualitySelector.from(Quality.HD))
        .build()
    var videoCapture: VideoCapture<Recorder> = VideoCapture.withOutput(recorder)
        private set

    private var activeRecording: Recording? = null
    var isRecordingVideo: Boolean = false
        private set

    // Configuration State
    var lensFacing: Int = CameraSelector.LENS_FACING_BACK
        private set
    var isTorchOn: Boolean = false
        private set
    var jpegQuality: Int = 60
    var currentResolutionString: String = "720p"
    var nightVisionMode: String = "auto" // "off", "on", "auto"
    var isNightVisionActive: Boolean = false
        private set
    var dynamicFpsAdjustmentEnabled: Boolean = false
    var dynamicScaleFactor = 1.0f
    var defaultJpegQuality: Int = 60
    private var lastProcessedFrameTime = 0L
    private var dynamicTargetFps = 15
    private var processTimesBuffer = mutableListOf<Long>()
    var isMotionDetectionEnabled: Boolean = false
    var motionSensitivity: Float = 5.0f // 1..10
    var motionCooldownSeconds: Int = 30 // seconds
    var autoNightVisionThreshold: Float = 45.0f

    // Motion Detection State
    private var prevLumaMatrix: FloatArray? = null
    private var prevMatrixWidth = 0
    private var prevMatrixHeight = 0
    private var lastMotionTime = 0L
    private var cameraStartTime = 0L
    private val isProcessingFrame = AtomicBoolean(false)
    
    // Cached Paint objects for watermark
    private var cachedTextPaint: android.graphics.Paint? = null
    private var cachedBgPaint: android.graphics.Paint? = null
    private var cachedDateFormat: java.text.SimpleDateFormat? = null
    
    private var cachedNightVisionPaint: android.graphics.Paint? = null
    
    private var reusableBitmap: Bitmap? = null
    private var renderCanvas: Canvas? = null
    private val jpegOutputStream = ByteArrayOutputStream(1024 * 500)

    // Callbacks
    var onFrameEncoded: ((ByteArray) -> Unit)? = null
    var onFrameReadyForRecording: ((ByteArray, Long) -> Unit)? = null
    var onMotionDetected: ((Float, ByteArray, Bitmap?) -> Unit)? = null // percentage, thumbnail JPEG, rotated Bitmap
    var onLumaMeasured: ((Float) -> Unit)? = null
    var hasActiveConsumers: () -> Boolean = { true }

    private var currentLifecycleOwner: LifecycleOwner? = null
    private var currentPreviewSurface: Preview.SurfaceProvider? = null

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewSurface: Preview.SurfaceProvider? = null,
        onReady: () -> Unit = {}
    ) {
        prevLumaMatrix = null
        lastMotionTime = System.currentTimeMillis()
        cameraStartTime = System.currentTimeMillis()
        this.currentLifecycleOwner = lifecycleOwner
        if (previewSurface != null) {
            this.currentPreviewSurface = previewSurface
        }
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
                onReady()
            } catch (e: Exception) {
                Log.e("CameraManagerHelper", "Error starting camera provider", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun attachPreviewSurface(lifecycleOwner: LifecycleOwner, previewSurface: Preview.SurfaceProvider) {
        this.currentLifecycleOwner = lifecycleOwner
        this.currentPreviewSurface = previewSurface
        bindCameraUseCases()
    }

    fun bindCameraUseCases(
        lifecycleOwner: LifecycleOwner? = currentLifecycleOwner,
        previewSurface: Preview.SurfaceProvider? = currentPreviewSurface
    ) {
        val owner = lifecycleOwner ?: currentLifecycleOwner ?: return
        val provider = cameraProvider ?: return
        provider.unbindAll()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        val targetSize = getTargetSize(currentResolutionString)

        val imageAnalysisBuilder = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)

        if (targetSize != null) {
            imageAnalysisBuilder.setTargetResolution(targetSize)
        }

        val imageAnalysis = imageAnalysisBuilder.build()
        imageAnalysis.setAnalyzer(executor) { imageProxy ->
            processImageProxy(imageProxy)
        }

        val surfaceToUse = previewSurface ?: currentPreviewSurface

        try {
            if (surfaceToUse != null) {
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(surfaceToUse)
                camera = provider.bindToLifecycle(owner, cameraSelector, preview, imageAnalysis, videoCapture)
            } else {
                camera = provider.bindToLifecycle(owner, cameraSelector, imageAnalysis, videoCapture)
            }
            if (lensFacing == CameraSelector.LENS_FACING_BACK && isTorchOn) {
                camera?.cameraControl?.enableTorch(true)
            }
        } catch (e: Exception) {
            Log.e("CameraManagerHelper", "Primary bind with VideoCapture failed, trying fallback...", e)
            try {
                val fallbackAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                fallbackAnalysis.setAnalyzer(executor) { imageProxy ->
                    processImageProxy(imageProxy)
                }

                if (surfaceToUse != null) {
                    val preview = Preview.Builder().build()
                    preview.setSurfaceProvider(surfaceToUse)
                    camera = provider.bindToLifecycle(owner, cameraSelector, preview, fallbackAnalysis, videoCapture)
                } else {
                    camera = provider.bindToLifecycle(owner, cameraSelector, fallbackAnalysis, videoCapture)
                }
                if (lensFacing == CameraSelector.LENS_FACING_BACK && isTorchOn) {
                    camera?.cameraControl?.enableTorch(true)
                }
            } catch (fallbackEx: Exception) {
                Log.e("CameraManagerHelper", "Fallback bind also failed", fallbackEx)
            }
        }
    }

    fun startRecording(outputFile: java.io.File, onRecordingFinished: (Boolean, String?) -> Unit) {
        if (isRecordingVideo) {
            Log.w("CameraManagerHelper", "startRecording called while already recording!")
            return
        }
        try {
            val fileOutputOptions = FileOutputOptions.Builder(outputFile).build()
            isRecordingVideo = true
            activeRecording = videoCapture.output
                .prepareRecording(context, fileOutputOptions)
                .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                    when (recordEvent) {
                        is VideoRecordEvent.Finalize -> {
                            isRecordingVideo = false
                            activeRecording = null
                            if (!recordEvent.hasError()) {
                                Log.i("CameraManagerHelper", "Video recording finalized successfully: ${outputFile.absolutePath}")
                                onRecordingFinished(true, outputFile.absolutePath)
                            } else {
                                Log.e("CameraManagerHelper", "Video recording error: ${recordEvent.error}")
                                onRecordingFinished(false, null)
                            }
                        }
                    }
                }
            Log.i("CameraManagerHelper", "Started video recording to ${outputFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("CameraManagerHelper", "Failed to start video recording", e)
            isRecordingVideo = false
            activeRecording = null
            onRecordingFinished(false, null)
        }
    }

    fun stopRecording() {
        try {
            activeRecording?.stop()
        } catch (e: Exception) {
            Log.e("CameraManagerHelper", "Error stopping recording", e)
        } finally {
            activeRecording = null
            isRecordingVideo = false
        }
    }

    fun switchCamera(lifecycleOwner: LifecycleOwner? = null, previewSurface: Preview.SurfaceProvider? = null) {
        if (lifecycleOwner != null) this.currentLifecycleOwner = lifecycleOwner
        if (previewSurface != null) this.currentPreviewSurface = previewSurface
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        isTorchOn = false // Torch only available on back
        bindCameraUseCases()
    }

    fun setTorch(enable: Boolean) {
        if (lensFacing == CameraSelector.LENS_FACING_BACK && camera?.cameraInfo?.hasFlashUnit() == true) {
            isTorchOn = enable
            camera?.cameraControl?.enableTorch(enable)
        }
    }

    fun setResolution(resolution: String, lifecycleOwner: LifecycleOwner? = null, previewSurface: Preview.SurfaceProvider? = null) {
        if (lifecycleOwner != null) this.currentLifecycleOwner = lifecycleOwner
        if (previewSurface != null) this.currentPreviewSurface = previewSurface
        currentResolutionString = resolution
        bindCameraUseCases()
    }

    private fun getTargetSize(res: String): Size? {
        return when (res) {
            "1080p" -> Size(1920, 1080)
            "720p" -> Size(1280, 720)
            "480p" -> Size(854, 480)
            "360p" -> Size(640, 360)
            else -> Size(1280, 720)
        }
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
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

        try {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val yPlane = imageProxy.planes[0]
            val yBuffer = yPlane.buffer
            val yRowStride = yPlane.rowStride
            val ySize = yBuffer.remaining()
            val width = imageProxy.width
            val height = imageProxy.height

            // 1. Calculate Frame Average Luma
            var totalLuma = 0L
            val step = (ySize / 500).coerceAtLeast(1)
            var sampleCount = 0
            for (i in 0 until ySize step step) {
                totalLuma += (yBuffer.get(i).toInt() and 0xFF)
                sampleCount++
            }
            val avgLuma = if (sampleCount > 0) (totalLuma.toFloat() / sampleCount) else 100f
            onLumaMeasured?.invoke(avgLuma)

            // Determine Night Vision state
            isNightVisionActive = when (nightVisionMode) {
                "on" -> true
                "off" -> false
                else -> avgLuma < autoNightVisionThreshold
            }

            // 1.5. If motion detection is enabled, we can run it on Y-buffer directly without Bitmap
            if (isMotionDetectionEnabled) {
                // Pass null for bitmap since analyzeMotion only uses yBuffer
                analyzeMotion(imageProxy, yBuffer, yRowStride, width, height, avgLuma, null, rotationDegrees)
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

            if (bitmap != null) {
                
                // toBitmap() already handles rotation in CameraX!
                val baseWidth = bitmap.width
                val baseHeight = bitmap.height

                val scaledWidth = (baseWidth * dynamicScaleFactor).toInt().coerceAtLeast(1)
                val scaledHeight = (baseHeight * dynamicScaleFactor).toInt().coerceAtLeast(1)

                // 1. Initialize reusable bitmap
                if (reusableBitmap == null || reusableBitmap!!.width != scaledWidth || reusableBitmap!!.height != scaledHeight) {
                    reusableBitmap?.recycle()
                    reusableBitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
                    renderCanvas = Canvas(reusableBitmap!!)
                }
                
                val canvas = renderCanvas!!
                val targetBitmap = reusableBitmap!!

                // 2. Clear / Draw proxyBitmap to targetBitmap with necessary transformations
                canvas.save()
                
                // Apply dynamic scaling
                canvas.scale(dynamicScaleFactor, dynamicScaleFactor)
                
                if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    canvas.translate(baseWidth.toFloat(), 0f)
                    canvas.scale(-1f, 1f) // Mirror front camera
                }

                if (isNightVisionActive) {
                    if (cachedNightVisionPaint == null) {
                        val paint = Paint()
                        val colorMatrix = ColorMatrix().apply {
                            setSaturation(0f)
                            val contrast = 1.45f
                            val brightness = 35f
                            val cm = floatArrayOf(
                                contrast, 0f, 0f, 0f, brightness,
                                0f, contrast, 0f, 0f, brightness,
                                0f, 0f, contrast, 0f, brightness,
                                0f, 0f, 0f, 1f, 0f
                            )
                            postConcat(ColorMatrix(cm))
                        }
                        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
                        cachedNightVisionPaint = paint
                    }
                    canvas.drawBitmap(bitmap, 0f, 0f, cachedNightVisionPaint)
                } else {
                    canvas.drawBitmap(bitmap, 0f, 0f, null)
                }
                canvas.restore()
                
                bitmap.recycle() // Recycle original CameraX bitmap early

                // 3. Overlay Current Time Watermark
                if (cachedDateFormat == null) {
                    cachedDateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                }
                val timeStr = cachedDateFormat!!.format(java.util.Date())

                val textSize = (targetBitmap.height * 0.022f).coerceAtLeast(12f)
                if (cachedTextPaint == null || cachedTextPaint!!.textSize != textSize) {
                    cachedTextPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        this.textSize = textSize
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.MONOSPACE
                        setShadowLayer(3f, 1f, 1f, android.graphics.Color.BLACK)
                    }
                }
                if (cachedBgPaint == null) {
                    cachedBgPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#99000000")
                        style = android.graphics.Paint.Style.FILL
                    }
                }
                val textPaint = cachedTextPaint!!
                val bgPaint = cachedBgPaint!!

                val textWidth = textPaint.measureText(timeStr)
                val margin = (targetBitmap.height * 0.015f).coerceAtLeast(8f)
                val x = margin
                val y = targetBitmap.height - margin

                val bgRect = RectF(
                    x - 6f,
                    y - textSize - 3f,
                    x + textWidth + 6f,
                    y + 4f
                )
                canvas.drawRoundRect(bgRect, 6f, 6f, bgPaint)
                canvas.drawText(timeStr, x, y, textPaint)

                // 4. Compress targetBitmap reusing ByteArrayOutputStream
                jpegOutputStream.reset()
                targetBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, jpegOutputStream)
                rawJpegBytes = jpegOutputStream.toByteArray()
                
                // Pass to video recorder
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
                        dynamicTargetFps = (dynamicTargetFps - 2).coerceAtLeast(10)
                        jpegQuality = (jpegQuality - 10).coerceAtLeast(30)
                        dynamicScaleFactor = (dynamicScaleFactor - 0.1f).coerceAtLeast(0.4f)
                    } else if (avgProcessTime < 40) { // Device has headroom
                        dynamicTargetFps = (dynamicTargetFps + 1).coerceAtMost(30)
                        jpegQuality = (jpegQuality + 5).coerceAtMost(defaultJpegQuality)
                        dynamicScaleFactor = (dynamicScaleFactor + 0.1f).coerceAtMost(1.0f)
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("CameraManagerHelper", "Error processing frame", e)
        } finally {
            imageProxy.close()
            isProcessingFrame.set(false)
        }
    }

    private fun yuv420888ToNv21(imageProxy: ImageProxy): ByteArray {
        val width = imageProxy.width
        val height = imageProxy.height
        val pixelCount = width * height
        val nv21 = ByteArray(pixelCount + (pixelCount / 2))

        val yPlane = imageProxy.planes[0]
        val uPlane = imageProxy.planes[1]
        val vPlane = imageProxy.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride

        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride

        // Copy Y plane row by row to strip rowStride padding
        var nvIndex = 0
        yBuffer.rewind()
        if (yRowStride == width) {
            yBuffer.get(nv21, 0, pixelCount)
            nvIndex = pixelCount
        } else {
            val rowBuffer = ByteArray(width)
            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                yBuffer.get(rowBuffer, 0, width)
                System.arraycopy(rowBuffer, 0, nv21, nvIndex, width)
                nvIndex += width
            }
        }

        // Copy UV channels
        uBuffer.rewind()
        vBuffer.rewind()
        val chromaHeight = height / 2
        val chromaWidth = width / 2

        for (row in 0 until chromaHeight) {
            for (col in 0 until chromaWidth) {
                val vPos = row * vRowStride + col * vPixelStride
                val uPos = row * uRowStride + col * uPixelStride

                nv21[nvIndex++] = vBuffer.get(vPos)
                nv21[nvIndex++] = uBuffer.get(uPos)
            }
        }

        return nv21
    }

    private fun applyNightVisionFilter(src: Bitmap): Bitmap {
        val dest = Bitmap.createBitmap(src.width, src.height, src.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        
        if (cachedNightVisionPaint == null) {
            val paint = Paint()
            val colorMatrix = ColorMatrix().apply {
                setSaturation(0f)
                val contrast = 1.45f
                val brightness = 35f
                val cm = floatArrayOf(
                    contrast, 0f, 0f, 0f, brightness,
                    0f, contrast, 0f, 0f, brightness,
                    0f, 0f, contrast, 0f, brightness,
                    0f, 0f, 0f, 1f, 0f
                )
                postConcat(ColorMatrix(cm))
            }
            paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
            cachedNightVisionPaint = paint
        }
        
        canvas.drawBitmap(src, 0f, 0f, cachedNightVisionPaint!!)
        return dest
    }

    private fun analyzeMotion(imageProxy: androidx.camera.core.ImageProxy, yBuffer: ByteBuffer, yRowStride: Int, width: Int, height: Int, avgLuma: Float, bitmap: Bitmap?, rotation: Int) {
        val now = System.currentTimeMillis()
        val gridWidth = 32
        val gridHeight = 24
        val totalCells = gridWidth * gridHeight
        val currentLumaGrid = FloatArray(totalCells)

        val cellW = width / gridWidth
        val cellH = height / gridHeight

        yBuffer.rewind()
        for (gy in 0 until gridHeight) {
            for (gx in 0 until gridWidth) {
                val sampleX = gx * cellW + cellW / 2
                val sampleY = gy * cellH + cellH / 2
                val index = sampleY * yRowStride + sampleX
                if (index < yBuffer.capacity()) {
                    currentLumaGrid[gy * gridWidth + gx] = (yBuffer.get(index).toInt() and 0xFF).toFloat()
                }
            }
        }

        val prev = prevLumaMatrix
        if (prev != null && prev.size == totalCells) {
            var diffCells = 0
            // 使用固定的亮度變化門檻 (原為公式: 25.0f - motionSensitivity * 1.8f)
            val thresholdDelta = 15.0f

            for (i in 0 until totalCells) {
                val diff = Math.abs(currentLumaGrid[i] - prev[i])
                if (diff > thresholdDelta) {
                    diffCells++
                }
            }

            val motionRatio = (diffCells.toFloat() / totalCells) * 100f
            // 直接使用設定值 (動態差異觸發門檻 %) 作為判定標準
            val triggerRatioThreshold = motionSensitivity.coerceIn(1.0f, 100.0f)


            if (motionRatio > triggerRatioThreshold) {
                if (now - cameraStartTime < 5000L) {
                    com.example.util.AppLogger.d("CameraManagerHelper", "忽略開機初期的不穩定動態變化")
                    return
                }

                // Motion detected!

                val cooldownMs = (motionCooldownSeconds * 1000L).coerceAtLeast(2000L)
                if (now - lastMotionTime > cooldownMs) {
                    com.example.util.AppLogger.d("CameraManagerHelper", "觸發動態警報! 當前變動比例: ${String.format("%.2f", motionRatio)}% > 門檻: ${String.format("%.1f", triggerRatioThreshold)}%")
                    lastMotionTime = now
                    
                    try {
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
                    }
                }
            }
        }

        prevLumaMatrix = currentLumaGrid
        prevMatrixWidth = gridWidth
        prevMatrixHeight = gridHeight
    }

    fun release() {
        prevLumaMatrix = null

        try {
            cameraProvider?.unbindAll()
            executor.shutdown()
        } catch (e: Exception) {
            Log.e("CameraManagerHelper", "Error releasing camera resources", e)
        }
    }
}
