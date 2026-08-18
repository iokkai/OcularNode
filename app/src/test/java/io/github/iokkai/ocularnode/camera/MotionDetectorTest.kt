package io.github.iokkai.ocularnode.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 測試 32x24 灰階像素差分動態偵測演算法 (Pixel-diff Motion Detection & Sensitivity Gate)。
 */
class MotionDetectorTest {

    class MotionDetectorSimulator(
        val gridWidth: Int = 32,
        val gridHeight: Int = 24,
        var sensitivity: Float = 5.0f, // 5% of cells need to change
        val cellThreshold: Float = 15.0f,
        var cooldownSeconds: Int = 30
    ) {
        val totalCells = gridWidth * gridHeight
        private var prevGrid = FloatArray(totalCells)
        private var hasPrev = false
        private var lastMotionTime = 0L

        fun processFrame(currentGrid: FloatArray, now: Long = System.currentTimeMillis()): Pair<Boolean, Float> {
            if (!hasPrev) {
                currentGrid.copyInto(prevGrid)
                hasPrev = true
                return false to 0f
            }

            var changedCount = 0
            for (i in 0 until totalCells) {
                val diff = abs(currentGrid[i] - prevGrid[i])
                if (diff > cellThreshold) {
                    changedCount++
                }
            }

            val percentage = (changedCount.toFloat() / totalCells) * 100f
            val isMotionAboveThreshold = percentage >= sensitivity

            val shouldAlert = if (isMotionAboveThreshold) {
                if (now - lastMotionTime >= cooldownSeconds * 1000L) {
                    lastMotionTime = now
                    true
                } else {
                    false // Suppressed by cooldown
                }
            } else {
                false
            }

            currentGrid.copyInto(prevGrid)
            return shouldAlert to percentage
        }
    }

    @Test
    fun `first frame establishes baseline and never triggers motion`() {
        val detector = MotionDetectorSimulator()
        val frame1 = FloatArray(768) { 100f }
        val (triggered, percent) = detector.processFrame(frame1)
        assertFalse(triggered)
        assertEquals(0f, percent, 0.001f)
    }

    @Test
    fun `identical consecutive frames yield 0 percent change`() {
        val detector = MotionDetectorSimulator()
        val frame1 = FloatArray(768) { 100f }
        detector.processFrame(frame1)

        val frame2 = FloatArray(768) { 100f }
        val (triggered, percent) = detector.processFrame(frame2)
        assertFalse(triggered)
        assertEquals(0f, percent, 0.001f)
    }

    @Test
    fun `minor luma noise below cell threshold is completely ignored`() {
        val detector = MotionDetectorSimulator(cellThreshold = 15f)
        val frame1 = FloatArray(768) { 100f }
        detector.processFrame(frame1)

        // Minor noise: each cell changes by +5 (below 15 threshold)
        val frame2 = FloatArray(768) { 105f }
        val (triggered, percent) = detector.processFrame(frame2)
        assertFalse(triggered)
        assertEquals(0f, percent, 0.001f)
    }

    @Test
    fun `significant movement above sensitivity triggers alert`() {
        val detector = MotionDetectorSimulator(sensitivity = 5.0f, cellThreshold = 15f)
        val frame1 = FloatArray(768) { 100f }
        detector.processFrame(frame1)

        // Change 10% of cells (77 cells) by +50 brightness (object entered)
        val frame2 = FloatArray(768) { 100f }
        for (i in 0 until 80) {
            frame2[i] = 150f
        }

        val (triggered, percent) = detector.processFrame(frame2)
        assertTrue(triggered)
        assertTrue(percent > 10.0f)
    }

    @Test
    fun `cooldown suppresses repeated alerts within cooldown window`() {
        val detector = MotionDetectorSimulator(cooldownSeconds = 30)
        val frame1 = FloatArray(768) { 100f }
        detector.processFrame(frame1, now = 1000L)

        // First big motion event at t = 2000ms -> Alert!
        val frame2 = FloatArray(768) { 200f }
        val (triggered1, _) = detector.processFrame(frame2, now = 2000L)
        assertTrue(triggered1)

        // Second big motion event at t = 10000ms (only 8s later, within 30s cooldown) -> Suppressed!
        val frame3 = FloatArray(768) { 50f }
        val (triggered2, _) = detector.processFrame(frame3, now = 10000L)
        assertFalse(triggered2)

        // Third big motion event at t = 35000ms (33s later, past 30s cooldown) -> Alert!
        val frame4 = FloatArray(768) { 220f }
        val (triggered3, _) = detector.processFrame(frame4, now = 35000L)
        assertTrue(triggered3)
    }
}
