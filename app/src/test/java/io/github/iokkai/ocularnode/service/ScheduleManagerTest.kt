package io.github.iokkai.ocularnode.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ScheduleManagerTest {

    @Test
    fun isCurrentTimeInSchedule_withInvalidScheduleFormat_returnsFalse() {
        assertFalse(ScheduleManager.isCurrentTimeInSchedule("invalid", "06:00"))
        assertFalse(ScheduleManager.isCurrentTimeInSchedule("22:00", "invalid"))
        assertFalse(ScheduleManager.isCurrentTimeInSchedule("25:00", "06:00"))
        assertFalse(ScheduleManager.isCurrentTimeInSchedule("22:00", "06:70"))
        assertFalse(ScheduleManager.isCurrentTimeInSchedule("", ""))
    }

    @Test
    fun isCurrentTimeInSchedule_coveringWholeDay_returnsTrue() {
        // 00:00 to 23:59 covers entire day
        assertTrue(ScheduleManager.isCurrentTimeInSchedule("00:00", "23:59"))
    }

    @Test
    fun isCurrentTimeInSchedule_crossMidnight_correctlyMatchesNightTimes() {
        // Night schedule 22:00 to 06:00
        val cal2300 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 0)
        }
        val cal0300 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 3)
            set(Calendar.MINUTE, 30)
        }
        val cal1200 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
        }

        assertTrue(ScheduleManager.isCurrentTimeInSchedule("22:00", "06:00", cal2300))
        assertTrue(ScheduleManager.isCurrentTimeInSchedule("22:00", "06:00", cal0300))
        assertFalse(ScheduleManager.isCurrentTimeInSchedule("22:00", "06:00", cal1200))
    }

    @Test
    fun isCurrentTimeInSchedule_daytimeRange_correctlyMatchesDayTimes() {
        // Daytime schedule 09:00 to 18:00
        val cal1000 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
        }
        val cal2000 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
        }

        assertTrue(ScheduleManager.isCurrentTimeInSchedule("09:00", "18:00", cal1000))
        assertFalse(ScheduleManager.isCurrentTimeInSchedule("09:00", "18:00", cal2000))
    }
}

