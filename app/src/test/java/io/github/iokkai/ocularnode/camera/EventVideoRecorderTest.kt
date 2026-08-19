package io.github.iokkai.ocularnode.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque

/**
 * 測試 5 秒 Pre-roll 記憶體環狀緩衝與動態延長錄影演算法 (Circular Ring Buffer & PTS Timestamp Calculator)。
 */
class EventVideoRecorderTest {

    // 模擬 EventVideoRecorder 中的記憶體環狀緩衝區 (時間驅動)
    class PreRollRingBufferSimulator(
        val fps: Int = 30,
        val preRecordSeconds: Int = 5,
        val maxRecordSeconds: Int = 180,
        val basePostRecordSeconds: Int = 10
    ) {
        val maxBufferSize = 30 * preRecordSeconds * 2 // 300 幀容量保護
        val ringBuffer = ArrayDeque<Pair<ByteArray, Long>>()
        var dynamicTargetPostDurationUs = basePostRecordSeconds * 1_000_000L
        val maxDurationUs = maxRecordSeconds * 1_000_000L

        private var baseTimestampUs = -1L
        private var lastPtsUs = -1L

        fun pushFrame(frame: ByteArray, presentationTimeUs: Long) {
            if (presentationTimeUs > 0) {
                val cutoff = presentationTimeUs - (preRecordSeconds * 1_000_000L)
                while (ringBuffer.isNotEmpty() && ringBuffer.first().second > 0 && ringBuffer.first().second < cutoff) {
                    ringBuffer.removeFirst()
                }
            }
            if (ringBuffer.size >= maxBufferSize) {
                ringBuffer.removeFirst()
            }
            ringBuffer.addLast(frame to presentationTimeUs)
        }

        fun onContinuousMotionTrigger() {
            val newTarget = (dynamicTargetPostDurationUs + (basePostRecordSeconds * 1_000_000L)).coerceAtMost(maxDurationUs)
            dynamicTargetPostDurationUs = newTarget
        }

        fun computePtsUs(presentationTimeUs: Long): Long {
            val framePtsUs = if (presentationTimeUs > 0) {
                if (baseTimestampUs < 0) {
                    baseTimestampUs = presentationTimeUs
                    0L
                } else {
                    (presentationTimeUs - baseTimestampUs).coerceAtLeast(0L)
                }
            } else {
                if (lastPtsUs < 0) 0L else lastPtsUs + (1_000_000L / fps)
            }

            val ptsUs = if (framePtsUs > lastPtsUs) framePtsUs else lastPtsUs + 1_000L
            lastPtsUs = ptsUs
            return ptsUs
        }
    }

    @Test
    fun `buffer strictly retains only frames within the 5s time window regardless of frame rate`() {
        val recorder = PreRollRingBufferSimulator(preRecordSeconds = 5)

        // Simulate 10 FPS (100ms per frame) over 8 seconds
        val startTimeUs = 10_000_000L
        for (i in 0 until 80) { // 8 seconds, 80 frames
            val timestampUs = startTimeUs + i * 100_000L
            recorder.pushFrame(byteArrayOf(i.toByte()), timestampUs)
        }

        // 5 seconds window at 100ms = 50-51 frames
        val oldestRemainingTimestamp = recorder.ringBuffer.first().second
        val newestTimestamp = recorder.ringBuffer.last().second
        val timeSpanUs = newestTimestamp - oldestRemainingTimestamp

        assertTrue("Retained window should not exceed 5 seconds", timeSpanUs <= 5_000_000L)
        assertEquals(51, recorder.ringBuffer.size)
    }

    @Test
    fun `dynamic pts timestamp calculation matches real time and is strictly monotonic`() {
        val recorder = PreRollRingBufferSimulator(fps = 30)

        // Real frame timestamps from sensor in microseconds
        val t0 = 100_000_000L // 100s
        val t1 = t0 + 33_333L // +33.3ms (~30fps)
        val t2 = t0 + 66_666L // +66.6ms
        val t30 = t0 + 1_000_000L // +1.0s

        assertEquals(0L, recorder.computePtsUs(t0))
        assertEquals(33_333L, recorder.computePtsUs(t1))
        assertEquals(66_666L, recorder.computePtsUs(t2))
        assertEquals(1_000_000L, recorder.computePtsUs(t30))
    }

    @Test
    fun `dynamic pts handles duplicate timestamps by guaranteeing monotonicity`() {
        val recorder = PreRollRingBufferSimulator(fps = 30)

        val t0 = 50_000_000L
        val pts0 = recorder.computePtsUs(t0)
        val pts1 = recorder.computePtsUs(t0) // Duplicate timestamp
        val pts2 = recorder.computePtsUs(t0 + 10_000L)

        assertEquals(0L, pts0)
        assertTrue("Duplicate frame must have strictly greater PTS", pts1 > pts0)
        assertEquals(1_000L, pts1)
        assertEquals(10_000L, pts2)
    }

    @Test
    fun `continuous motion extends recording duration up to max ceiling in microseconds`() {
        val recorder = PreRollRingBufferSimulator(basePostRecordSeconds = 10, maxRecordSeconds = 180)
        val maxAllowedDurationUs = 180 * 1_000_000L // 180s in us

        // Initially target is 10s (10,000,000 us)
        assertEquals(10_000_000L, recorder.dynamicTargetPostDurationUs)

        // Continuous motion occurs
        recorder.onContinuousMotionTrigger()
        assertEquals(20_000_000L, recorder.dynamicTargetPostDurationUs)

        // Extend many times past ceiling
        for (i in 1..30) {
            recorder.onContinuousMotionTrigger()
        }
        // Should be capped at maxAllowedDurationUs (180s)
        assertEquals(maxAllowedDurationUs, recorder.dynamicTargetPostDurationUs)
    }
}
