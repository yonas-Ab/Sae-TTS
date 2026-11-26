package com.example.benchmark

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File

data class DeviceTelemetry(
    val deviceModel: String,
    val manufacturer: String,
    val androidVersion: String,
    val apiLevel: Int,
    val hardware: String,
    val supportedAbis: List<String>,
    val cpuCores: Int,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val lowMemoryFlag: Boolean,
    val processRssMb: Double?,
    val processHwmMb: Double?,
    val jvmHeapUsedMb: Double,
) {
    val isArm64Supported: Boolean
        get() = supportedAbis.any { it.equals("arm64-v8a", ignoreCase = true) }
}

class PerformanceMonitor(private val context: Context) {

    fun captureTelemetry(): DeviceTelemetry {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)

        val (rssMb, hwmMb) = readProcStatusMemoryMb()
        val rt = Runtime.getRuntime()
        val jvmUsedMb = (rt.totalMemory() - rt.freeMemory()) / (1024.0 * 1024.0)

        return DeviceTelemetry(
            deviceModel = Build.MODEL ?: "Unknown",
            manufacturer = Build.MANUFACTURER ?: "Unknown",
            androidVersion = Build.VERSION.RELEASE ?: "?",
            apiLevel = Build.VERSION.SDK_INT,
            hardware = Build.HARDWARE ?: Build.BOARD ?: "Unknown",
            supportedAbis = Build.SUPPORTED_ABIS?.toList().orEmpty(),
            cpuCores = rt.availableProcessors().coerceAtLeast(1),
            totalRamMb = memInfo.totalMem / (1024L * 1024L),
            availableRamMb = memInfo.availMem / (1024L * 1024L),
            lowMemoryFlag = memInfo.lowMemory,
            processRssMb = rssMb,
            processHwmMb = hwmMb,
            jvmHeapUsedMb = jvmUsedMb,
        )
    }

    fun readCurrentProcessRssMb(): Double? = readProcStatusMemoryMb().first

    private fun readProcStatusMemoryMb(): Pair<Double?, Double?> {
        return try {
            val statusFile = File("/proc/self/status")
            if (!statusFile.exists() || !statusFile.canRead()) return null to null
            var rssKb: Long? = null
            var hwmKb: Long? = null
            statusFile.forEachLine { line ->
                when {
                    line.startsWith("VmRSS:") -> {
                        rssKb = line.substringAfter(":").trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull()
                    }
                    line.startsWith("VmHWM:") -> {
                        hwmKb = line.substringAfter(":").trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull()
                    }
                }
            }
            val rssMb = rssKb?.let { it / 1024.0 }
            val hwmMb = hwmKb?.let { it / 1024.0 }
            rssMb to hwmMb
        } catch (_: Throwable) {
            null to null
        }
    }
}
