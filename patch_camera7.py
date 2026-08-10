import re

with open("app/src/main/java/com/example/camera/CameraManagerHelper.kt", "r") as f:
    content = f.read()

# Add reusable properties
props = """    private var cachedDateFormat: java.text.SimpleDateFormat? = null
    
    private var cachedNightVisionPaint: android.graphics.Paint? = null"""

props_new = """    private var cachedDateFormat: java.text.SimpleDateFormat? = null
    
    private var cachedNightVisionPaint: android.graphics.Paint? = null
    
    private var reusableBitmap: Bitmap? = null
    private var renderCanvas: Canvas? = null
    private val jpegOutputStream = ByteArrayOutputStream(1024 * 500)"""

content = content.replace(props, props_new)

old_bitmap_logic = """            if (bitmap != null) {
                if (isMotionDetectionEnabled) {
                    analyzeMotion(yBuffer, yRowStride, width, height, avgLuma, bitmap, rotationDegrees)
                }

                val matrix = Matrix()
                // toBitmap() already applies rotationDegrees, so we only need to mirror for front camera
                if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    matrix.postScale(-1f, 1f) // Mirror front camera
                }

                if (isNightVisionActive) {
                    val nvBitmap = applyNightVisionFilter(bitmap)
                    if (nvBitmap != bitmap) {
                        bitmap.recycle()
                        bitmap = nvBitmap
                    }
                }

                var transformedBitmap = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )
                if (transformedBitmap != bitmap) {
                    bitmap.recycle()
                }

                val mutableBitmap = if (transformedBitmap.isMutable) transformedBitmap else transformedBitmap.copy(Bitmap.Config.ARGB_8888, true)
                if (mutableBitmap != transformedBitmap) {
                    transformedBitmap.recycle()
                }

                // 3. Overlay Current Time Watermark at Bottom-Left Corner (Compact & Subtle)
                val canvas = Canvas(mutableBitmap)
                
                if (cachedDateFormat == null) {
                    cachedDateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                }
                val timeStr = cachedDateFormat!!.format(java.util.Date())

                val textSize = (mutableBitmap.height * 0.022f).coerceAtLeast(12f)
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
                val margin = (mutableBitmap.height * 0.015f).coerceAtLeast(8f)
                val x = margin
                val y = mutableBitmap.height - margin

                val bgRect = RectF(
                    x - 6f,
                    y - textSize - 3f,
                    x + textWidth + 6f,
                    y + 4f
                )
                canvas.drawRoundRect(bgRect, 6f, 6f, bgPaint)
                canvas.drawText(timeStr, x, y, textPaint)

val finalStream = ByteArrayOutputStream()
                mutableBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, finalStream)
                rawJpegBytes = finalStream.toByteArray()
                
                // Pass to video recorder
                onFrameReadyForRecording?.invoke(rawJpegBytes, imageProxy.imageInfo.timestamp / 1000)
                
                mutableBitmap.recycle()
            } else {
                rawJpegBytes = ByteArray(0)
            }"""

new_bitmap_logic = """            if (bitmap != null) {
                if (isMotionDetectionEnabled) {
                    analyzeMotion(yBuffer, yRowStride, width, height, avgLuma, bitmap, rotationDegrees)
                }
                
                // 1. Initialize reusable bitmap
                if (reusableBitmap == null || reusableBitmap!!.width != bitmap.width || reusableBitmap!!.height != bitmap.height) {
                    reusableBitmap?.recycle()
                    reusableBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                    renderCanvas = Canvas(reusableBitmap!!)
                }
                
                val canvas = renderCanvas!!
                val targetBitmap = reusableBitmap!!

                // 2. Clear / Draw proxyBitmap to targetBitmap with necessary transformations
                canvas.save()
                
                if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    canvas.scale(-1f, 1f, targetBitmap.width / 2f, targetBitmap.height / 2f)
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
            }"""

content = content.replace(old_bitmap_logic, new_bitmap_logic)

with open("app/src/main/java/com/example/camera/CameraManagerHelper.kt", "w") as f:
    f.write(content)
