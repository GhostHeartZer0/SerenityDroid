package com.example.serenitydroid.engine

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.serenityai.jni.LlamaAndroid
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

enum class DeviceBackendMode {
    AUTO,
    NPU,
    GPU,
    CPU
}

enum class EngineType {
    LITERT_LM,
    LLAMA_CPP,
    UNKNOWN
}

data class ModelConfig(
    val temperature: Float = 0.8f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val minP: Float = 0.05f,
    val frequencyPenalty: Float = 0.0f,
    val presencePenalty: Float = 0.0f,
    val enableMtp: Boolean = false,
    val enableSplitKv: Boolean = true,
    val enableAttentionCaching: Boolean = true,
    val sequenceBatchSize: Int = 1,
    val kvCacheSize: Int = 2048,
    val bufferSize: Int = 1024
)

class InferenceManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "InferenceManager"

        init {
            try {
                System.loadLibrary("litertlm_jni")
                Log.i(TAG, "Successfully loaded liblitertlm_jni.so")
            } catch (e: Throwable) {
                Log.w(TAG, "Native load litertlm_jni failed: ${e.message}")
            }
        }
    }

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var currentModelPath: String? = null
    private var activeEngineType: EngineType = EngineType.UNKNOWN
    
    var activeBackendName: String = "NOT LOADED"
        private set

    val isModelLoaded: Boolean
        get() = (activeEngineType == EngineType.LITERT_LM && engine != null && conversation != null) ||
                (activeEngineType == EngineType.LLAMA_CPP && LlamaAndroid.isModelLoaded())

    private fun isNpuSupported(): Boolean {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val dispatchLib = File(nativeLibDir, "liblitert_dispatch.so")
        return dispatchLib.exists()
    }

    fun isGguf(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        try {
            file.inputStream().use { 
                val buffer = ByteArray(4)
                if (it.read(buffer) == 4) {
                    return String(buffer) == "GGUF"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking magic number: ${e.message}")
        }
        return false
    }

    @OptIn(ExperimentalApi::class)
    suspend fun loadModel(
        modelPath: String, 
        backendMode: DeviceBackendMode = DeviceBackendMode.AUTO,
        config: ModelConfig = ModelConfig()
    ): Int = withContext(Dispatchers.IO) {
        unloadModelInternal()

        val modelFile = File(modelPath)
        if (!modelFile.exists() || !modelFile.canRead() || modelFile.length() == 0L) {
            Log.e(TAG, "Model file invalid or missing: $modelPath")
            activeBackendName = "INVALID FILE"
            return@withContext -1
        }

        currentModelPath = modelPath
        val actuallyGguf = isGguf(modelFile)
        
        Log.d(TAG, "Loading model: $modelPath (isGguf: $actuallyGguf, MTP: ${config.enableMtp})")

        // Enable Multi-Token Prediction (MTP) if requested
        ExperimentalFlags.enableSpeculativeDecoding = config.enableMtp

        return@withContext if (actuallyGguf) {
            loadLlamaModel(modelPath)
        } else {
            loadLiteRtModel(modelPath, backendMode, config)
        }
    }

    private fun loadLlamaModel(modelPath: String): Int {
        Log.d(TAG, "Loading model using llama.cpp engine: $modelPath")
        val res = LlamaAndroid.loadModel(modelPath, 35, null) 
        if (res == 0) {
            activeEngineType = EngineType.LLAMA_CPP
            activeBackendName = "llama.cpp (CPU/GPU)"
            Log.i(TAG, "Successfully loaded llama.cpp model")
            return 0
        } else {
            Log.e(TAG, "Failed to load llama.cpp model: $res")
            activeBackendName = "LLAMA_CPP FAILED"
            return -1
        }
    }

    private fun loadLiteRtModel(modelPath: String, backendMode: DeviceBackendMode, config: ModelConfig): Int {
        Log.d(TAG, "Loading model using LiteRT-LM engine: $modelPath")
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val npuSupported = isNpuSupported()

        Log.d(TAG, "NPU environment: nativeLibDir=$nativeLibDir, isNpuSupported=$npuSupported")
        
        // List all files to find the missing dispatch library
        File(nativeLibDir).listFiles()?.forEach { Log.v(TAG, "Available Lib: ${it.name}") }

        // Detailed check for QNN/LiteRT binaries
        val requiredLibs = listOf("liblitert_dispatch.so", "liblitert_lm_dispatch.so", "libQnnHtp.so", "libQnnSystem.so")
        requiredLibs.forEach { lib ->
            val file = File(nativeLibDir, lib)
            if (file.exists()) Log.i(TAG, "Found library: $lib (${file.length()} bytes)")
        }

        val backendsToTry = when {
            backendMode == DeviceBackendMode.NPU && npuSupported -> listOf(
                "NPU" to Backend.NPU(nativeLibraryDir = nativeLibDir),
                "GPU" to Backend.GPU(),
                "CPU" to Backend.CPU()
            )
            backendMode == DeviceBackendMode.GPU -> listOf(
                "GPU" to Backend.GPU()
            )
            backendMode == DeviceBackendMode.CPU -> listOf(
                "CPU" to Backend.CPU()
            )
            else -> {
                // TODO: Fix fallback behavior: if CPU selected, it shouldn't get GPU.
                // NPU takes priority in AUTO mode.
                val list = mutableListOf<Pair<String, Backend>>()
                if (npuSupported) {
                    list.add("NPU" to Backend.NPU(nativeLibraryDir = nativeLibDir))
                }
                list.add("GPU" to Backend.GPU())
                list.add("CPU" to Backend.CPU())
                list
            }
        }

        for ((name, backend) in backendsToTry) {
            try {
                if (name == "NPU") {
                    try {
                        System.loadLibrary("litert_dispatch")
                        System.loadLibrary("QnnHtp")
                        Log.i(TAG, "Successfully pre-loaded NPU dispatch/QNN libraries")
                    } catch (e: Throwable) {
                        Log.w(TAG, "NPU library pre-load failed: ${e.message}")
                    }
                }

                val engineConfig = EngineConfig(
                    modelPath = modelPath,
                    backend = backend,
                    cacheDir = context.cacheDir.path
                )
                
                // Debug: list all EngineConfig methods to find the right setter
                engineConfig::class.java.methods.forEach { 
                    if (it.name.contains("Lib", ignoreCase = true) || it.name.contains("Dir", ignoreCase = true)) {
                        Log.v(TAG, "Found potential EngineConfig setter: ${it.name}")
                    }
                }

                // Set advanced hardware flags and DispatchLibraryDir via reflection
                try {
                    val methods = engineConfig::class.java.methods
                    methods.find { it.name == "setSplitKvCache" }?.invoke(engineConfig, config.enableSplitKv)
                    methods.find { it.name == "setEnableAttentionCaching" }?.invoke(engineConfig, config.enableAttentionCaching)
                    methods.find { it.name == "setSequenceBatchSize" }?.invoke(engineConfig, config.sequenceBatchSize)
                    
                    // Attempt to set DispatchLibraryDir for NPU using multiple possible names
                    if (name == "NPU") {
                        val setterNames = listOf("setDispatchLibraryDir", "setLitertDispatchLibDir", "setNativeLibraryDir", "setLibDir")
                        for (sName in setterNames) {
                            val method = methods.find { it.name == sName }
                            if (method != null) {
                                try {
                                    method.invoke(engineConfig, nativeLibDir)
                                    Log.d(TAG, "Successfully applied $sName: $nativeLibDir")
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to apply $sName: ${e.message}")
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
                
                // Set advanced hardware flags via reflection
                try {
                    val methods = engineConfig::class.java.methods
                    methods.find { it.name == "setSplitKvCache" }?.invoke(engineConfig, config.enableSplitKv)
                    methods.find { it.name == "setEnableAttentionCaching" }?.invoke(engineConfig, config.enableAttentionCaching)
                    methods.find { it.name == "setSequenceBatchSize" }?.invoke(engineConfig, config.sequenceBatchSize)
                } catch (_: Exception) {}

                val newEngine = Engine(engineConfig)
                newEngine.initialize()

                val conversationConfig = ConversationConfig(
                    samplerConfig = SamplerConfig(
                        temperature = config.temperature.toDouble(),
                        topK = config.topK,
                        topP = config.topP.toDouble()
                    )
                )

                val newConversation = newEngine.createConversation(conversationConfig)

                engine = newEngine
                conversation = newConversation
                activeEngineType = EngineType.LITERT_LM
                activeBackendName = "LiteRT-LM ($name)"
                Log.i(TAG, "Successfully initialized LiteRT-LM Engine on $name")
                return 0
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to initialize LiteRT backend $name: ${e.message}")
            }
        }

        activeBackendName = "LITERT_LM FAILED"
        return -1
    }

    fun generateResponse(prompt: String, image: Bitmap? = null): Flow<String> = flow {
        if (!isModelLoaded) throw IllegalStateException("Model is not loaded.")

        if (activeEngineType == EngineType.LITERT_LM) {
            val activeConversation = conversation!!
            if (image != null) {
                try {
                    val addImageMethod = activeConversation::class.java.methods.find { it.name == "addImage" }
                    addImageMethod?.invoke(activeConversation, image)
                } catch (e: Exception) {
                    Log.e(TAG, "LiteRT image add failed", e)
                }
            }
            activeConversation.sendMessageAsync(prompt).collect { message ->
                emit(message.toString())
            }
        } else if (activeEngineType == EngineType.LLAMA_CPP) {
            LlamaAndroid.completion_init(prompt)
            while (true) {
                val token = LlamaAndroid.completion_loop()
                if (token == "[EOS]" || token.isEmpty()) break
                emit(token)
                delay(10)
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun runBenchmark(): String = withContext(Dispatchers.IO) {
        if (!isModelLoaded) return@withContext "Error: Model not loaded"
        
        val start = System.currentTimeMillis()
        var tokens = 0
        try {
            if (activeEngineType == EngineType.LITERT_LM) {
                val benchPrompt = "Why is the sky blue? Answer in 30 words."
                conversation!!.sendMessageAsync(benchPrompt).collect { 
                    tokens++
                }
            } else if (activeEngineType == EngineType.LLAMA_CPP) {
                LlamaAndroid.completion_init("Why is the sky blue?")
                repeat(30) {
                    val t = LlamaAndroid.completion_loop()
                    if (t == "[EOS]" || t.isEmpty()) return@repeat
                    tokens++
                }
            }
            val end = System.currentTimeMillis()
            val seconds = (end - start) / 1000f
            val tps = if (seconds > 0) tokens / seconds else 0f
            return@withContext String.format("%.2f tokens/sec", tps)
        } catch (e: Exception) {
            return@withContext "Error: ${e.message}"
        }
    }

    suspend fun unloadModel() = withContext(Dispatchers.IO) {
        unloadModelInternal()
    }

    private fun unloadModelInternal() {
        try {
            if (activeEngineType == EngineType.LITERT_LM) {
                conversation?.close()
                engine?.close()
            } else if (activeEngineType == EngineType.LLAMA_CPP) {
                LlamaAndroid.unloadModel()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            conversation = null
            engine = null
            activeEngineType = EngineType.UNKNOWN
            activeBackendName = "NOT LOADED"
        }
    }

    fun getModelVerboseDetails(): String {
        val path = currentModelPath ?: return "No model loaded."
        val file = File(path)
        if (!file.exists()) return "Model file missing: $path"
        val sizeMb = file.length() / (1024f * 1024f)
        return String.format(
            "File: %s\nEngine: %s\nSize: %.2f MB\nBackend: %s\nState: %s",
            file.name,
            activeEngineType.name,
            sizeMb,
            activeBackendName,
            if (isModelLoaded) "READY" else "NOT READY"
        )
    }
}
