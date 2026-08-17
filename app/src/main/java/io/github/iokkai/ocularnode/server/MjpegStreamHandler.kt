package io.github.iokkai.ocularnode.server

import kotlinx.coroutines.CoroutineScope
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * 負責 MJPEG 即時影像串流通道的連線生命週期管理、幀率 Conflation 與客戶端推播。
 */
class MjpegStreamHandler {

    private val mjpegSessions = CopyOnWriteArrayList<MjpegClientSession>()
    val connectedClientsCount = AtomicInteger(0)

    /**
     * 處理 /mjpeg、/stream、/live 請求：建立 MJPEG 串流 Session
     */
    fun handleStream(
        socket: Socket,
        output: OutputStream,
        scope: CoroutineScope,
        onActiveClientsChanged: ((Int) -> Unit)?
    ) {
        socket.soTimeout = 0 // 取消 Streaming Socket 的逾時限制
        val count = connectedClientsCount.incrementAndGet()
        onActiveClientsChanged?.invoke(count)

        output.write((
            "HTTP/1.1 200 OK\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Cache-Control: no-store, no-cache, must-revalidate, pre-check=0, post-check=0, max-age=0\r\n" +
            "Pragma: no-cache\r\n" +
            "Connection: close\r\n" +
            "Content-Type: multipart/x-mixed-replace; boundary=--jpgboundary\r\n\r\n"
        ).toByteArray())
        output.flush()

        val sessionHolder = AtomicReference<MjpegClientSession>()
        val session = MjpegClientSession(socket, output, scope) {
            sessionHolder.get()?.let { mjpegSessions.remove(it) }
            val c = connectedClientsCount.decrementAndGet().coerceAtLeast(0)
            onActiveClientsChanged?.invoke(c)
        }
        sessionHolder.set(session)
        mjpegSessions.add(session)
        session.start()
    }

    /**
     * 向所有已連線的 MJPEG 客戶端推播新幀
     */
    fun pushFrame(jpegBytes: ByteArray) {
        for (session in mjpegSessions) {
            session.pushFrame(jpegBytes)
        }
    }

    /**
     * 關閉所有進行中的 MJPEG 串流 Session
     */
    fun closeAll() {
        mjpegSessions.forEach {
            try { it.close() } catch (_: Exception) {}
        }
        mjpegSessions.clear()
        connectedClientsCount.set(0)
    }
}
