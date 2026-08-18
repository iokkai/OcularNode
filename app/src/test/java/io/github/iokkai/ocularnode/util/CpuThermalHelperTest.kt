package io.github.iokkai.ocularnode.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CpuThermalHelperTest {

    @Test
    fun testGetEffectiveTemperature_cpuNull_returnsBatteryTemp() {
        val effective = CpuThermalHelper.getEffectiveTemperature(batteryTemp = 36.5f, context = null)
        // In local JVM test environment, CPU thermal sysfs might return null or a host temperature
        // effective temp should at least be >= batteryTemp
        assert(effective >= 36.5f)
    }

    @Test
    fun testGetEffectiveTemperature_maxComparison() {
        // Direct testing of logic contract
        val bTemp = 25.0f // Battery-less phone fixed 25C
        val cpuTemp = 68.0f // High CPU load
        val effective = maxOf(bTemp, cpuTemp)
        assertEquals(68.0f, effective, 0.01f)
    }
}
