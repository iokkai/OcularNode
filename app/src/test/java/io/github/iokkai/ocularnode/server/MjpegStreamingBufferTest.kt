package io.github.iokkai.ocularnode.server

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 測試 MJPEG 串流 Conflated Channel 非阻塞與防 OOM 緩衝機制 (Non-blocking Backpressure & OOM Immunity)。
 */
class MjpegStreamingBufferTest {

    @Test
    fun `conflated channel always drops stale unread frames without blocking producer`() = runBlocking {
        val frameChannel = Channel<ByteArray>(Channel.CONFLATED)

        // Producer sends 1000 frames without consumer reading
        for (i in 1..1000) {
            val frameData = byteArrayOf((i % 128).toByte())
            val result = frameChannel.trySend(frameData)
            assertTrue("trySend must always succeed immediately", result.isSuccess)
        }

        // Consumer reads ONE frame -> it MUST be the 1000th (newest) frame!
        val newestFrame = frameChannel.receive()
        assertEquals((1000 % 128).toByte(), newestFrame[0])

        // After reading the newest frame, the channel should be empty (no backlogged 999 frames in RAM!)
        val pollResult = frameChannel.tryReceive()
        assertTrue("Channel must not hold backlog of older frames", pollResult.isFailure)
    }

    @Test
    fun `closed session closes channel and terminates consumer cleanly`() = runBlocking {
        val frameChannel = Channel<ByteArray>(Channel.CONFLATED)
        frameChannel.trySend(byteArrayOf(1))

        // Consumer reads first frame
        val frame = frameChannel.receive()
        assertEquals(1.toByte(), frame[0])

        // Session disconnects / socket closes
        frameChannel.close()
        assertTrue(frameChannel.isClosedForSend)
        assertTrue(frameChannel.isClosedForReceive)
    }
}
