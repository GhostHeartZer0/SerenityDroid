package com.example.serenitydroid.jni

import android.util.Log

object LlamaAndroid {
    private const val TAG = "LlamaAndroid"

    init {
        try {
            System.loadLibrary("serenity_llama")
            Log.i(TAG, "Successfully loaded libserenity_llama.so")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load libserenity_llama.so: ${e.message}")
        }
    }

    external fun loadModel(
        modelPath: String, 
        nGpuLayers: Int, 
        nContext: Int, 
        nThreads: Int, 
        mmprojPath: String? = null,
        kvQuantK: Int = 1,
        kvQuantV: Int = 1,
        backendHint: String? = null
    ): Int
    external fun unloadModel()
    external fun deinitBackend()
    external fun isModelLoaded(): Boolean
    external fun getModelContextLength(): Int
    external fun getBackendName(): String
    external fun completion_init(
        prompt: String,
        temp: Float = 0.7f,
        topK: Int = 40,
        topP: Float = 0.9f,
        minP: Float = 0.05f,
        penaltyRepeat: Float = 1.1f,
        penaltyFreq: Float = 0.0f,
        penaltyPresent: Float = 0.0f
    )
    external fun completion_loop(): String
    external fun autoSelectLevelForPrompt(prompt: String): Int
    external fun initWakeWord()
    external fun processAudioForWakeWord(audioData: ShortArray): Boolean
    external fun setThreadAffinity()
}
