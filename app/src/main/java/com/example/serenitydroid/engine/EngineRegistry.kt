package com.example.serenitydroid.engine

import android.content.Context
import android.system.Os
import android.util.Log
import java.io.File

object EngineRegistry {
    private const val TAG = "EngineRegistry"

    private var isInitialized = false
    var isNpuSupported: Boolean = false
        private set
    var hasGenieXCore: Boolean = false
        private set
    var hasLlamaPlugin: Boolean = false
        private set
    var hasQairtPlugin: Boolean = false
        private set
    var hasQnnHtp: Boolean = false
        private set
    var hasOpenMp: Boolean = false
        private set
    var cachedNativeLibDir: String = ""
        private set

    fun initializeOnce(context: Context) {
        if (isInitialized) return
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        cachedNativeLibDir = nativeLibDir
        val libDirFile = File(nativeLibDir)

        // Hint ADSP search path for Qualcomm HTP
        try {
            Os.setenv("ADSP_LIBRARY_PATH", "$nativeLibDir;/vendor/lib/rfsa/adsp;/system/lib/rfsa/adsp;/dsp", true)
            System.setProperty("ADSP_LIBRARY_PATH", nativeLibDir)
            Log.i(TAG, "ADSP_LIBRARY_PATH environment variable and property set to: $nativeLibDir")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set ADSP_LIBRARY_PATH in registry: ${e.message}")
        }

        hasGenieXCore = File(libDirFile, "libgeniex.so").exists()
        hasLlamaPlugin = File(libDirFile, "libgeniex_plugin_llama_cpp.so").exists()
        hasQairtPlugin = File(libDirFile, "libgeniex_plugin_qairt.so").exists()
        hasQnnHtp = File(libDirFile, "libQnnHtp.so").exists()
        hasOpenMp = File(libDirFile, "libomp.so").exists() || File(libDirFile, "libgomp.so").exists() || File(libDirFile, "libiomp5.so").exists()

        // NPU is supported if essential GenieX/QNN cores are present, OR if LiteRT NPU dispatch is present
        val hasLiteRtNpu = File(libDirFile, "liblitert_dispatch.so").exists()
        isNpuSupported = (hasGenieXCore && hasLlamaPlugin) || hasQnnHtp || hasLiteRtNpu
        
        isInitialized = true
        Log.i(TAG, "EngineRegistry initialized: NPU=$isNpuSupported (GenieCore=$hasGenieXCore, QNN=$hasQnnHtp, LiteRT_NPU=$hasLiteRtNpu)")
    }
}
