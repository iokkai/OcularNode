package io.github.iokkai.ocularnode.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 測試溫控防護狀態機與電源斷電守護邏輯 (Thermal Throttling Hysteresis & Power Cut Sentinel)。
 */
class BatteryPowerMonitorTest {

    enum class PowerPlugStatus {
        CHARGING,
        DISCHARGING
    }

    class BatteryPowerSentinelSimulator(
        val highTempThreshold: Float = 45.0f,
        val recoveryTempThreshold: Float = 42.0f,
        val lowBatteryThreshold: Int = 60
    ) {
        var isOverheating = false
        var lastPowerStatus: PowerPlugStatus? = null
        var powerCutAlertTriggered = false
        var lowBatteryAlertTriggered = false

        fun onBatteryUpdate(tempCelsius: Float, powerStatus: PowerPlugStatus, batteryPct: Int): String {
            // 1. 溫控防護 (45°C 降級暫停 AI / 42°C 降溫恢復)
            if (!isOverheating && tempCelsius >= highTempThreshold) {
                isOverheating = true
            } else if (isOverheating && tempCelsius <= recoveryTempThreshold) {
                isOverheating = false
            }

            // 2. 斷電警報 (由 CHARGING 變為 DISCHARGING)
            if (lastPowerStatus == PowerPlugStatus.CHARGING && powerStatus == PowerPlugStatus.DISCHARGING) {
                powerCutAlertTriggered = true
            }
            lastPowerStatus = powerStatus

            // 3. 低電量警報
            if (powerStatus == PowerPlugStatus.DISCHARGING && batteryPct < lowBatteryThreshold) {
                lowBatteryAlertTriggered = true
            }

            return if (isOverheating) "THROTTLED" else "NORMAL"
        }
    }

    @Test
    fun `thermal throttle triggers at 45 degrees and only recovers when cooled to 42 degrees`() {
        val monitor = BatteryPowerSentinelSimulator()

        // Normal temperature: 38°C
        assertEquals("NORMAL", monitor.onBatteryUpdate(38f, PowerPlugStatus.CHARGING, 100))
        assertFalse(monitor.isOverheating)

        // Temperature rises to 44.9°C (just below 45°C) -> Still NORMAL
        assertEquals("NORMAL", monitor.onBatteryUpdate(44.9f, PowerPlugStatus.CHARGING, 95))
        assertFalse(monitor.isOverheating)

        // Temperature spikes to 45.1°C (>= 45°C) -> Overheat THROTTLED!
        assertEquals("THROTTLED", monitor.onBatteryUpdate(45.1f, PowerPlugStatus.CHARGING, 90))
        assertTrue(monitor.isOverheating)

        // Temperature drops slightly to 43.5°C (in hysteresis zone 42~45) -> MUST REMAIN THROTTLED!
        assertEquals("THROTTLED", monitor.onBatteryUpdate(43.5f, PowerPlugStatus.CHARGING, 85))
        assertTrue(monitor.isOverheating)

        // Temperature drops to 41.8°C (<= 42°C) -> Recovered to NORMAL!
        assertEquals("NORMAL", monitor.onBatteryUpdate(41.8f, PowerPlugStatus.CHARGING, 80))
        assertFalse(monitor.isOverheating)
    }

    @Test
    fun `power cut alert triggers when unplugged from charging to discharging`() {
        val monitor = BatteryPowerSentinelSimulator()

        // Initial charging state
        monitor.onBatteryUpdate(35f, PowerPlugStatus.CHARGING, 100)
        assertFalse(monitor.powerCutAlertTriggered)

        // Suddenly unplugged -> DISCHARGING
        monitor.onBatteryUpdate(35f, PowerPlugStatus.DISCHARGING, 99)
        assertTrue(monitor.powerCutAlertTriggered)
    }

    @Test
    fun `low battery alert triggers when discharging below threshold`() {
        val monitor = BatteryPowerSentinelSimulator(lowBatteryThreshold = 60)

        // Discharging at 70% -> no alert
        monitor.onBatteryUpdate(35f, PowerPlugStatus.DISCHARGING, 70)
        assertFalse(monitor.lowBatteryAlertTriggered)

        // Discharging at 58% (< 60%) -> Low battery alert!
        monitor.onBatteryUpdate(35f, PowerPlugStatus.DISCHARGING, 58)
        assertTrue(monitor.lowBatteryAlertTriggered)
    }
}
