package io.github.iokkai.ocularnode.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference

/**
 * 代表一個已連線的 MJPEG 串流客戶端 Session。
 * 使用 Conflation 機制（AtomicReference）確保低端設備下不會因幀率超出輸出能力而 OOM。
 */
class MjpegClientSession(
    val socket: Socket,
    val outputStream: OutputStream,
    val scope: CoroutineScope,
    val onDisconnected: () -> Unit
) {
    private val latestFrame = AtomicReference<ByteArray?>(null)
    private var job: Job? = null

    /** 推播新影像幀（衝突時丟棄舊幀，保留最新） */
    fun pushFrame(bytes: ByteArray) {
        latestFrame.set(bytes)
    }

    fun start() {
        job = scope.launch(Dispatchers.IO) {
            try {
                socket.tcpNoDelay = true
                socket.sendBufferSize = 32768
                while (isActive) {
                    val frame = latestFrame.getAndSet(null)
                    if (frame != null) {
                        val header = "--jpgboundary\r\nContent-Type: image/jpeg\r\nContent-Length: ${frame.size}\r\n\r\n"
                        outputStream.write(header.toByteArray())
                        outputStream.write(frame)
                        outputStream.write("\r\n".toByteArray())
                        outputStream.flush()
                    } else {
                        delay(10)
                    }
                }
            } catch (_: Exception) {
                // Connection closed or reset by client
            } finally {
                try { socket.close() } catch (_: Exception) {}
                onDisconnected()
            }
        }
    }

    fun close() {
        job?.cancel()
        try { socket.close() } catch (_: Exception) {}
    }
}
