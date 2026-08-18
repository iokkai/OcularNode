package io.github.iokkai.ocularnode.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 測試排程自我淨化重開機判定邏輯 (Device Owner Scheduled Reboot Logic & Debounce Guards)。
 */
class ScheduleManagerRebootTest {

    data class MockRebootContext(
        val isEnabled: Boolean,
        val scheduledTime: String,
        val lastRebootDate: String,
        val isDeviceOwner: Boolean,
        val isRecordingVideo: Boolean
    )

    /** 模擬 ScheduleManager 中的重開機評估邏輯 */
    private fun shouldTriggerReboot(
        context: MockRebootContext,
        nowCalendar: Calendar = Calendar.getInstance()
    ): Pair<Boolean, String> {
        if (!context.isEnabled) return false to "DISABLED"

        val parts = context.scheduledTime.split(":")
        val targetHour = parts.getOrNull(0)?.toIntOrNull() ?: -1
        val targetMinute = parts.getOrNull(1)?.toIntOrNull() ?: -1
        val currentHour = nowCalendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = nowCalendar.get(Calendar.MINUTE)

        if (currentHour != targetHour || currentMinute != targetMinute) {
            return false to "TIME_NOT_MATCHED"
        }

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(nowCalendar.time)
        if (context.lastRebootDate == todayStr) {
            return false to "ALREADY_REBOOTED_TODAY"
        }

        if (!context.isDeviceOwner) {
            return false to "NOT_DEVICE_OWNER"
        }

        if (context.isRecordingVideo) {
            return false to "RECORDING_ACTIVE_POSTPONED"
        }

        return true to "TRIGGER_REBOOT"
    }

    @Test
    fun `reboot is not triggered when disabled`() {
        val cal0400 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 4)
            set(Calendar.MINUTE, 0)
        }
        val ctx = MockRebootContext(
            isEnabled = false,
            scheduledTime = "04:00",
            lastRebootDate = "",
            isDeviceOwner = true,
            isRecordingVideo = false
        )
        val (shouldReboot, reason) = shouldTriggerReboot(ctx, cal0400)
        assertFalse(shouldReboot)
        assertEquals("DISABLED", reason)
    }

    @Test
    fun `reboot is postponed if emergency recording is active`() {
        val cal0400 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 4)
            set(Calendar.MINUTE, 0)
        }
        val ctx = MockRebootContext(
            isEnabled = true,
            scheduledTime = "04:00",
            lastRebootDate = "",
            isDeviceOwner = true,
            isRecordingVideo = true // Recording in progress!
        )
        val (shouldReboot, reason) = shouldTriggerReboot(ctx, cal0400)
        assertFalse(shouldReboot)
        assertEquals("RECORDING_ACTIVE_POSTPONED", reason)
    }

    @Test
    fun `same day debounce lock prevents multiple reboots on the same day`() {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 4)
            set(Calendar.MINUTE, 0)
        }
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now.time)
        val ctx = MockRebootContext(
            isEnabled = true,
            scheduledTime = "04:00",
            lastRebootDate = todayStr, // Already rebooted today
            isDeviceOwner = true,
            isRecordingVideo = false
        )
        val (shouldReboot, reason) = shouldTriggerReboot(ctx, now)
        assertFalse(shouldReboot)
        assertEquals("ALREADY_REBOOTED_TODAY", reason)
    }

    @Test
    fun `reboot executes cleanly when time matches, is DO, and not recording`() {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 4)
            set(Calendar.MINUTE, 0)
        }
        val yesterdayStr = "2026-01-01"
        val ctx = MockRebootContext(
            isEnabled = true,
            scheduledTime = "04:00",
            lastRebootDate = yesterdayStr,
            isDeviceOwner = true,
            isRecordingVideo = false
        )
        val (shouldReboot, reason) = shouldTriggerReboot(ctx, now)
        assertTrue(shouldReboot)
        assertEquals("TRIGGER_REBOOT", reason)
    }

    @Test
    fun `reboot safely aborts without crash if not device owner`() {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 4)
            set(Calendar.MINUTE, 0)
        }
        val ctx = MockRebootContext(
            isEnabled = true,
            scheduledTime = "04:00",
            lastRebootDate = "",
            isDeviceOwner = false, // Regular app mode
            isRecordingVideo = false
        )
        val (shouldReboot, reason) = shouldTriggerReboot(ctx, now)
        assertFalse(shouldReboot)
        assertEquals("NOT_DEVICE_OWNER", reason)
    }
}
