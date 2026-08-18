package io.github.iokkai.ocularnode.util

import android.content.Context
import android.os.Build
import android.os.HardwarePropertiesManager
import android.util.Log
import java.io.File

/**
 * CPU / SoC 溫度讀取輔助類別 (CpuThermalHelper)
 * 提供在無電池直供電或常規設備上，透過 Linux sysfs 或 HardwarePropertiesManager 讀取真實 CPU 溫度的能力。
 */
object CpuThermalHelper {
    private const val TAG = "CpuThermalHelper"

    /**
     * 讀取 CPU / SoC 當前溫度 (攝氏度 °C)。
     * 若成功讀取回傳浮點數 (例如 58.5f)，若無權限或檔案不存在回傳 null。
     */
    fun getCpuTemperature(context: Context? = null): Float? {
        // 1. 優先嘗試 Android 原生 HardwarePropertiesManager (API 24+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && context != null) {
            try {
                val hpm = context.getSystemService(Context.HARDWARE_PROPERTIES_SERVICE) as? HardwarePropertiesManager
                val temps = hpm?.getDeviceTemperatures(
                    HardwarePropertiesManager.DEVICE_TEMPERATURE_CPU,
                    HardwarePropertiesManager.TEMPERATURE_CURRENT
                )
                if (temps != null && temps.isNotEmpty()) {
                    val validTemps = temps.filter { it in 15.0f..115.0f }
                    if (validTemps.isNotEmpty()) {
                        return validTemps.maxOrNull()
                    }
                }
            } catch (_: Exception) {
                // 權限不足或不支援時回退至 sysfs
            }
        }

        // 2. 回退方案：掃描 Linux 核心 thermal_zone 目錄
        val thermalDirs = listOf(
            "/sys/class/thermal",
            "/sys/devices/virtual/thermal"
        )

        for (baseDir in thermalDirs) {
            val dir = File(baseDir)
            if (!dir.exists() || !dir.isDirectory) continue

            val zones = dir.listFiles { f -> f.name.startsWith("thermal_zone") } ?: continue
            val foundTemps = mutableListOf<Float>()

            for (zone in zones) {
                try {
                    val typeFile = File(zone, "type")
                    val typeName = if (typeFile.exists() && typeFile.canRead()) typeFile.readText().trim().lowercase() else ""
                    val isCpuOrSoc = typeName.isEmpty() ||
                            typeName.contains("cpu") ||
                            typeName.contains("soc") ||
                            typeName.contains("ap") ||
                            typeName.contains("tsens") ||
                            typeName.contains("mtktscpu") ||
                            typeName.contains("cluster")

                    if (isCpuOrSoc) {
                        val tempFile = File(zone, "temp")
                        if (tempFile.exists() && tempFile.canRead()) {
                            val rawStr = tempFile.readText().trim()
                            val rawVal = rawStr.toFloatOrNull() ?: continue
                            val tempCelsius = if (rawVal > 1000) rawVal / 1000f else rawVal
                            if (tempCelsius in 15.0f..115.0f) {
                                foundTemps.add(tempCelsius)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            if (foundTemps.isNotEmpty()) {
                return foundTemps.maxOrNull()
            }
        }

        return null
    }

    /**
     * 計算綜合有效溫度 (Effective Temperature)
     * 取電池溫度與 CPU 溫度的最大值，確保無電池固定電阻設備依然享有熱防護。
     */
    fun getEffectiveTemperature(batteryTemp: Float, context: Context? = null): Float {
        val cpuTemp = getCpuTemperature(context)
        return if (cpuTemp != null) {
            maxOf(batteryTemp, cpuTemp)
        } else {
            batteryTemp
        }
    }
}
