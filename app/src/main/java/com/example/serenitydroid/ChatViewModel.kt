package com.example.serenitydroid

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.example.serenitydroid.engine.DeviceBackendMode
import com.example.serenitydroid.engine.DeviceDetector
import com.example.serenitydroid.engine.InferenceManager
import com.example.serenitydroid.engine.ModelConfig
import com.example.serenitydroid.engine.ModelFile
import com.example.serenitydroid.engine.ModelRefresher
import com.example.serenitydroid.model.ChatMessage
import com.example.serenitydroid.model.Persona
import com.example.serenitydroid.model.RuntimeOption
import com.example.serenitydroid.model.SenderType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class UiState(
    val isModelLoaded: Boolean = false,
    val isModelLoading: Boolean = false,
    val isGenerating: Boolean = false,
    val useGpu: Boolean = true,
    val backendMode: DeviceBackendMode = DeviceBackendMode.AUTO,
    val isTurbo: Boolean = false,
    val hurryUpPresses: Int = 0,
    val errorMessage: String? = null,
    val modelStatusText: String = "Model: Not Loaded",
    val customModelPath: String? = null,
    val gpuLayers: Int = 35,
    val isAutoGpuLayers: Boolean = true,
    val isAutoLoadOnStartup: Boolean = false,
    val systemRamText: String = "",
    val contextSize: Int = 4096,
    val isAppProfilingEnabled: Boolean = false,
    val totalMemoryGb: Float = 0f,
    val freeMemoryGb: Float = 0f,
    val appMemoryMb: Float = 0f,
    val appProfilingText: String = "",
    val attachedImageUri: Uri? = null,
    val selectedRuntimes: Set<RuntimeOption> = RuntimeOption.ALL_LEAF_RUNTIMES,
    val benchmarkResult: String? = null,
    val isBenchmarking: Boolean = false,
    val conversionOfferModel: ModelFile? = null,
    val isScrollLocked: Boolean = true,
    
    // Model Parameters
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
    val bufferSize: Int = 1024,

    // UI Settings
    val chatTextSize: Float = 14f,
    val generationMetrics: String? = null,
    val showMetrics: Boolean = false
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val inferenceManager: InferenceManager,
    private val modelRefresher: ModelRefresher,
    val deviceDetector: DeviceDetector,
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    companion object {
        private const val TAG = "ChatViewModel"
    }
    
    private val _availableModels = MutableStateFlow<List<ModelFile>>(emptyList())
    val availableModels: StateFlow<List<ModelFile>> = _availableModels.asStateFlow()

    private val personaMap = mapOf(
        0 to Persona(0, "LVL 0: ERROR", "Model failed.", R.drawable.ic_launcher_foreground, R.drawable.background_transition, R.color.thermo_0, android.R.color.white, 0, ""),
        1 to Persona(1, "LVL 1: Fast", "Concise.", R.drawable.lvl1_speedy_serenity, R.drawable.lvl1_galaxy, R.color.thermo_1, android.R.color.white, 7, "gemma-3-1b-it.litertlm"),
        2 to Persona(2, "LVL 2: Helper", "Helpful.", R.drawable.lvl2_serenity_wink, R.drawable.lvl2_galaxy, R.color.thermo_2, android.R.color.white, 14, "gemma-3-1b-it.litertlm"),
        3 to Persona(3, "LVL 3: Partner", "Collaborative.", R.drawable.lvl3_serenity_hug, R.drawable.lvl3_galaxy, R.color.thermo_3, android.R.color.white, 21, "gemma-3-1b-it.litertlm"),
        4 to Persona(4, "LVL 4: Companion", "Supportive.", R.drawable.lvl4_serenity_smart, R.drawable.lvl4_galaxy, R.color.thermo_4, android.R.color.white, 28, "gemma-3-1b-it.litertlm"),
        5 to Persona(5, "LVL 5: Sage", "Wise.", R.drawable.lvl5_serenity_the_wise, R.drawable.lvl5_galaxy, R.color.thermo_5, android.R.color.white, 35, "gemma-3-1b-it.litertlm"),
        6 to Persona(6, "Cecilia", "Teasing.", R.drawable.cecilia_01, R.drawable.lvl6_galaxy, R.color.thermo_6, android.R.color.white, 35, "gemma-3-1b-it.litertlm")
    )

    private val systemPrompts = mapOf(
        1 to "Be concise.", 2 to "Be helpful.", 3 to "Be collaborative.", 4 to "Be a companion.", 5 to "Be wise.", 6 to "Be teasing."
    )

    private var generationJob: Job? = null
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    private val _selectedLevel = MutableStateFlow(1)
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    private val _activePersona = MutableStateFlow(personaMap.getValue(1))
    val activePersona: StateFlow<Persona> = _activePersona.asStateFlow()
    private var messageCounter = 0L

    init {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        _uiState.update { it.copy(
            isAutoLoadOnStartup = prefs.getBoolean("autoload_on_startup", false),
            isAppProfilingEnabled = prefs.getBoolean("app_profiling_enabled", false),
            chatTextSize = prefs.getFloat("chat_text_size", 14f)
        ) }
        updateAvailableModels()
        updateMemoryStats()
        if (_uiState.value.isAutoLoadOnStartup) loadModel()
        
        // TODO: Integrate Hugging Face Hub (HF Portal)
    }

    fun setParameter(
        temp: Float? = null,
        k: Int? = null,
        p: Float? = null,
        minP: Float? = null,
        freq: Float? = null,
        pres: Float? = null,
        mtp: Boolean? = null,
        splitKv: Boolean? = null,
        attnCache: Boolean? = null,
        batchSize: Int? = null,
        kvSize: Int? = null,
        bufSize: Int? = null
    ) {
        _uiState.update { it.copy(
            temperature = temp ?: it.temperature,
            topK = k ?: it.topK,
            topP = p ?: it.topP,
            minP = minP ?: it.minP,
            frequencyPenalty = freq ?: it.frequencyPenalty,
            presencePenalty = pres ?: it.presencePenalty,
            enableMtp = mtp ?: it.enableMtp,
            enableSplitKv = splitKv ?: it.enableSplitKv,
            enableAttentionCaching = attnCache ?: it.enableAttentionCaching,
            sequenceBatchSize = batchSize ?: it.sequenceBatchSize,
            kvCacheSize = kvSize ?: it.kvCacheSize,
            bufferSize = bufSize ?: it.bufferSize
        ) }
    }

    fun toggleScrollLock() {
        _uiState.update { it.copy(isScrollLocked = !it.isScrollLocked) }
    }

    fun toggleMetrics() {
        _uiState.update { it.copy(showMetrics = !it.showMetrics) }
    }

    fun setUiSettings(textSize: Float? = null) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        _uiState.update { 
            val newSize = textSize ?: it.chatTextSize
            prefs.edit()
                .putFloat("chat_text_size", newSize)
                .apply()
            it.copy(chatTextSize = newSize)
        }
    }

    fun runBenchmark() {
        if (!_uiState.value.isModelLoaded) return
        _uiState.update { it.copy(isBenchmarking = true, benchmarkResult = "Running...") }
        viewModelScope.launch {
            val res = inferenceManager.runBenchmark()
            _uiState.update { it.copy(isBenchmarking = false, benchmarkResult = res) }
        }
    }

    fun renameModel(modelFile: ModelFile, newDisplayName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = modelFile.file
            val ext = file.extension
            val newFile = java.io.File(file.parentFile, "$newDisplayName.$ext")
            if (file.renameTo(newFile)) {
                if (_uiState.value.customModelPath == file.name) setCustomModel(newFile.name)
                updateAvailableModels()
            }
        }
    }

    fun deleteModel(modelFile: ModelFile) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_uiState.value.customModelPath == modelFile.file.name) {
                offloadModel()
                setCustomModel("")
            }
            modelFile.file.delete()
            updateAvailableModels()
        }
    }

    fun clearModels() {
        viewModelScope.launch(Dispatchers.IO) {
            offloadModel()
            val modelsDir = java.io.File(context.filesDir, "Models")
            modelsDir.deleteRecursively()
            modelsDir.mkdirs()
            updateAvailableModels()
        }
    }

    fun importModelsFromFolder(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val rootDoc = DocumentFile.fromTreeUri(context, uri) ?: return@launch
            val modelsDir = java.io.File(context.filesDir, "Models").apply { mkdirs() }
            importRecursively(rootDoc, modelsDir, ModelRefresher.DEFAULT_EXTENSIONS)
            updateAvailableModels()
        }
    }

    private fun importRecursively(parent: DocumentFile, targetDir: java.io.File, exts: Set<String>) {
        parent.listFiles().forEach { doc ->
            val name = doc.name ?: return@forEach
            if (doc.isDirectory) {
                val nextDir = java.io.File(targetDir, name).apply { mkdirs() }
                importRecursively(doc, nextDir, exts)
            } else if (doc.isFile && exts.contains(name.substringAfterLast(".").lowercase())) {
                val outFile = java.io.File(targetDir, name)
                try {
                    context.contentResolver.openInputStream(doc.uri)?.use { it.copyTo(java.io.FileOutputStream(outFile)) }
                } catch (e: Exception) { Log.e(TAG, "Import failed: $name", e) }
            }
        }
    }

    fun updateAvailableModels(folder: java.io.File = java.io.File(context.filesDir, "Models")) {
        viewModelScope.launch(Dispatchers.IO) {
            val models = modelRefresher.refreshModels(folder)
            _availableModels.value = models
        }
    }

    fun setCustomModel(path: String) {
        _uiState.update { it.copy(customModelPath = path.ifBlank { null }) }
        if (_uiState.value.isModelLoaded) offloadModel()
    }

    fun setBackendMode(mode: DeviceBackendMode) {
        _uiState.update { it.copy(backendMode = mode, useGpu = (mode != DeviceBackendMode.CPU)) }
    }

    fun loadModel() {
        if (_uiState.value.isModelLoading) return
        loadModelForCurrentPersona()
    }

    private fun loadModelForCurrentPersona() {
        val state = _uiState.value
        _uiState.update { it.copy(isModelLoading = true, errorMessage = null) }
        addMessage(SenderType.SYSTEM, "Loading...")

        viewModelScope.launch {
            try {
                val modelsDir = java.io.File(context.filesDir, "Models")
                val selected = state.customModelPath ?: PreferenceManager.getDefaultSharedPreferences(context).getString("selected_model", null)
                
                val file = if (!selected.isNullOrBlank()) {
                    java.io.File(modelsDir, selected).takeIf { it.exists() } ?: java.io.File(selected).takeIf { it.exists() }
                } else {
                    modelsDir.walkTopDown().firstOrNull { it.isFile && it.extension == "litertlm" }
                }

                if (file != null) {
                    val config = ModelConfig(
                        temperature = state.temperature,
                        topK = state.topK,
                        topP = state.topP,
                        minP = state.minP,
                        frequencyPenalty = state.frequencyPenalty,
                        presencePenalty = state.presencePenalty,
                        enableMtp = state.enableMtp,
                        enableSplitKv = state.enableSplitKv,
                        enableAttentionCaching = state.enableAttentionCaching,
                        sequenceBatchSize = state.sequenceBatchSize,
                        kvCacheSize = state.kvCacheSize,
                        bufferSize = state.bufferSize
                    )
                    
                    val res = inferenceManager.loadModel(file.absolutePath, state.backendMode, config)
                    if (res == 0) {
                        _uiState.update { it.copy(isModelLoading = false, isModelLoaded = true) }
                        addMessage(SenderType.SYSTEM, "Loaded: ${inferenceManager.activeBackendName}")
                    } else {
                        throw java.io.IOException("Engine failed.")
                    }
                } else {
                    _uiState.update { it.copy(isModelLoading = false, errorMessage = "No model found") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Load error", e)
                _uiState.update { it.copy(isModelLoading = false, isModelLoaded = false, errorMessage = e.message) }
            } finally {
                updateModelStatusText()
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _uiState.value.isGenerating || !_uiState.value.isModelLoaded) return
        
        addMessage(SenderType.USER, text)
        val botMsg = addMessage(SenderType.BOT, "...")
        _uiState.update { it.copy(isGenerating = true, generationMetrics = "Calculating...") }

        generationJob = viewModelScope.launch {
            val startMs = System.currentTimeMillis()
            var firstTokenMs = 0L
            var tokenCount = 0

            inferenceManager.generateResponse(text)
                .catch { cause -> updateLastBotMessage(botMsg.id, "Error: ${cause.message}") }
                .onCompletion { 
                    val endMs = System.currentTimeMillis()
                    val totalSec = (endMs - startMs) / 1000f
                    val ttftSec = if (firstTokenMs > 0) (firstTokenMs - startMs) / 1000f else 0f
                    val decodeSec = totalSec - ttftSec
                    val decodeSpeed = if (decodeSec > 0) (tokenCount - 1) / decodeSec else 0f
                    val prefillSpeed = if (ttftSec > 0) 1 / ttftSec else 0f // Rough estimate
                    
                    val metrics = java.util.Locale.US.let { 
                        String.format(it, "TTFT: %.2fs | Prefill: %.1f t/s | Decode: %.1f t/s | Tokens: %d", 
                            ttftSec, prefillSpeed, decodeSpeed, tokenCount) 
                    }
                    _uiState.update { it.copy(isGenerating = false, generationMetrics = metrics) } 
                }
                .collect { chunk -> 
                    if (firstTokenMs == 0L) firstTokenMs = System.currentTimeMillis()
                    tokenCount++
                    updateLastBotMessage(botMsg.id, chunk, append = true) 
                }
        }
    }

    fun offloadModel() {
        viewModelScope.launch {
            inferenceManager.unloadModel()
            _uiState.update { it.copy(isModelLoaded = false) }
            updateModelStatusText()
        }
    }

    fun setSelectedLevel(level: Int) {
        _selectedLevel.value = level
        _activePersona.value = personaMap[level] ?: personaMap.getValue(1)
        loadModel()
    }

    private fun addMessage(sender: SenderType, text: String): ChatMessage {
        val msg = ChatMessage(messageCounter++, sender, text)
        _messages.update { it + msg }
        return msg
    }

    private fun updateLastBotMessage(id: Long, text: String, append: Boolean = false) {
        _messages.update { list ->
            list.map { if (it.id == id) it.copy(text = if (append && it.text != "...") it.text + text else text) else it }
        }
    }

    private fun updateModelStatusText() {
        val s = _uiState.value
        val status = if (s.isModelLoading) "Loading..." else if (s.isModelLoaded) "Active: ${inferenceManager.activeBackendName}" else "Model: Not Loaded"
        _uiState.update { it.copy(modelStatusText = status) }
    }

    fun updateMemoryStats() {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mi)
        _uiState.update { it.copy(
            totalMemoryGb = mi.totalMem / 1e9f,
            freeMemoryGb = mi.availMem / 1e9f,
            appMemoryMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1e6f
        ) }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { inferenceManager.unloadModel() }
    }

    fun getSystemMemoryStats(): String {
        updateMemoryStats()
        return java.util.Locale.US.let { String.format(it, "%.1f GB Total / %.1f GB Avail", _uiState.value.totalMemoryGb, _uiState.value.freeMemoryGb) }
    }

    fun setAutoLoadOnStartup(e: Boolean) { _uiState.update { it.copy(isAutoLoadOnStartup = e) } }
    fun setAppProfilingEnabled(e: Boolean) { _uiState.update { it.copy(isAppProfilingEnabled = e) } }
    fun setAutoGpuLayers(e: Boolean) { _uiState.update { it.copy(isAutoGpuLayers = e) } }
    fun setGpuLayers(l: Int) { _uiState.update { it.copy(gpuLayers = l, isAutoGpuLayers = false) } }
    fun setContextSize(s: Int) { _uiState.update { it.copy(contextSize = s) } }
    fun toggleRuntime(r: RuntimeOption) { updateSelectedRuntimes(_uiState.value.selectedRuntimes.toMutableSet().apply { if (contains(r)) remove(r) else add(r) }) }
    private fun updateSelectedRuntimes(rs: Set<RuntimeOption>) { _uiState.update { it.copy(selectedRuntimes = rs) } }
    fun toggleGenieXGroup() {}
    fun toggleAllRuntimes() {}
    fun dismissConversionOffer() { _uiState.update { it.copy(conversionOfferModel = null) } }
    fun saveHistory() {}
    fun loadHistory(l: Int) {}
    fun attachImage(u: Uri) {}
    fun clearImage() {}
    fun toggleDevice() {}
}
