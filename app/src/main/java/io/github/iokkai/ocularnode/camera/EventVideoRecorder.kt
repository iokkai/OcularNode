package io.github.iokkai.ocularnode.camera

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicReference

enum class RecorderState {
    IDLE,
    RECORDING,
    COOLDOWN
}

data class FrameData(
    val jpegBytes: ByteArray,
    val presentationTimeUs: Long
)

/**
 * 專為事件驅動設計的影片錄製器，內建環狀預錄緩衝區 (Circular Pre-roll Buffer)。
 * 當事件觸發時，能將發生前 (Pre-roll) 與發生後 (Post-roll) 的畫面結合成一段完整的影片。
 */
class EventVideoRecorder(
    private val outputDir: File,
    private val width: Int = 1280,
    private val height: Int = 720,
    private val fps: Int = 15,
    private val preRecordSeconds: Int = 5,
    private val basePostRecordSeconds: Int = 10,
    private val maxRecordSeconds: Int = 180 // 最大錄製 3 分鐘 (180秒)
) {
    private val tag = "EventVideoRecorder"
    
    // 狀態管理：使用 AtomicReference 確保跨執行緒狀態切換的原子性與安全性
    private val state = AtomicReference(RecorderState.IDLE)
    
    // 環狀緩衝區，存放過去特定秒數的畫面
    private val maxBufferSize = fps * preRecordSeconds
    private val preRollBuffer = ArrayDeque<FrameData>(maxBufferSize)
    private val bufferMutex = Mutex()
    
    // 動態目標幀數 (支援延長錄影)
    private val dynamicTargetPostFrames = java.util.concurrent.atomic.AtomicInteger(0)
    
    // 錄影相關 CoroutineScope (在背景執行緒中處理耗時編碼)
    private val recorderScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var recordingJob: Job? = null
    private val onFinishedCallbacks = java.util.concurrent.CopyOnWriteArrayList<(java.io.File?) -> Unit>()
    
    // 即時畫面通道，供 RECORDING 狀態時傳遞即時新畫面給編碼器處理
    // 設定容量為 unlimited 是為了不阻塞相機的 pushFrame，但記憶體管理需謹慎
    private val realTimeFrames = Channel<FrameData>(Channel.UNLIMITED)
    
    /**
     * 接收即時畫面供分析或錄影。
     * - 若為 IDLE：將畫面存入環狀緩衝區 (滿了則剔除最舊)。
     * - 若為 RECORDING：將畫面送入即時通道供編碼器處理。
     * - 若為 COOLDOWN：直接捨棄並釋放。
     *
     * @param bitmap 相機擷取之 Bitmap 畫面。
     * @param presentationTimeUs 畫面的呈現時間戳 (微秒)。
     */
    fun pushFrame(jpegBytes: ByteArray, presentationTimeUs: Long) {
        val frameData = FrameData(jpegBytes, presentationTimeUs)
        
        recorderScope.launch {
            when (state.get()) {
                RecorderState.IDLE -> {
                    bufferMutex.withLock {
                        if (preRollBuffer.size >= maxBufferSize) {
                            preRollBuffer.removeFirst()
                        }
                        preRollBuffer.addLast(frameData)
                    }
                }
                RecorderState.RECORDING -> {
                    realTimeFrames.trySend(frameData)
                }
                else -> {
                }
            }
        }
    }
    
    /**
     * 觸發事件錄影。
     * 若當前狀態為 IDLE，將狀態切換至 RECORDING，並啟動非同步編碼與寫檔流程。
     * 若當前已經在 RECORDING，則延長錄影時間（不超過最大限制）。
     *
     * @param onFinished 錄影完成的回呼，並回傳存檔的 File，若失敗則回傳 null。
     */
    fun triggerRecording(onFinished: (File?) -> Unit = {}) {
        if (state.compareAndSet(RecorderState.IDLE, RecorderState.RECORDING)) {
            Log.i(tag, "觸發事件錄影，狀態切換為 RECORDING")
            onFinishedCallbacks.clear()
            onFinishedCallbacks.add(onFinished)
            dynamicTargetPostFrames.set(fps * basePostRecordSeconds)
            val outputFile = File(outputDir, "event_video_${System.currentTimeMillis()}.mp4")
            
            recordingJob = recorderScope.launch {
                var success = false
                try {
                    recordVideo(outputFile)
                    success = true
                } catch (e: Exception) {
                    Log.e(tag, "錄影過程發生錯誤", e)
                } finally {
                    state.set(RecorderState.COOLDOWN)
                    Log.i(tag, "錄影結束，進入 COOLDOWN (冷卻期)")
                    
                    // 冷卻時間，避免連續頻繁觸發同一事件
                    delay(3000L) 
                    state.set(RecorderState.IDLE)
                    Log.i(tag, "冷卻結束，回到 IDLE")
                    
                    withContext(Dispatchers.Main) {
                        onFinishedCallbacks.forEach { it(if (success) outputFile else null) }
                        onFinishedCallbacks.clear()
                    }
                }
            }
        } else if (state.get() == RecorderState.RECORDING) {
            // 如果已經在錄影中，則延長錄影時間
            onFinishedCallbacks.add(onFinished)
            val maxFrames = fps * maxRecordSeconds
            val currentTarget = dynamicTargetPostFrames.get()
            // 延長 basePostRecordSeconds，但不能超過最大限制
            val newTarget = (currentTarget + (fps * basePostRecordSeconds)).coerceAtMost(maxFrames)
            dynamicTargetPostFrames.set(newTarget)
            Log.i(tag, "持續偵測到動態，延長錄影時間！新目標幀數: $newTarget / 最大限制: $maxFrames")
        } else {
            Log.d(tag, "忽略觸發：目前狀態為 COOLDOWN")
            // In cooldown, we might just return null immediately or ignore
            onFinished(null)
        }
    }
    
    /**
     * 停止當前所有操作並釋放資源，包含清空 Buffer
     */
    fun release() {
        recordingJob?.cancel()
        recorderScope.cancel()
        
        // 清空並安全釋放緩衝區中的所有 Bitmap
        recorderScope.launch {
            bufferMutex.withLock {
                while (preRollBuffer.isNotEmpty()) {
                    preRollBuffer.removeFirst()
                }
            }
        }
    }
    
    /**
     * 執行影片編碼與 MediaMuxer 寫檔的核心邏輯。
     */
    private suspend fun recordVideo(outputFile: File) {
        // 1. 將 Pre-roll Buffer 內的畫面完整取出並清空 Buffer
        val historicalFrames = mutableListOf<FrameData>()
        bufferMutex.withLock {
            historicalFrames.addAll(preRollBuffer)
            preRollBuffer.clear()
        }

        // 偵測首張畫面的實際寬高，動態設定 MediaCodec 編碼解析度，避免畫面比例變形拉伸
        val sampleBytes = historicalFrames.firstOrNull()?.jpegBytes
            ?: withTimeoutOrNull(2000L) { realTimeFrames.receive() }?.also { historicalFrames.add(it) }?.jpegBytes

        val sampleOpts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (sampleBytes != null) {
            android.graphics.BitmapFactory.decodeByteArray(sampleBytes, 0, sampleBytes.size, sampleOpts)
        }

        val rawFrameW = if (sampleOpts.outWidth > 0) sampleOpts.outWidth else width
        val rawFrameH = if (sampleOpts.outHeight > 0) sampleOpts.outHeight else height

        val encWidth = ((rawFrameW + 15) / 16) * 16
        val encHeight = ((rawFrameH + 15) / 16) * 16

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val chosenColorFormat = selectSupportedColorFormat(codec)

        // 設定 H.264 / AVC 影片格式
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, encWidth, encHeight).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, chosenColorFormat)
            setInteger(MediaFormat.KEY_BIT_RATE, 2500000) // 2.5 Mbps 位元率
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // 關鍵幀間隔 1 秒
        }

        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        // 建立 MediaMuxer 負責將編碼後的資料寫入 MP4 容器
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var isMuxerStarted = false

        val bufferInfo = MediaCodec.BufferInfo()
        val timeoutUs = 10000L

        var encodedPostFrames = 0
        var isEos = false
        var frameIndex = 0L

        Log.i(tag, "開始編碼歷史畫面，共取出 ${historicalFrames.size} 幀，動態解析度: ${encWidth}x${encHeight} (原圖: ${rawFrameW}x${rawFrameH})，ColorFormat: $chosenColorFormat")

        // 內部 Helper：將 MediaCodec 編碼完的資料抽出並交由 MediaMuxer 寫入檔案
        fun drainOutput() {
            while (true) {
                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    break
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (isMuxerStarted) {
                        throw IllegalStateException("格式已被設定過，無法再次變更")
                    }
                    val newFormat = codec.outputFormat
                    trackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    isMuxerStarted = true
                } else if (outputBufferIndex >= 0) {
                    val encodedData = codec.getOutputBuffer(outputBufferIndex)
                        ?: throw RuntimeException("無法獲取編碼緩衝區資料")

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size != 0) {
                        if (!isMuxerStarted) {
                            throw RuntimeException("嘗試寫入資料但 MediaMuxer 尚未啟動")
                        }
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                    }

                    codec.releaseOutputBuffer(outputBufferIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                }
            }
        }

        // 內部 Helper：將 FrameData 轉碼放入 MediaCodec (維持比例，防止畫面變形)
        fun encodeFrame(frame: FrameData, isEndOfStream: Boolean) {
            val options = android.graphics.BitmapFactory.Options().apply {
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            }
            var bmp = android.graphics.BitmapFactory.decodeByteArray(frame.jpegBytes, 0, frame.jpegBytes.size, options) ?: return
            if (bmp.width != encWidth || bmp.height != encHeight) {
                val targetBmp = android.graphics.Bitmap.createBitmap(encWidth, encHeight, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(targetBmp)
                val scale = Math.min(encWidth.toFloat() / bmp.width, encHeight.toFloat() / bmp.height)
                val dx = (encWidth - bmp.width * scale) / 2f
                val dy = (encHeight - bmp.height * scale) / 2f
                val matrix = android.graphics.Matrix().apply {
                    postScale(scale, scale)
                    postTranslate(dx, dy)
                }
                canvas.drawBitmap(bmp, matrix, null)
                bmp.recycle()
                bmp = targetBmp
            }
            val yuvBytes = if (chosenColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
                getI420FromBitmap(bmp, encWidth, encHeight)
            } else {
                getNV12FromBitmap(bmp, encWidth, encHeight)
            }
            bmp.recycle()

            var inputBufferIndex = -1
            var retries = 0
            while (inputBufferIndex < 0 && retries < 10) {
                inputBufferIndex = codec.dequeueInputBuffer(timeoutUs)
                if (inputBufferIndex < 0) {
                    drainOutput()
                    retries++
                }
            }

            if (inputBufferIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                if (inputBuffer != null) {
                    inputBuffer.clear()
                    val bytesToPut = Math.min(yuvBytes.size, inputBuffer.remaining())
                    inputBuffer.put(yuvBytes, 0, bytesToPut)
                    val ptsUs = frameIndex * 1_000_000L / fps
                    frameIndex++
                    val flags = if (isEndOfStream) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                    codec.queueInputBuffer(inputBufferIndex, 0, bytesToPut, ptsUs, flags)
                }
            }
        }

        try {
            // 首先，編碼所有歷史緩衝區的畫面
            for (frame in historicalFrames) {
                if (!kotlin.coroutines.coroutineContext.isActive) break
                encodeFrame(frame, false)
                drainOutput()
            }

            Log.i(tag, "歷史畫面編碼完成。開始擷取即時畫面")

            // 2. 持續從 Channel 獲取新的即時畫面並進行編碼
            while (kotlin.coroutines.coroutineContext.isActive && !isEos) {
                val frameData = withTimeoutOrNull(2000L) {
                    realTimeFrames.receive()
                }

                if (frameData != null) {
                    encodedPostFrames++
                    val currentTargetPostFrames = dynamicTargetPostFrames.get()
                    val isLastFrame = encodedPostFrames >= currentTargetPostFrames

                    encodeFrame(frameData, isLastFrame)
                    drainOutput()

                    if (isLastFrame) {
                        isEos = true
                        Log.i(tag, "已達成目標即時幀數 ($currentTargetPostFrames)，正常結束錄製。")
                    }
                } else {
                    Log.w(tag, "超過 2 秒未收到新畫面，將強制結束錄製流程。")
                    val inputBufferIndex = codec.dequeueInputBuffer(timeoutUs)
                    if (inputBufferIndex >= 0) {
                        codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    }
                    drainOutput()
                    isEos = true
                }
            }
        } finally {
            // 3. 安全停止並釋放 MediaCodec 與 MediaMuxer
            try {
                codec.stop()
            } catch (e: Exception) {
                Log.e(tag, "codec.stop 失敗", e)
            }
            try {
                codec.release()
            } catch (e: Exception) {
                Log.e(tag, "codec.release 失敗", e)
            }

            try {
                if (isMuxerStarted) {
                    try {
                        muxer.stop()
                    } catch (e: Exception) {
                        Log.e(tag, "muxer.stop 失敗", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "MediaMuxer 異常", e)
            } finally {
                try {
                    muxer.release()
                } catch (e: Exception) {
                    Log.e(tag, "muxer.release 失敗", e)
                }
            }

            // 4. 清空 Channel 中未處理完的即時畫面並釋放，防止洩漏
            while (true) {
                realTimeFrames.tryReceive().getOrNull() ?: break
            }
        }
    }

    private fun selectSupportedColorFormat(codec: MediaCodec): Int {
        return try {
            val caps = codec.codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
            if (caps.colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)) {
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
            } else if (caps.colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar)) {
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
            } else {
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
            }
        } catch (e: Exception) {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
        }
    }

    /**
     * 將 Bitmap 轉換為 YUV420 Semi-Planar (NV12) 格式，供 MediaCodec 編碼。
     */
    private fun getNV12FromBitmap(bitmap: Bitmap, width: Int, height: Int): ByteArray {
        val size = width * height
        val pixels = IntArray(size)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val yuv = ByteArray(size * 3 / 2)
        var yIndex = 0
        var uvIndex = size

        for (j in 0 until height) {
            val rowOffset = j * width
            val isEvenRow = (j % 2 == 0)
            for (i in 0 until width) {
                val argb = pixels[rowOffset + i]
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF

                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[yIndex++] = y.coerceIn(0, 255).toByte()

                if (isEvenRow && (i % 2 == 0)) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    if (uvIndex + 1 < yuv.size) {
                        yuv[uvIndex++] = u.coerceIn(0, 255).toByte()
                        yuv[uvIndex++] = v.coerceIn(0, 255).toByte()
                    }
                }
            }
        }
        return yuv
    }

    /**
     * 將 Bitmap 轉換為 YUV420 Planar (I420) 格式。
     */
    private fun getI420FromBitmap(bitmap: Bitmap, width: Int, height: Int): ByteArray {
        val size = width * height
        val pixels = IntArray(size)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val yuv = ByteArray(size * 3 / 2)
        var yIndex = 0
        var uIndex = size
        var vIndex = size + (size / 4)

        for (j in 0 until height) {
            val rowOffset = j * width
            val isEvenRow = (j % 2 == 0)
            for (i in 0 until width) {
                val argb = pixels[rowOffset + i]
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF

                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[yIndex++] = y.coerceIn(0, 255).toByte()

                if (isEvenRow && (i % 2 == 0)) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    if (uIndex < size + (size / 4)) {
                        yuv[uIndex++] = u.coerceIn(0, 255).toByte()
                    }
                    if (vIndex < yuv.size) {
                        yuv[vIndex++] = v.coerceIn(0, 255).toByte()
                    }
                }
            }
        }
        return yuv
    }
}
