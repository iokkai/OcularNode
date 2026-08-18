package io.github.iokkai.ocularnode.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * 測試觀看端 MJPEG 畫面串流封包解析與 ReusableByteBuffer 機制 (MJPEG Stream Delimiter Parsing & Buffer Compaction)。
 */
class CameraStreamClientTest {

    // 模擬 CameraStreamClient 內部的位元組緩衝區
    class TestStreamBuffer(initialCapacity: Int = 1024) {
        var buffer = ByteArray(initialCapacity)
        var size = 0

        fun write(b: ByteArray, off: Int, len: Int) {
            ensureCapacity(size + len)
            System.arraycopy(b, off, buffer, size, len)
            size += len
        }

        fun compact(consumed: Int) {
            if (consumed >= size) {
                size = 0
            } else {
                val remaining = size - consumed
                System.arraycopy(buffer, consumed, buffer, 0, remaining)
                size = remaining
            }
        }

        private fun ensureCapacity(minCapacity: Int) {
            if (minCapacity > buffer.size) {
                var newCap = buffer.size * 2
                if (newCap < minCapacity) newCap = minCapacity
                val newBuf = ByteArray(newCap)
                System.arraycopy(buffer, 0, newBuf, 0, size)
                buffer = newBuf
            }
        }
    }

    private fun findSequence(data: ByteArray, sequence: ByteArray, startOffset: Int = 0, limit: Int = data.size): Int {
        if (limit - startOffset < sequence.size) return -1
        for (i in startOffset..limit - sequence.size) {
            var match = true
            for (j in sequence.indices) {
                if (data[i + j] != sequence[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
    }

    @Test
    fun `findSequence correctly finds JPEG SOI and EOI markers in stream`() {
        val soi = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        val eoi = byteArrayOf(0xFF.toByte(), 0xD9.toByte())

        // Create a mock stream with headers and a JPEG payload: [Header...][SOI][Payload...][EOI][Trailer...]
        val streamData = byteArrayOf(
            0x2D, 0x2D, 0x66, 0x72, 0x61, 0x6D, 0x65, // "--frame"
            0xFF.toByte(), 0xD8.toByte(),               // SOI (index 7, 8)
            0x01, 0x02, 0x03, 0x04,                     // JPEG payload
            0xFF.toByte(), 0xD9.toByte(),               // EOI (index 13, 14)
            0x0D, 0x0A                                  // "\r\n"
        )

        val startIndex = findSequence(streamData, soi, 0, streamData.size)
        assertEquals(7, startIndex)

        val endIndex = findSequence(streamData, eoi, startIndex + 2, streamData.size)
        assertEquals(13, endIndex)

        // Total JPEG length = (endIndex + 2) - startIndex = 15 - 7 = 8 bytes
        val jpegLength = (endIndex + 2) - startIndex
        assertEquals(8, jpegLength)
    }

    @Test
    fun `buffer compacting correctly retains remaining bytes across segmented network packets`() {
        val streamBuffer = TestStreamBuffer(128)
        val soi = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        val eoi = byteArrayOf(0xFF.toByte(), 0xD9.toByte())

        // Packet 1: Contains first complete frame and partial second frame
        // Frame 1: SOI (0), payload (2,3), EOI (4,5) -> 6 bytes
        // Frame 2 (partial): SOI (6,7), payload (8,9)
        val packet1 = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0x11, 0x12, 0xFF.toByte(), 0xD9.toByte(),
            0xFF.toByte(), 0xD8.toByte(), 0x21, 0x22
        )
        streamBuffer.write(packet1, 0, packet1.size)
        assertEquals(10, streamBuffer.size)

        // Extract Frame 1
        val start1 = findSequence(streamBuffer.buffer, soi, 0, streamBuffer.size)
        val end1 = findSequence(streamBuffer.buffer, eoi, start1 + 2, streamBuffer.size)
        assertEquals(0, start1)
        assertEquals(4, end1)

        // Consume Frame 1 (consumed = 4 + 2 = 6)
        streamBuffer.compact(end1 + 2)
        assertEquals(4, streamBuffer.size) // 4 bytes remaining from partial Frame 2

        // Packet 2 arrives with remainder of Frame 2: payload (30), EOI (31, 32)
        val packet2 = byteArrayOf(0x23, 0xFF.toByte(), 0xD9.toByte())
        streamBuffer.write(packet2, 0, packet2.size)
        assertEquals(7, streamBuffer.size)

        // Extract Frame 2
        val start2 = findSequence(streamBuffer.buffer, soi, 0, streamBuffer.size)
        val end2 = findSequence(streamBuffer.buffer, eoi, start2 + 2, streamBuffer.size)
        assertEquals(0, start2)
        assertEquals(5, end2)

        // Consume Frame 2
        streamBuffer.compact(end2 + 2)
        assertEquals(0, streamBuffer.size)
    }

    @Test
    fun `findSequence returns minus one when sequence is absent or partial`() {
        val soi = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        val incompleteData = byteArrayOf(0x01, 0x02, 0xFF.toByte()) // Missing 0xD8

        val startIndex = findSequence(incompleteData, soi, 0, incompleteData.size)
        assertEquals(-1, startIndex)
    }
}
