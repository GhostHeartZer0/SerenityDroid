package com.example.serenityai.jni

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

    external fun loadModel(modelPath: String, nGpuLayers: Int, mmprojPath: String? = null): Int
    external fun unloadModel()
    external fun isModelLoaded(): Boolean
    external fun getModelContextLength(): Int
    external fun completion_init(prompt: String)
    external fun completion_loop(): String
    external fun autoSelectLevelForPrompt(prompt: String): Int
    external fun initWakeWord()
    external fun processAudioForWakeWord(audioData: ShortArray): Boolean
}
