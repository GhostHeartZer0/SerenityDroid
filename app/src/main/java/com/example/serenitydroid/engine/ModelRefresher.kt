package com.example.serenitydroid.engine

import android.util.Log
import java.io.File
import com.example.serenitydroid.model.RuntimeOption

data class ModelFile(
    val name: String,
    val file: File,
    val type: EngineType
) {
    fun getTopCompatibleEngine(context: android.content.Context): String {
        val info = DeviceDetector(context).getDeviceHardwareInfo()
        val nameLower = name.lowercase()
        val isExplicitNpuModel = nameLower.contains("npu") || nameLower.contains("sm8") || nameLower.contains("sm7") || nameLower.contains("qnn")
        
        return when {
            type == EngineType.LITERT_LM && info.isNpuSupported && isExplicitNpuModel -> "NPU"
            type == EngineType.LITERT_LM -> "GPU"
            type == EngineType.LLAMA_CPP && info.isNpuSupported -> "NPU"
            type == EngineType.LLAMA_CPP -> if (info.totalRamGb < 4f) "CPU" else "GPU"
            type == EngineType.GENIE_X && info.isNpuSupported -> "NPU"
            info.isNpuSupported && isExplicitNpuModel -> "NPU"
            info.totalRamGb >= 4f -> "GPU"
            else -> "CPU"
        }
    }
}

/**
 * Utility component that scans and refreshes available AI model files from a specified directory.
 */
class ModelRefresher {

    companion object {
        private const val TAG = "ModelRefresher"
        val DEFAULT_EXTENSIONS = setOf("litertlm", "bin", "gguf", "tflite", "task", "onnx", "ort", "dlc", "so")

        fun extensionsForRuntimes(runtimes: Set<RuntimeOption>): Set<String> {
            if (runtimes.isEmpty()) return DEFAULT_EXTENSIONS
            return runtimes.flatMap { it.extensions }.toSet()
        }
    }

    /**
     * Scans [folder] recursively and returns all matching model [File] objects.
     */
    fun refreshModels(
        folder: File,
        supportedExtensions: Set<String> = DEFAULT_EXTENSIONS
    ): List<ModelFile> {
        Log.d(TAG, "Scanning folder: ${folder.absolutePath} for extensions: $supportedExtensions")
        if (!folder.exists()) {
            Log.w(TAG, "Folder does not exist: ${folder.absolutePath}")
            return emptyList()
        }
        if (!folder.isDirectory) {
            Log.w(TAG, "Path is not a directory: ${folder.absolutePath}")
            return emptyList()
        }
        val files = folder.walkTopDown()
            .filter { file ->
                val nameLower = file.name.lowercase()
                val isSupported = supportedExtensions.contains(file.extension.lowercase())
                val isAuxiliary = nameLower.contains("mmproj") || 
                                 nameLower.contains("mtp") || 
                                 nameLower.contains("assistant")
                
                file.isFile && isSupported && !isAuxiliary
            }
            .map { file ->
                var type = when (file.extension.lowercase()) {
                    "litertlm" -> EngineType.LITERT_LM
                    "gguf" -> EngineType.LLAMA_CPP
                    else -> EngineType.UNKNOWN
                }
                
                // Double check magic number for registered/converted files
                if (type == EngineType.LITERT_LM || type == EngineType.UNKNOWN) {
                    if (isGguf(file)) {
                        type = EngineType.LLAMA_CPP
                    }
                }

                ModelFile(file.name, file, type)
            }
            .toList()
        Log.i(TAG, "Catalog Scan Complete: Found ${files.size} models in ${folder.absolutePath}")
        files.forEach { Log.v(TAG, "  -> ${it.name} (${it.type})") }
        return files
    }

    fun refreshAuxiliaryFiles(folder: File): List<File> {
        if (!folder.exists() || !folder.isDirectory) return emptyList()
        return folder.walkTopDown()
            .filter { file ->
                val nameLower = file.name.lowercase()
                file.isFile && (nameLower.contains("mmproj") || nameLower.contains("mtp"))
            }
            .toList()
    }

    private fun isGguf(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        try {
            file.inputStream().use { 
                val buffer = ByteArray(4)
                if (it.read(buffer) == 4) {
                    return String(buffer) == "GGUF"
                }
            }
        } catch (_: Exception) {
            // Ignore
        }
        return false
    }

    /**
     * Scans [folder] recursively and returns relative path strings from [folder].
     */
    fun refreshModelRelativePaths(
        folder: File,
        supportedExtensions: Set<String> = DEFAULT_EXTENSIONS
    ): List<String> {
        return refreshModels(folder, supportedExtensions)
            .map { it.file.relativeTo(folder).path }
    }
}
