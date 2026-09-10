package com.example.serenitydroid.engine

import android.content.Context
import android.graphics.Bitmap
import android.system.Os
import android.util.Log
import com.example.serenitydroid.jni.LlamaAndroid
import com.example.serenitydroid.model.ChatMessage
import com.example.serenitydroid.model.SenderType
import com.geniex.sdk.GenieXSdk
import com.geniex.sdk.LlmWrapper
import com.geniex.sdk.bean.ComputeUnitValue
import com.geniex.sdk.bean.LlmCreateInput
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import com.geniex.sdk.bean.LlmStreamResult as GenieStreamResult
import com.geniex.sdk.bean.ModelConfig as GenieModelConfig

enum class KvQuant(val value: Int) {
    F16(1),
    Q8_0(8),
    Q4_0(2),
    Q4_1(3),
    Q5_0(6),
    Q5_1(7)
}

enum class DeviceBackendMode {
    AUTO,
    NPU,
    GPU,
    CPU
}

enum class EngineSelection {
    AUTO,
    LLAMA_CPP,
    GENIE_X,
    LITERT_LM
}

enum class EngineType {
    LITERT_LM,
    LLAMA_CPP,
    GENIE_X,
    UNKNOWN
}

data class ModelConfig(
    val temperature: Float = 0.8f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val minP: Float = 0.05f,
    val frequencyPenalty: Float = 0.0f,
    val presencePenalty: Float = 0.0f,
    val maxTokens: Int = 1024,
    val enableMtp: Boolean = false,
    val enableSplitKv: Boolean = true,
    val enableAttentionCaching: Boolean = true,
    val enableFlashAttn: Boolean = true,
    val sequenceBatchSize: Int = 1,
    val kvCacheSize: Int = 4096,
    val bufferSize: Int = 1024,
    val waitOnFailure: Boolean = true,
    val retryDelayMs: Long = 1000L,
    val kvQuantK: KvQuant = KvQuant.F16,
    val kvQuantV: KvQuant = KvQuant.F16,
    val preferredEngine: EngineSelection = EngineSelection.AUTO
)

class InferenceManager(
    private val context: Context
) {
    init {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        try {
            // CRITICAL: Set ADSP_LIBRARY_PATH as an environment variable for FastRPC / DSP discovery
            Os.setenv("ADSP_LIBRARY_PATH", "$nativeLibDir;/vendor/lib/rfsa/adsp;/system/lib/rfsa/adsp;/dsp", true)
            Log.i(TAG, "Successfully set native ADSP_LIBRARY_PATH to $nativeLibDir")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set ADSP_LIBRARY_PATH: ${e.message}")
        }
        EngineRegistry.initializeOnce(context)
    }

    companion object {
        private const val TAG = "InferenceManager"

        // Logcat noise reduction: Filter command suggestion for adb
        // adb logcat -s InferenceManager:V GenieXSdk:V LlamaNative:V *:S
        const val LOGCAT_RECOMMENDED_FILTER = "adb logcat -s InferenceManager:V GenieXSdk:V LlamaNative:V *:S"

        private val CONTROL_TAG_REGEX = Regex(
            "(?i)</?(?:start_of_turn|end_of_turn|user_turn|model_turn|system_turn|bos|eos|eot_id|eom_id|start_header_id|end_header_id|im_start|im_end|pause|tool|tool_response|turn)(?:\\s*|:)?(?:user|model|system|assistant|tool)?/?>?|</s>|<s>",
            RegexOption.IGNORE_CASE
        )

        private val STOP_TAG_REGEX = Regex(
            "(?i)<end_of_turn>|<start_of_turn>|start_of_turn|<turn\\||</s>|<\\|eot_id\\|>|<\\|eom_id\\|>|<\\|im_end\\|>|<\\|start_header_id\\|>|<model>|<user>|<assistant>|user_turn|model_turn",
            RegexOption.IGNORE_CASE
        )

        private val TRAILING_PARTIAL_TAG_REGEX = Regex("<[/a-zA-Z0-9_\\-|]*$")

        fun stripControlTags(text: String): String {
            val stripped = CONTROL_TAG_REGEX.replace(text, "")
            return stripped
                .replace(Regex("(?i)^(?:user|model|system|assistant)\\s*\n"), "")
                .replace(Regex("(?i)\n(?:user|model|system|assistant)\\s*$"), "")
        }




        private var librariesLoaded = false

        fun ensureLibrariesLoaded() {
            if (librariesLoaded) return
            synchronized(this) {
                if (librariesLoaded) return
                Log.w(TAG, "==== SERENITY INFERENCE MANAGER PRE-LOADING RUNTIMES ====")
                Log.i(TAG, "Logcat filter command: $LOGCAT_RECOMMENDED_FILTER")
                listOf(
                    "omp",
                    "c++_shared", 
                    "ggml-base", 
                    "ggml", 
                    "llama", 
                    "serenity_llama",
                    "QnnHtp", 
                    "QnnSystem",
                    "LiteRt", 
                    "litert_jni", 
                    "litert_dispatch", 
                    "litert_npu_compiler",
                    "litertlm_jni", 
                    "geniex_core",
                    "geniex-proc",
                    "geniex_vlm",
                    "geniex",
                    "geniex_plugin_llama_cpp",
                    "geniex_plugin_qairt"
                ).forEach { lib ->
                    try {
                        System.loadLibrary(lib)
                        Log.i(TAG, "Successfully pre-loaded lib$lib.so")
                    } catch (e: Throwable) {
                        Log.v(TAG, "Pre-load lib$lib.so note: ${e.message}")
                    }
                }
                librariesLoaded = true
            }
        }
    }

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var llmWrapper: LlmWrapper? = null
    private var currentModelPath: String? = null
    private var currentSystemPrompt: String? = null
    private var activeEngineType: EngineType = EngineType.UNKNOWN
    private var currentConfig: ModelConfig = ModelConfig()
    
    var activeBackendName: String = "NOT LOADED"
        private set

    val isModelLoaded: Boolean
        get() = ((activeEngineType == EngineType.LITERT_LM) && engine != null && conversation != null) ||
                ((activeEngineType == EngineType.LLAMA_CPP) && LlamaAndroid.isModelLoaded()) ||
                ((activeEngineType == EngineType.GENIE_X) && llmWrapper != null)

    private fun isNpuSupported(): Boolean {
        return EngineRegistry.isNpuSupported
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
        config: ModelConfig = ModelConfig(),
        systemPrompt: String? = null
    ): Int = withContext(Dispatchers.IO) {
        ensureLibrariesLoaded()
        unloadModelInternal()

        val modelFile = File(modelPath)
        if (!modelFile.exists() || !modelFile.canRead() || modelFile.length() == 0L) {
            Log.e(TAG, "Model file invalid or missing: $modelPath")
            activeBackendName = "INVALID FILE"
            return@withContext -1
        }

        currentModelPath = modelPath
        currentConfig = config
        currentSystemPrompt = systemPrompt
        val ext = modelFile.extension.lowercase()
        val actuallyGguf = ext == "gguf" || isGguf(modelFile)
        val isLiteRtFormat = ext == "litertlm" || ext == "tflite"
        
        Log.d(TAG, "Loading model: $modelPath (ext: $ext, isGguf: $actuallyGguf, isLiteRt: $isLiteRtFormat, preferredEngine: ${config.preferredEngine})")

        // Enable Multi-Token Prediction (MTP) and Benchmarking if requested
        ExperimentalFlags.enableSpeculativeDecoding = config.enableMtp
        ExperimentalFlags.enableBenchmark = true

        // Strict Engine Routing
        return@withContext when {
            isLiteRtFormat -> {
                Log.i(TAG, "Smart Select: .litertlm / .tflite format detected ($ext). Routing to LiteRT-LM.")
                loadLiteRtModel(modelPath, backendMode, config)
            }
            config.preferredEngine == EngineSelection.LLAMA_CPP -> {
                Log.i(TAG, "Engine Override: LLAMA_CPP explicitly requested (Backend: $backendMode).")
                when (backendMode) {
                    DeviceBackendMode.NPU -> {
                        Log.i(TAG, "NPU requested for LLAMA_CPP: Routing to Genie X SDK (Qualcomm llama.cpp bridge)...")
                        val res = loadGenieModel(modelPath, DeviceBackendMode.NPU, config)
                        if (res == 0) 0 else {
                            val gpuRes = loadLlamaModel(modelPath, DeviceBackendMode.GPU, config)
                            if (gpuRes == 0) 0 else loadLlamaModel(modelPath, DeviceBackendMode.CPU, config)
                        }
                    }
                    DeviceBackendMode.GPU -> {
                        loadLlamaModel(modelPath, DeviceBackendMode.GPU, config)
                    }
                    DeviceBackendMode.CPU -> {
                        loadLlamaModel(modelPath, DeviceBackendMode.CPU, config)
                    }
                    DeviceBackendMode.AUTO -> {
                        Log.i(TAG, "AUTO backend requested for LLAMA_CPP: Priority Genie X NPU -> llama.cpp HTP -> OpenCL -> CPU")
                        
                        // Try Genie X NPU first as it's the most likely way to get GGUF on NPU on Qualcomm
                        val genieRes = loadGenieModel(modelPath, DeviceBackendMode.NPU, config)
                        if (genieRes == 0) return@withContext 0
                        
                        // Fallback to native llama.cpp sequence
                        val nRes = loadLlamaModel(modelPath, DeviceBackendMode.NPU, config)
                        if (nRes == 0) 0 else {
                            val gpuRes = loadLlamaModel(modelPath, DeviceBackendMode.GPU, config)
                            if (gpuRes == 0) 0 else loadLlamaModel(modelPath, DeviceBackendMode.CPU, config)
                        }
                    }
                }
            }
            config.preferredEngine == EngineSelection.GENIE_X -> {
                Log.i(TAG, "Engine Override: GENIE_X explicitly requested.")
                loadGenieModel(modelPath, backendMode, config)
            }
            config.preferredEngine == EngineSelection.LITERT_LM -> {
                Log.i(TAG, "Engine Override: LITERT_LM explicitly requested.")
                loadLiteRtModel(modelPath, backendMode, config)
            }
                    actuallyGguf -> {
                Log.i(TAG, "Smart Select: GGUF format detected (backendMode=$backendMode, preferredEngine=${config.preferredEngine}).")
                when (backendMode) {
                    DeviceBackendMode.NPU -> {
                        Log.d(TAG, "Targeting NPU via Genie X SDK or llama.cpp HTP...")
                        val genieRes = loadGenieModel(modelPath, DeviceBackendMode.NPU, config)
                        if (genieRes == 0) 0 else {
                            val llamaHtpRes = loadLlamaModel(modelPath, DeviceBackendMode.NPU, config)
                            if (llamaHtpRes == 0) 0 else loadLlamaModel(modelPath, DeviceBackendMode.CPU, config)
                        }
                    }
                    DeviceBackendMode.GPU -> {
                        Log.d(TAG, "Targeting GPU via Genie X GPU or llama.cpp OpenCL...")
                        val genieGpuRes = loadGenieModel(modelPath, DeviceBackendMode.GPU, config)
                        if (genieGpuRes == 0) 0 else {
                            val gpuRes = loadLlamaModel(modelPath, DeviceBackendMode.GPU, config)
                            if (gpuRes == 0) 0 else loadLlamaModel(modelPath, DeviceBackendMode.CPU, config)
                        }
                    }
                    DeviceBackendMode.CPU -> {
                        loadLlamaModel(modelPath, DeviceBackendMode.CPU, config)
                    }
                    DeviceBackendMode.AUTO -> {
                        Log.d(TAG, "Attempting Priority 1: NPU via Genie X SDK...")
                        val genieRes = loadGenieModel(modelPath, DeviceBackendMode.NPU, config)
                        if (genieRes == 0) 0 else {
                            Log.i(TAG, "Genie X NPU unavailable/failed, trying Priority 2: native llama.cpp HTP (NPU)...")
                            val llamaHtpRes = loadLlamaModel(modelPath, DeviceBackendMode.NPU, config)
                            if (llamaHtpRes == 0) 0 else {
                                Log.i(TAG, "llama.cpp HTP unavailable/failed, trying Priority 3: Genie X GPU / llama.cpp OpenCL...")
                                val genieGpuRes = loadGenieModel(modelPath, DeviceBackendMode.GPU, config)
                                if (genieGpuRes == 0) 0 else {
                                    val gpuRes = loadLlamaModel(modelPath, DeviceBackendMode.GPU, config)
                                    if (gpuRes == 0) 0 else {
                                        Log.i(TAG, "GPU accelerators unavailable, falling back to Priority 4: CPU via llama.cpp...")
                                        loadLlamaModel(modelPath, DeviceBackendMode.CPU, config)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                // Unknown format, try available engines with strict fallback: NPU -> CPU
                Log.i(TAG, "Smart Select: Unknown format. Priority: LiteRT (NPU) -> Genie (NPU) -> CPU.")
                
                // LiteRT-LM (NPU or CPU)
                val litertRes = loadLiteRtModel(modelPath, backendMode, config)
                if (litertRes == 0) return@withContext 0
                
                // Genie X (NPU only in AUTO mode)
                val genieRes = loadGenieModel(modelPath, if (backendMode == DeviceBackendMode.AUTO) DeviceBackendMode.NPU else backendMode, config)
                if (genieRes == 0) return@withContext 0
                
                // Absolute Last Resort: CPU
                if (backendMode == DeviceBackendMode.AUTO || backendMode == DeviceBackendMode.CPU) {
                    Log.w(TAG, "SEVERE COMPATIBILITY FALLBACK: All accelerators failed, switching to llama.cpp (CPU)")
                    loadLlamaModel(modelPath, DeviceBackendMode.CPU, config)
                } else {
                    -1
                }
            }
        }
    }

    private suspend fun loadGenieModel(
        modelPath: String,
        backendMode: DeviceBackendMode = DeviceBackendMode.AUTO,
        config: ModelConfig = ModelConfig()
    ): Int {
        Log.d(TAG, "Loading model via Qualcomm Genie X: $modelPath (Mode: $backendMode)")
        activeEngineType = EngineType.GENIE_X
        try {
            val sdk = GenieXSdk.getInstance()
            
            val initResult = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                sdk.init(context, object : GenieXSdk.InitCallback {
                    override fun onSuccess() {
                        Log.i(TAG, "GenieX SDK initialized successfully")
                        continuation.resume(true)
                    }
                    override fun onFailure(reason: String) {
                        Log.e(TAG, "GenieX SDK initialization failed: $reason")
                        continuation.resume(false)
                    }
                })
            }

            if (!initResult) {
                activeBackendName = "GENIE_X INIT FAILED"
                return -1
            }

            val backendsToTry = when (backendMode) {
                DeviceBackendMode.NPU -> listOf(ComputeUnitValue.NPU)
                DeviceBackendMode.GPU -> listOf(ComputeUnitValue.GPU)
                DeviceBackendMode.CPU -> listOf(ComputeUnitValue.CPU)
                DeviceBackendMode.AUTO -> listOf(ComputeUnitValue.NPU, ComputeUnitValue.GPU, ComputeUnitValue.CPU)
            }

            val modelName = File(modelPath).nameWithoutExtension
            val ext = File(modelPath).extension.lowercase()
            val isQairt = ext == "bin" || ext == "dlc" || ext == "zip"
            val pluginId = if (isQairt) {
                try { GenieXSdk.PLUGIN_ID_QAIRT } catch (_: Throwable) { "qairt" }
            } else {
                GenieXSdk.PLUGIN_ID_LLAMA_CPP
            }

            val modelDir = File(modelPath).parentFile
            val tokenizerFile = File(modelDir, "tokenizer.model").takeIf { it.exists() }
                ?: File(modelDir, "tokenizer.json").takeIf { it.exists() }
            val tokenizerPath = tokenizerFile?.absolutePath ?: modelPath
            Log.i(TAG, "Genie X tokenizer path set to: $tokenizerPath, pluginId=$pluginId, isQairt=$isQairt")

            for (computeUnit in backendsToTry) {
                Log.d(TAG, "Attempting Genie X build on ${computeUnit.name} (Code: ${computeUnit.value}) using pluginId=$pluginId")
                
                // QAIRT bundles require nCtx=0 and nGpuLayers=0 (fixed at compile time in AI Hub bundle)
                val layers = if (computeUnit == ComputeUnitValue.CPU || isQairt) 0 else {
                    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                    val memInfo = android.app.ActivityManager.MemoryInfo()
                    am?.getMemoryInfo(memInfo)
                    val availGb = memInfo.availMem / (1024.0 * 1024.0 * 1024.0)
                    when {
                        availGb >= 6.0 -> 999
                        availGb >= 4.0 -> 32
                        else -> 16
                    }
                }
                val nCtxVal = if (isQairt) 0 else config.kvCacheSize
                Log.d(TAG, "Genie X config: dynamic layers=$layers, nCtx=$nCtxVal")
                
                val genieConfig = GenieModelConfig(
                    nCtx = nCtxVal, 
                    nGpuLayers = layers,
                    nThreads = 0 // Auto
                )

                // Discovery and configuration
                try {
                    val genieMethods = genieConfig::class.java.methods
                    
                    // Log all methods for diagnostic
                    genieMethods.forEach { Log.v(TAG, "GenieConfig Method: ${it.name}") }

                    genieMethods.find { it.name == "setLogLevel" }?.invoke(genieConfig, 3)
                    
                    // Common names for mmap/flash attention in various SDK versions
                    val mmapSetter = genieMethods.find { it.name.lowercase().contains("mmap") && it.parameterTypes.size == 1 }
                    val faSetter = genieMethods.find { it.name.lowercase().contains("flash") && it.parameterTypes.size == 1 }
                    
                    mmapSetter?.invoke(genieConfig, true)
                    faSetter?.invoke(genieConfig, true)
                    
                    Log.i(TAG, "Genie X: Applied mmap=${mmapSetter?.name ?: "NOT FOUND"}, flash_attn=${faSetter?.name ?: "NOT FOUND"}")
                } catch (e: Exception) {
                    Log.w(TAG, "GenieConfig discovery failed: ${e.message}")
                }

                val input = LlmCreateInput(
                    model_name = modelName,
                    model_path = modelPath,
                    tokenizer_path = tokenizerPath, 
                    config = genieConfig,
                    runtime_id = pluginId,
                    compute_unit = computeUnit.value
                )

                val result = LlmWrapper.builder().llmCreateInput(input).build()
                
                if (result.isSuccess) {
                    this.llmWrapper = result.getOrNull()
                    activeEngineType = EngineType.GENIE_X
                    activeBackendName = "Genie X (${computeUnit.name} / $pluginId)"
                    Log.i(TAG, "Successfully initialized Genie X LlmWrapper for $modelName on ${computeUnit.name} ($pluginId)")
                    
                    // Small delay to allow native background settle (avoid race on first inference)
                    delay(300)
                    return 0
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Unknown error"
                    Log.w(TAG, "Genie X build failed on ${computeUnit.name}: $err")
                }
            }

            activeBackendName = "GENIE_X ALL BACKENDS FAILED"
            return -1
        } catch (e: Exception) {
            Log.e(TAG, "Genie X loading failed: ${e.message}", e)
            activeBackendName = "GENIE_X EXCEPTION"
            return -1
        }
    }

    private fun loadLlamaModel(modelPath: String, backendMode: DeviceBackendMode, config: ModelConfig): Int {
        Log.d(TAG, "Loading model using llama.cpp engine: $modelPath (Backend: $backendMode)")
        activeEngineType = EngineType.LLAMA_CPP
        
        // Leave at least one core for the UI thread and other background tasks
        val threadCount = maxOf(1, minOf(3, Runtime.getRuntime().availableProcessors() - 1))
        
        val hintsToTry = when (backendMode) {
            DeviceBackendMode.NPU -> listOf("HTP", "CPU")
            DeviceBackendMode.GPU -> listOf("OpenCL", "CPU") // Temporarily disabled Vulkan until SPIRV-Headers are bundled
            DeviceBackendMode.CPU -> listOf("CPU")
            DeviceBackendMode.AUTO -> listOf("HTP", "OpenCL", "CPU")
        }

        var res = -1
        for (backendHint in hintsToTry) {
            val requestedLayers = if (backendHint == "CPU") 0 else 999
            Log.d(TAG, "Attempting llama.cpp load with backendHint=$backendHint, requestedLayers=$requestedLayers")
            res = LlamaAndroid.loadModel(
                modelPath, 
                requestedLayers, 
                config.kvCacheSize, 
                threadCount, 
                null,
                config.kvQuantK.value,
                config.kvQuantV.value,
                if (config.enableFlashAttn) 1 else 0,
                backendHint
            )
            if (res == 0) break
        }

        if (res == 0) {
            activeEngineType = EngineType.LLAMA_CPP
            activeBackendName = LlamaAndroid.getBackendName()
            if (backendMode == DeviceBackendMode.CPU || (backendMode == DeviceBackendMode.AUTO && activeBackendName.contains("CPU"))) {
                Log.w(TAG, "Running in SEVERE COMPATIBILITY MODE: CPU only. Performance will be limited.")
            }
            Log.i(TAG, "Successfully loaded llama.cpp model ($activeBackendName)")
            return 0
        } else {
            Log.e(TAG, "Failed to load llama.cpp model: $res")
            activeBackendName = "LLAMA_CPP FAILED ($res)"
            return -1
        }
    }

    @OptIn(ExperimentalApi::class)
    private fun createLiteRtConversation(engine: Engine, config: ModelConfig, systemPrompt: String?): Conversation {
        val conversationConfig = ConversationConfig(
            systemInstruction = systemPrompt?.let { Contents.of(it) },
            samplerConfig = SamplerConfig(
                temperature = config.temperature.toDouble(),
                topK = config.topK,
                topP = config.topP.toDouble()
            )
        )

        // Try to enable thinking mode via reflection if available in this build
        try {
            val thinkingConfigClass = Class.forName("com.google.ai.edge.litertlm.ThinkingConfig")
            val thinkingConfig = thinkingConfigClass.getConstructor(Boolean::class.java, Int::class.java)
                .newInstance(true, -1)
            
            val setter = conversationConfig::class.java.methods.find { it.name == "setThinkingConfig" || it.name.contains("Thinking", true) }
            setter?.invoke(conversationConfig, thinkingConfig)
            Log.i(TAG, "Successfully enabled Thinking mode via reflection")
        } catch (_: Exception) {}
        
        // Aggressive reflection to override chat template and fix Jinja errors
        val compatibleTemplate = "{% for message in messages %}<start_of_turn>{{message['role']}}\n{{message['content']}}<end_of_turn>\n{% endfor %}<start_of_turn>model\n"
        
        // Try official ExperimentalFlags first if possible
        try {
            ExperimentalFlags.overwritePromptTemplate = compatibleTemplate
        } catch (_: Throwable) {}

        listOf(conversationConfig, engine).forEach { obj ->
            try {
                obj::class.java.methods.find { it.name == "setPromptTemplate" || it.name.contains("Template", true) }
                    ?.invoke(obj, compatibleTemplate)
            } catch (_: Exception) {}
        }

        return engine.createConversation(conversationConfig)
    }

    @OptIn(ExperimentalApi::class)
    private fun loadLiteRtModel(modelPath: String, backendMode: DeviceBackendMode, config: ModelConfig): Int {
        Log.d(TAG, "Loading model using LiteRT-LM engine: $modelPath")
        activeEngineType = EngineType.LITERT_LM
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val npuSupported = isNpuSupported()

        Log.d(TAG, "NPU environment: nativeLibDir=$nativeLibDir, isNpuSupported=$npuSupported")
        
        // Diagnostic library scan
        try {
            val files = File(nativeLibDir).listFiles()
            Log.v(TAG, "Native libs scan (${files?.size ?: 0} found):")
            files?.forEach { Log.v(TAG, "  - ${it.name}") }
        } catch (_: Exception) {}

        val backendsToTry = when (backendMode) {
            DeviceBackendMode.NPU -> listOf("NPU" to Backend.NPU(nativeLibraryDir = nativeLibDir))
            DeviceBackendMode.GPU -> listOf("GPU" to Backend.GPU())
            DeviceBackendMode.CPU -> listOf("CPU" to Backend.CPU())
            DeviceBackendMode.AUTO -> {
                val list = mutableListOf<Pair<String, Backend>>()
                if (npuSupported) {
                    list.add("NPU" to Backend.NPU(nativeLibraryDir = nativeLibDir))
                }
                // GPU removed from AUTO to avoid driver crashes
                list.add("CPU" to Backend.CPU())
                list
            }
        }

        var lastError: String? = null
        for ((name, backend) in backendsToTry) {
            try {
                if (name == "NPU") {
                    try {
                        // Attempt to pre-load all possible accelerator bridges
                        val libs = listOf("LiteRt", "litert_jni", "litert_dispatch", "litert_npu_compiler", "QnnTFLiteDelegate", "QnnHtp")
                        libs.forEach { try { System.loadLibrary(it); Log.v(TAG, "Pre-loaded $it") } catch (_: Throwable) {} }
                    } catch (e: Throwable) {
                        Log.w(TAG, "NPU library pre-load failed: ${e.message}")
                    }
                }

                // Apply context bucketing for HTP stability (Power of 2)
                val bucketedCtx = when {
                    config.kvCacheSize <= 512 -> 512
                    config.kvCacheSize <= 1024 -> 1024
                    config.kvCacheSize <= 2048 -> 2048
                    config.kvCacheSize <= 4096 -> 4096
                    else -> 8192
                }

                val engineConfig = EngineConfig(
                    modelPath = modelPath,
                    backend = backend,
                    cacheDir = context.cacheDir.path
                )
                
                // Set advanced hardware flags and DispatchLibraryDir via reflection
                try {
                    val methods = engineConfig::class.java.methods
                    
                    // Log all methods for discovery
                    methods.forEach { Log.v(TAG, "LiteRT EngineConfig Method: ${it.name}") }

                    methods.find { it.name == "setSplitKvCache" }?.invoke(engineConfig, config.enableSplitKv)
                    methods.find { it.name == "setEnableAttentionCaching" }?.invoke(engineConfig, config.enableAttentionCaching)
                    methods.find { it.name == "setSequenceBatchSize" }?.invoke(engineConfig, config.sequenceBatchSize)
                    methods.find { it.name == "setMaxContextLength" }?.invoke(engineConfig, bucketedCtx)
                    
                    // Try to enable mmap if available
                    methods.find { it.name.lowercase().contains("mmap") }?.invoke(engineConfig, true)
                    
                    if (name == "NPU") {
                        val setter = methods.find { it.name.contains("Dispatch", true) && it.name.contains("Dir", true) } 
                            ?: methods.find { it.name.contains("Native", true) && it.name.contains("Dir", true) }
                        
                        if (setter != null) {
                            setter.invoke(engineConfig, nativeLibDir)
                            Log.i(TAG, "Applied NPU dispatch directory using: ${setter.name}")
                        }

                        // Debug: Dump all methods for discovery
                        methods.forEach { Log.v(TAG, "Discovery: Found method ${it.name}") }

                        // Enable NPU/QNN Debug Logging and Backend Config via reflection
                        try {
                            methods.find { it.name == "setLogLevel" }?.invoke(engineConfig, 3) // VERBOSE
                            methods.find { it.name == "setHtpPerformanceMode" }?.invoke(engineConfig, 4) // BURST
                            methods.find { it.name == "setHtpPrecision" }?.invoke(engineConfig, 1) // FP16
                            methods.find { it.name == "setUseConvHmx" }?.invoke(engineConfig, true)
                            Log.i(TAG, "Applied advanced NPU backend configuration")
                        } catch (e: Exception) {
                            Log.v(TAG, "Some advanced NPU setters not found: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "EngineConfig reflection failed: ${e.message}")
                }

                // Try to enable native debug logging globally
                try {
                    Engine.setNativeMinLogSeverity(com.google.ai.edge.litertlm.LogSeverity.VERBOSE)
                    Log.i(TAG, "Set LiteRT-LM native log severity to VERBOSE")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to set native log severity: ${e.message}")
                }

                val newEngine = Engine(engineConfig)
                newEngine.initialize()

                val newConversation = createLiteRtConversation(newEngine, config, currentSystemPrompt)

                engine = newEngine
                conversation = newConversation
                activeEngineType = EngineType.LITERT_LM
                activeBackendName = "LiteRT-LM ($name)"
                Log.i(TAG, "Successfully initialized LiteRT-LM Engine on $name")
                return 0
            } catch (e: Throwable) {
                lastError = e.message ?: e.toString()
                Log.w(TAG, "Failed to initialize LiteRT backend $name: $lastError")
            }
        }

        activeBackendName = "LITERT_LM FAILED ($lastError)"
        return -1
    }

    fun generateResponse(history: List<ChatMessage>, image: Bitmap? = null, systemPrompt: String? = null): Flow<String> = flow {
        if (!isModelLoaded) throw IllegalStateException("Model is not loaded.")
        val prompt = history.lastOrNull { it.senderType == SenderType.USER }?.text ?: ""
        Log.d(TAG, "Generating response for prompt: ${prompt.take(50)}...")
        
        if (activeEngineType == EngineType.LITERT_LM) {
            // Re-initialize conversation if system prompt changed to avoid template prefix mismatch
            if (systemPrompt != currentSystemPrompt) {
                currentSystemPrompt = systemPrompt
                engine?.let {
                    conversation?.close()
                    conversation = createLiteRtConversation(it, currentConfig, currentSystemPrompt)
                }
            }

            val activeConversation = conversation!!
            if (image != null) {
                try {
                    val addImageMethod = activeConversation::class.java.methods.find { it.name == "addImage" }
                    addImageMethod?.invoke(activeConversation, image)
                } catch (e: Exception) {
                    Log.e(TAG, "LiteRT image add failed", e)
                }
            }
            
            // LiteRT-LM handles templates internally via Conversation history.
            // Do NOT prepend manual tags here, as it causes "prefix mismatch" errors.
            activeConversation.sendMessageAsync(prompt).collect { message ->
                val text = stripControlTags(message.toString()).trim()
                if (text.isNotEmpty()) emit(text)
            }
        } else if (activeEngineType == EngineType.GENIE_X) {
            llmWrapper?.let { wrapper ->
                val formattedPrompt = applyChatTemplate(history, systemPrompt)
                val genConfig = com.geniex.sdk.bean.GenerationConfig(
                    maxTokens = currentConfig.maxTokens,
                    stopWords = arrayOf(
                        "<end_of_turn>", "<start_of_turn>", "<turn|>", "</s>", 
                        "\nuser", "\nmodel", "<model>", "<user>", "<assistant>",
                        "user\n", "model\n", "assistant\n", "### User:", "### Assistant:"
                    )
                )
                
                val fullResponseRaw = StringBuilder()
                var lastCleanLength = 0
                var stopTriggered = false

                wrapper.generateStreamFlow(formattedPrompt, genConfig).collect { result ->
                    if (stopTriggered || (result as Any?) == null) return@collect
                    
                    when (result) {
                        is GenieStreamResult.Token -> {
                            val tokenText = result.text
                            if (tokenText.isEmpty()) return@collect
                            
                            fullResponseRaw.append(tokenText)
                            val currentRaw = fullResponseRaw.toString()
                            
                            val stopMatch = STOP_TAG_REGEX.find(currentRaw)
                            if (stopMatch != null) {
                                // Found stop tag, emit everything before it
                                val textBeforeStop = currentRaw.substring(0, stopMatch.range.first)
                                val cleanBefore = stripControlTags(textBeforeStop)
                                val safeBefore = cleanBefore.replace(TRAILING_PARTIAL_TAG_REGEX, "")
                                if (safeBefore.length > lastCleanLength) {
                                    emit(safeBefore.substring(lastCleanLength))
                                    lastCleanLength = safeBefore.length
                                }
                                stopTriggered = true
                                return@collect
                            }

                            val cleanFull = stripControlTags(currentRaw)
                            val safeFull = cleanFull.replace(TRAILING_PARTIAL_TAG_REGEX, "")
                            if (safeFull.length > lastCleanLength) {
                                val newText = safeFull.substring(lastCleanLength)
                                emit(newText)
                                lastCleanLength = safeFull.length
                            }
                            kotlinx.coroutines.yield()
                        }
                        is GenieStreamResult.Completed -> {
                            Log.d(TAG, "GenieX stream completed successfully")
                            stopTriggered = true
                        }
                        is GenieStreamResult.Error -> {
                            val reason = result.throwable.message ?: result.throwable.toString()
                            Log.e(TAG, "GenieX stream error: $reason")
                            stopTriggered = true
                        }
                    }
                }

                val finalClean = stripControlTags(fullResponseRaw.toString()).replace(TRAILING_PARTIAL_TAG_REGEX, "").trimEnd()
                if (finalClean.length > lastCleanLength) {
                    val remaining = finalClean.substring(lastCleanLength)
                    if (remaining.isNotEmpty()) emit(remaining)
                }
            }
        } else if (activeEngineType == EngineType.LLAMA_CPP) {
            val formattedPrompt = applyChatTemplate(history, systemPrompt)
            LlamaAndroid.completion_init(
                prompt = formattedPrompt,
                temp = currentConfig.temperature,
                topK = currentConfig.topK,
                topP = currentConfig.topP,
                minP = currentConfig.minP,
                penaltyRepeat = 1.1f, // Default for now
                penaltyFreq = currentConfig.frequencyPenalty,
                penaltyPresent = currentConfig.presencePenalty
            )
            
            val fullResponseRaw = StringBuilder()
            var lastCleanLength = 0
            
            while (true) {
                val token = LlamaAndroid.completion_loop()
                if (token == "[EOS]" || token.isEmpty()) break
                
                fullResponseRaw.append(token)
                val currentRaw = fullResponseRaw.toString()
                
                val stopMatch = STOP_TAG_REGEX.find(currentRaw)
                if (stopMatch != null) {
                    val textBeforeStop = currentRaw.substring(0, stopMatch.range.first)
                    val cleanBefore = stripControlTags(textBeforeStop)
                    val safeBefore = cleanBefore.replace(TRAILING_PARTIAL_TAG_REGEX, "")
                    if (safeBefore.length > lastCleanLength) {
                        emit(safeBefore.substring(lastCleanLength))
                        lastCleanLength = safeBefore.length
                    }
                    break
                }

                val cleanFull = stripControlTags(currentRaw)
                val safeFull = cleanFull.replace(TRAILING_PARTIAL_TAG_REGEX, "")
                if (safeFull.length > lastCleanLength) {
                    val newText = safeFull.substring(lastCleanLength)
                    emit(newText)
                    lastCleanLength = safeFull.length
                }
                
                kotlinx.coroutines.yield()
                delay(5)
            }

            val finalClean = stripControlTags(fullResponseRaw.toString()).replace(TRAILING_PARTIAL_TAG_REGEX, "").trimEnd()
            if (finalClean.length > lastCleanLength) {
                val remaining = finalClean.substring(lastCleanLength)
                if (remaining.isNotEmpty()) emit(remaining)
            }
        }
    }.flowOn(Dispatchers.IO)

    fun stripControlTags(text: String): String = Companion.stripControlTags(text)

    fun resetConversation(systemPrompt: String? = currentSystemPrompt) {
        currentSystemPrompt = systemPrompt
        if (activeEngineType == EngineType.LITERT_LM && engine != null) {
            try {
                conversation?.close()
            } catch (_: Exception) {}
            conversation = createLiteRtConversation(engine!!, currentConfig, currentSystemPrompt)
            Log.i(TAG, "Reset LiteRT-LM conversation state.")
        }
    }

    fun stripThoughtBlocks(text: String): String {
        return text
            .replace(Regex("(?s)<think>.*?</think>"), "")
            .replace(Regex("(?s)<thought>.*?</thought>"), "")
            .replace(Regex("(?is)<think>.*$"), "")
            .replace(Regex("(?is)<thought>.*$"), "")
            .replace(Regex("(?is)^.*?</think>"), "")
            .replace(Regex("(?is)^.*?</thought>"), "")
            .trim()
    }

    private fun applyChatTemplate(history: List<ChatMessage>, systemPrompt: String? = null): String {
        val sb = StringBuilder()
        
        sb.append("<bos>")
        if (!systemPrompt.isNullOrBlank()) {
            sb.append("<start_of_turn>system\n$systemPrompt<end_of_turn>\n")
        }

        // Include the last 10 messages for context, cleaning any existing tags to avoid recursion/spam
        val contextHistory = history.filter { it.text != "..." }.takeLast(10)
        
        for (msg in contextHistory) {
            var cleanText = stripControlTags(msg.text).trim()
            if (cleanText.isEmpty()) continue
            
            cleanText = cleanText.replace(Regex("(?i)^(?:user|model|system|assistant)\\n+"), "").trim()
            if (cleanText.isEmpty()) continue
            
            if (msg.senderType == SenderType.BOT) {
                cleanText = stripThoughtBlocks(cleanText)
                if (cleanText.isEmpty()) continue
            }
            
            when (msg.senderType) {
                SenderType.USER -> sb.append("<start_of_turn>user\n$cleanText<end_of_turn>\n")
                SenderType.BOT -> sb.append("<start_of_turn>model\n$cleanText<end_of_turn>\n")
                else -> {}
            }
        }

        // Final trigger for the model to respond
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

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
            return@withContext java.util.Locale.US.let { String.format(it, "%.2f tokens/sec", tps) }
        } catch (e: Exception) {
            return@withContext "Error: ${e.message}"
        }
    }

    suspend fun unloadModel() = withContext(Dispatchers.IO) {
        unloadModelInternal()
    }

    private fun unloadModelInternal() {
        try {
            when (activeEngineType) {
                EngineType.LITERT_LM -> {
                    conversation?.close()
                    engine?.close()
                }
                EngineType.GENIE_X -> {
                    llmWrapper?.destroy()
                    // CRITICAL: Delay to allow Qualcomm kernel driver to release FastRPC handles
                    Thread.sleep(100)
                }
                EngineType.LLAMA_CPP -> {
                    LlamaAndroid.unloadModel()
                    // CRITICAL: Delay to allow Adreno kernel driver (KGSL) to reset compute states
                    Thread.sleep(100)
                }
                else -> {}
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            conversation = null
            engine = null
            llmWrapper = null
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
            java.util.Locale.US,
            "File: %s\nEngine: %s\nSize: %.2f MB\nBackend: %s\nState: %s",
            file.name,
            activeEngineType.name,
            sizeMb,
            activeBackendName,
            if (isModelLoaded) "READY" else "NOT READY"
        )
    }

    fun getNpuDiagnosticInfo(): NpuDiagnosticStatus {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val libDirFile = File(nativeLibDir)
        val splitManager = SplitInstallManagerFactory.create(context)
        
        val isModuleInstalled = splitManager.installedModules.contains("serenity_npu")
        val hasGenieXSo = File(libDirFile, "libgeniex.so").exists()
        val hasLlamaPluginSo = File(libDirFile, "libgeniex_plugin_llama_cpp.so").exists()
        val hasQairtPluginSo = File(libDirFile, "libgeniex_plugin_qairt.so").exists()
        val hasQnnTfLiteDelegateSo = File(libDirFile, "libQnnTFLiteDelegate.so").exists()
        val hasQnnHtpSo = File(libDirFile, "libQnnHtp.so").exists()
        val hasOmpSo = File(libDirFile, "libomp.so").exists()

        return NpuDiagnosticStatus(
            isNpuCapable = isNpuSupported() || hasQnnHtpSo || hasQnnTfLiteDelegateSo,
            genieXModuleInstalled = isModuleInstalled,
            hasGenieXCore = hasGenieXSo,
            hasLlamaPlugin = hasLlamaPluginSo,
            hasQairtPlugin = hasQairtPluginSo,
            hasQnnHtp = hasQnnHtpSo,
            hasOpenMp = hasOmpSo,
            nativeLibDir = nativeLibDir
        )
    }


}

data class NpuDiagnosticStatus(
    val isNpuCapable: Boolean,
    val genieXModuleInstalled: Boolean,
    val hasGenieXCore: Boolean,
    val hasLlamaPlugin: Boolean,
    val hasQairtPlugin: Boolean,
    val hasQnnHtp: Boolean,
    val hasOpenMp: Boolean,
    val nativeLibDir: String
)
