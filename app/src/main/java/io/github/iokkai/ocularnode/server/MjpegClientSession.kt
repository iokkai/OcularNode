package io.github.iokkai.ocularnode.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.net.Socket

/**
 * 代表一個已連線的 MJPEG 串流客戶端 Session。
 * 使用協程事件驅動通道 Conflation 機制（Channel.CONFLATED），
 * 消除 10ms 忙碌輪詢延遲，無新幀時 0% CPU 消耗，新幀到達時微秒級即時發送。
 */
class MjpegClientSession(
    val socket: Socket,
    val outputStream: OutputStream,
    val scope: CoroutineScope,
    val onDisconnected: () -> Unit
) {
    private val frameChannel = Channel<ByteArray>(Channel.CONFLATED)
    private var job: Job? = null

    /** 推播新影像幀（若客戶端未及讀取則自動 Conflate 合併，保留最新一幀） */
    fun pushFrame(bytes: ByteArray) {
        frameChannel.trySend(bytes)
    }

    fun start() {
        job = scope.launch(Dispatchers.IO) {
            try {
                socket.tcpNoDelay = true
                socket.sendBufferSize = 65536
                for (frame in frameChannel) {
                    if (!isActive) break
                    val header = "--jpgboundary\r\nContent-Type: image/jpeg\r\nContent-Length: ${frame.size}\r\n\r\n"
                    outputStream.write(header.toByteArray(Charsets.UTF_8))
                    outputStream.write(frame)
                    outputStream.write("\r\n".toByteArray(Charsets.UTF_8))
                    outputStream.flush()
                }
            } catch (_: Exception) {
                // Connection closed or reset by client
            } finally {
                frameChannel.close()
                try { socket.close() } catch (_: Exception) {}
                onDisconnected()
            }
        }
    }

    fun close() {
        frameChannel.close()
        job?.cancel()
        try { socket.close() } catch (_: Exception) {}
    }
}
