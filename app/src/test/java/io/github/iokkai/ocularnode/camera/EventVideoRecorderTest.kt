package io.github.iokkai.ocularnode.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque

/**
 * 測試 5 秒 Pre-roll 記憶體環狀緩衝與動態延長錄影演算法 (Circular Ring Buffer & PTS Timestamp Calculator)。
 */
class EventVideoRecorderTest {

    // 模擬 EventVideoRecorder 中的記憶體環狀緩衝區
    class PreRollRingBufferSimulator(
        val fps: Int = 15,
        val preRecordSeconds: Int = 5,
        val maxRecordSeconds: Int = 180,
        val basePostRecordSeconds: Int = 10
    ) {
        val maxBufferSize = fps * preRecordSeconds // 75 幀
        val ringBuffer = ArrayDeque<ByteArray>()
        var dynamicTargetPostFrames = fps * basePostRecordSeconds
        val maxFrames = fps * maxRecordSeconds

        fun pushFrame(frame: ByteArray) {
            if (ringBuffer.size >= maxBufferSize) {
                ringBuffer.removeFirst()
            }
            ringBuffer.addLast(frame)
        }

        fun onContinuousMotionTrigger(currentRecordedFrames: Int) {
            val newTarget = (currentRecordedFrames + (fps * basePostRecordSeconds)).coerceAtMost(maxFrames)
            dynamicTargetPostFrames = newTarget
        }

        fun computePtsUs(frameIndex: Long): Long {
            return frameIndex * 1_000_000L / fps
        }
    }

    @Test
    fun `buffer capacity is strictly capped at fps times preRecordSeconds`() {
        val recorder = PreRollRingBufferSimulator(fps = 15, preRecordSeconds = 5)
        assertEquals(75, recorder.maxBufferSize)

        // Push 100 frames
        for (i in 1..100) {
            recorder.pushFrame(byteArrayOf(i.toByte()))
        }

        // Buffer size must stay at 75
        assertEquals(75, recorder.ringBuffer.size)

        // The oldest remaining frame should be frame #26 (100 - 75 + 1)
        assertEquals(26.toByte(), recorder.ringBuffer.first()[0])
        // The newest frame should be frame #100
        assertEquals(100.toByte(), recorder.ringBuffer.last()[0])
    }

    @Test
    fun `pts timestamp calculation produces uniform monotonically increasing microseconds`() {
        val recorder = PreRollRingBufferSimulator(fps = 15)

        // At 15 FPS, each frame is ~66666 microseconds (1/15s)
        val pts0 = recorder.computePtsUs(0)
        val pts1 = recorder.computePtsUs(1)
        val pts15 = recorder.computePtsUs(15) // Exactly 1 second = 1,000,000 us

        assertEquals(0L, pts0)
        assertEquals(66_666L, pts1)
        assertEquals(1_000_000L, pts15)

        val pts30 = recorder.computePtsUs(30) // Exactly 2 seconds = 2,000,000 us
        assertEquals(2_000_000L, pts30)
    }

    @Test
    fun `continuous motion extends recording frames up to max ceiling`() {
        val recorder = PreRollRingBufferSimulator(fps = 15, basePostRecordSeconds = 10, maxRecordSeconds = 180)
        val maxAllowedFrames = 15 * 180 // 2700 frames (3 minutes)

        // Initially target is 150 frames (10s)
        assertEquals(150, recorder.dynamicTargetPostFrames)

        // Continuous motion occurs at frame 100
        recorder.onContinuousMotionTrigger(currentRecordedFrames = 100)
        assertEquals(250, recorder.dynamicTargetPostFrames)

        // Continuous motion occurs near maximum limit (frame 2650)
        recorder.onContinuousMotionTrigger(currentRecordedFrames = 2650)
        // Should be capped at maxAllowedFrames (2700)
        assertEquals(maxAllowedFrames, recorder.dynamicTargetPostFrames)
    }
}
