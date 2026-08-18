package io.github.iokkai.ocularnode.ui.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 測試觀看端自適應防振盪階梯演算法 (Viewer Adaptive Ladder & Anti-Flapping Algorithm)。
 */
class ViewerViewModelAdaptiveTest {

    data class MockAdaptiveState(
        val isEnabled: Boolean = false,
        val isDowngraded: Boolean = false,
        val currentResolution: String = "360p",
        val currentQuality: Int = 30,
        val targetFps: Int = 15,
        val labelText: String = "⚡ 360p (手動模式)",
        val reasonText: String = "",
        val pingMs: Int = -1,
        val fps: Int = 0
    )

    // 模擬 ViewerViewModel 中的自適應計算引擎
    class AdaptiveEngineSimulator {
        var state = MockAdaptiveState()
        var lagConsecutiveCount = 0
        var severeConsecutiveCount = 0
        var recoveryConsecutiveCount = 0
        var lastDowngradeTime = 0L
        var lastUpgradeTime = 0L
        var flappingLockoutUntil = 0L

        fun tick(
            currentFps: Int,
            pingMs: Int,
            timeSinceLastFrame: Long,
            now: Long,
            serverRes: String,
            serverQual: Int
        ) {
            if (!state.isEnabled) {
                state = state.copy(
                    currentResolution = serverRes,
                    currentQuality = serverQual,
                    labelText = "⚡ $serverRes ($serverQual% 手動)"
                )
                return
            }

            val currentLevel = when (serverRes) {
                "360p" -> 0
                "480p" -> 1
                "720p" -> 2
                "1080p" -> 3
                else -> 0
            }

            val isSevereLowFps = currentFps in 1..5 || (currentFps == 0 && now - lastDowngradeTime > 5000)
            val isModerateLowFps = currentFps in 6..11
            val isHighPing = pingMs > 350
            val isSeverePing = pingMs > 600
            val isFrameLag = timeSinceLastFrame > 2000
            val isSevereFrameLag = timeSinceLastFrame > 3500

            val needsLagAction = isModerateLowFps || isHighPing || isFrameLag
            val needsSevereAction = isSevereLowFps || isSeverePing || isSevereFrameLag

            if (needsSevereAction) {
                severeConsecutiveCount++
                lagConsecutiveCount++
                recoveryConsecutiveCount = 0
            } else if (needsLagAction) {
                lagConsecutiveCount++
                severeConsecutiveCount = 0
                recoveryConsecutiveCount = 0
            } else {
                lagConsecutiveCount = 0
                severeConsecutiveCount = 0
                if (pingMs in 1..150 && currentFps >= 14 && timeSinceLastFrame < 800 && now > flappingLockoutUntil) {
                    recoveryConsecutiveCount++
                } else {
                    recoveryConsecutiveCount = 0
                }
            }

            // 嚴重降級 (2 次嚴重卡頓)
            if (severeConsecutiveCount >= 2 && currentLevel > 0 && (now - lastDowngradeTime > 5000)) {
                if (now - lastUpgradeTime < 30_000L) {
                    flappingLockoutUntil = now + 120_000L
                }
                lastDowngradeTime = now
                recoveryConsecutiveCount = 0
                state = state.copy(
                    isEnabled = true,
                    isDowngraded = true,
                    currentResolution = "360p",
                    currentQuality = 20,
                    targetFps = 15,
                    reasonText = "嚴重卡頓降級"
                )
            }
            // 溫和降級 (3 次卡頓)
            else if (lagConsecutiveCount >= 3 && currentLevel > 0 && (now - lastDowngradeTime > 6000)) {
                if (now - lastUpgradeTime < 30_000L) {
                    flappingLockoutUntil = now + 120_000L
                }
                lastDowngradeTime = now
                recoveryConsecutiveCount = 0
                val nextLevel = (currentLevel - 1).coerceAtLeast(0)
                val (nextRes, nextQual, nextFps) = when (nextLevel) {
                    0 -> Triple("360p", 25, 15)
                    1 -> Triple("480p", 30, 15)
                    else -> Triple("360p", 20, 15)
                }
                state = state.copy(
                    isEnabled = true,
                    isDowngraded = true,
                    currentResolution = nextRes,
                    currentQuality = nextQual,
                    targetFps = nextFps,
                    reasonText = "溫和降級"
                )
            }
            // 漸進式升級 (連續 15 次穩定，每次只升一階)
            else if (recoveryConsecutiveCount >= 15 && currentLevel < 2 && now > flappingLockoutUntil && (now - lastDowngradeTime > 20000)) {
                recoveryConsecutiveCount = 0
                lastUpgradeTime = now
                val nextLevel = currentLevel + 1
                val (nextRes, nextQual, nextFps) = when (nextLevel) {
                    1 -> Triple("480p", 30, 15)
                    2 -> Triple("720p", 40, 20)
                    else -> Triple("480p", 30, 15)
                }
                state = state.copy(
                    isEnabled = true,
                    isDowngraded = false,
                    currentResolution = nextRes,
                    currentQuality = nextQual,
                    targetFps = nextFps,
                    reasonText = "連線良好已升級"
                )
            }
        }
    }

    @Test
    fun `initial state defaults to lowest 360p quality 30 and manual mode`() {
        val engine = AdaptiveEngineSimulator()
        assertFalse(engine.state.isEnabled)
        assertEquals("360p", engine.state.currentResolution)
        assertEquals(30, engine.state.currentQuality)
        assertEquals(15, engine.state.targetFps)
    }

    @Test
    fun `manual mode reflects server resolution without triggering auto upgrade`() {
        val engine = AdaptiveEngineSimulator()
        // In manual mode, server is at 720p 60%
        engine.tick(currentFps = 15, pingMs = 50, timeSinceLastFrame = 100, now = 10000L, serverRes = "720p", serverQual = 60)
        assertEquals("720p", engine.state.currentResolution)
        assertEquals(60, engine.state.currentQuality)
        assertFalse(engine.state.isEnabled)
    }

    @Test
    fun `upgrade requires strictly 15 consecutive stable ticks and upgrades only one step`() {
        val engine = AdaptiveEngineSimulator()
        engine.state = engine.state.copy(isEnabled = true, currentResolution = "360p", currentQuality = 30)

        // 14 ticks of great network -> should NOT upgrade yet
        var time = 10000L
        for (i in 1..14) {
            time += 2000L
            engine.tick(currentFps = 15, pingMs = 50, timeSinceLastFrame = 100, now = time, serverRes = "360p", serverQual = 30)
            assertEquals("360p", engine.state.currentResolution)
        }

        // 15th tick -> upgrades to 480p (only 1 step, NOT 720p)
        time += 2000L
        engine.tick(currentFps = 15, pingMs = 50, timeSinceLastFrame = 100, now = time, serverRes = "360p", serverQual = 30)
        assertEquals("480p", engine.state.currentResolution)
        assertEquals(30, engine.state.currentQuality)
        assertEquals(15, engine.state.targetFps)
    }

    @Test
    fun `flapping lockout triggers 2 min freeze when downgrade occurs shortly after upgrade`() {
        val engine = AdaptiveEngineSimulator()
        engine.state = engine.state.copy(isEnabled = true, currentResolution = "360p", currentQuality = 30)

        // Simulate successful upgrade at t = 30,000ms to 480p
        var time = 30_000L
        for (i in 1..15) {
            time += 2000L
            engine.tick(currentFps = 15, pingMs = 50, timeSinceLastFrame = 100, now = time, serverRes = "360p", serverQual = 30)
        }
        assertEquals("480p", engine.state.currentResolution)
        val upgradeTime = time

        // 10 seconds later, severe network lag hits (e.g. Ping 700ms)
        time += 4000L
        engine.tick(currentFps = 3, pingMs = 700, timeSinceLastFrame = 4000, now = time, serverRes = "480p", serverQual = 30)
        time += 2000L
        engine.tick(currentFps = 3, pingMs = 700, timeSinceLastFrame = 4000, now = time, serverRes = "480p", serverQual = 30)

        // Should downgrade back to 360p
        assertEquals("360p", engine.state.currentResolution)
        assertTrue(engine.state.isDowngraded)

        // Verify that flapping lockout is set to now + 120_000L (2 minutes)
        assertTrue(engine.flappingLockoutUntil > time + 100_000L)

        // Even with 20 consecutive stable checks during lockout, it MUST NOT upgrade!
        for (i in 1..20) {
            time += 2000L
            engine.tick(currentFps = 15, pingMs = 50, timeSinceLastFrame = 100, now = time, serverRes = "360p", serverQual = 20)
            assertEquals("360p", engine.state.currentResolution) // Locked!
        }
    }
}
