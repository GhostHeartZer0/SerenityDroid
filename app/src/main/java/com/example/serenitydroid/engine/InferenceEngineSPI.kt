package com.example.serenitydroid.engine

import com.example.serenitydroid.engine.DeviceBackendMode

interface InferenceEngineSPI {
    val engineType: EngineType
    
    suspend fun loadModel(
        modelPath: String,
        backendMode: DeviceBackendMode,
        config: ModelConfig
    ): Int

    suspend fun completionInit(prompt: String): Boolean
    suspend fun completionNextToken(): String?
    fun unloadModel()
    fun isModelLoaded(): Boolean
    fun getBackendName(): String
}
