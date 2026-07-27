package com.example.serenitydroid.engine

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceHardwareInfo(
    val manufacturer: String,
    val model: String,
    val socModel: String,
    val totalRamGb: Float,
    val availableRamGb: Float,
    val isNpuSupported: Boolean,
    val npuVendor: String,
    val recommendedBackend: DeviceBackendMode
)

data class CompatibilityResult(
    val isCompatible: Boolean,
    val status: Status,
    val message: String,
    val requiredRamGb: Float
) {
    enum class Status {
        OPTIMAL,
        WARNING_HIGH_RAM,
        INCOMPATIBLE_NPU_MISSING,
        INCOMPATIBLE_RAM_EXCEEDED
    }
}

class DeviceDetector(
    private val context: Context
) {
    companion object {
        private const val TAG = "DeviceDetector"
    }

    fun getDeviceHardwareInfo(): DeviceHardwareInfo {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.ifBlank { Build.HARDWARE }
        } else {
            Build.HARDWARE
        }

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mi)
        val totalRamGb = (mi?.totalMem ?: 0L) / (1024f * 1024f * 1024f)
        val availableRamGb = (mi?.availMem ?: 0L) / (1024f * 1024f * 1024f)

        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val dispatchLib = File(nativeLibDir, "liblitert_dispatch.so").exists()
        val qnnLib = File(nativeLibDir, "libQnnHtp.so").exists()
        val isQualcommSoC = soc.contains("qcom", ignoreCase = true) || 
                            soc.contains("sm", ignoreCase = true) || 
                            Build.BOARD.contains("sm", ignoreCase = true) ||
                            Build.HARDWARE.contains("qcom", ignoreCase = true)

        val isNpuSupported = dispatchLib || (qnnLib && isQualcommSoC)
        val npuVendor = when {
            isQualcommSoC -> "Qualcomm® Hexagon™ NPU (HTP)"
            soc.contains("tensor", ignoreCase = true) -> "Google Tensor TPU/NPU"
            soc.contains("mt", ignoreCase = true) || soc.contains("dimensity", ignoreCase = true) -> "MediaTek APU/NPU"
            else -> "Generic Hardware NPU"
        }

        val recommendedBackend = when {
            isNpuSupported -> DeviceBackendMode.NPU
            totalRamGb >= 8f -> DeviceBackendMode.GPU
            else -> DeviceBackendMode.CPU
        }

        return DeviceHardwareInfo(
            manufacturer = manufacturer,
            model = model,
            socModel = soc.ifBlank { "Snapdragon / ARM64" },
            totalRamGb = totalRamGb,
            availableRamGb = availableRamGb,
            isNpuSupported = isNpuSupported,
            npuVendor = npuVendor,
            recommendedBackend = recommendedBackend
        )
    }

    fun checkCompatibility(
        fileSizeMb: Float,
        quantization: String,
        targetBackend: DeviceBackendMode
    ): CompatibilityResult {
        val info = getDeviceHardwareInfo()
        
        // Estimate RAM required: base model size + ~1.5x buffer for KV cache & runtime context
        val mult = when (quantization) {
            "INT4" -> 0.7f
            "INT8" -> 1.0f
            "FP16" -> 1.8f
            else -> 1.0f
        }
        val requiredRamGb = (fileSizeMb / 1024f) * mult + 1.2f

        if (targetBackend == DeviceBackendMode.NPU && !info.isNpuSupported) {
            return CompatibilityResult(
                isCompatible = false,
                status = CompatibilityResult.Status.INCOMPATIBLE_NPU_MISSING,
                message = "Selected target is NPU, but native NPU dispatch libraries (liblitert_dispatch.so / libQnnHtp.so) are missing on this device.",
                requiredRamGb = requiredRamGb
            )
        }

        if (requiredRamGb > info.availableRamGb) {
            return CompatibilityResult(
                isCompatible = false,
                status = CompatibilityResult.Status.INCOMPATIBLE_RAM_EXCEEDED,
                message = String.format(
                    "Model requires ~%.1f GB RAM, but only %.1f GB free RAM available on %s %s.",
                    requiredRamGb, info.availableRamGb, info.manufacturer, info.model
                ),
                requiredRamGb = requiredRamGb
            )
        }

        if (requiredRamGb > (info.totalRamGb * 0.75f)) {
            return CompatibilityResult(
                isCompatible = true,
                status = CompatibilityResult.Status.WARNING_HIGH_RAM,
                message = String.format(
                    "Model requires ~%.1f GB RAM. Will run on %s %s, but may trigger high memory pressure.",
                    requiredRamGb, info.manufacturer, info.model
                ),
                requiredRamGb = requiredRamGb
            )
        }

        return CompatibilityResult(
            isCompatible = true,
            status = CompatibilityResult.Status.OPTIMAL,
            message = String.format(
                "Optimal! Model requires ~%.1f GB RAM. Fully compatible with %s %s (%s).",
                requiredRamGb, info.manufacturer, info.model, info.npuVendor
            ),
            requiredRamGb = requiredRamGb
        )
    }
}
