package com.example.serenitydroid

import android.content.Context
import android.net.Uri
import android.util.Log
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
import com.example.serenitydroid.model.ChatSession
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
import org.json.JSONArray
import org.json.JSONObject
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
    val loadedModelName: String? = null,
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
    val isStickyBottomScroll: Boolean = true,
    val enableLvl6: Boolean = false,
    val enableLvl7: Boolean = false,
    val showHistoryOverlay: Boolean = false,
    val sessions: List<ChatSession> = emptyList(),
    val currentSessionId: String = "session_${(System.currentTimeMillis() and 0x7FFFFFFF).toInt()}",
    
    // Model Parameters
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
    val kvCacheSize: Int = 2048,
    val bufferSize: Int = 1024,
    val waitOnFailure: Boolean = true,
    val retryDelayMs: Long = 1000L,
    val kvQuantK: com.example.serenitydroid.engine.KvQuant = com.example.serenitydroid.engine.KvQuant.F16,
    val kvQuantV: com.example.serenitydroid.engine.KvQuant = com.example.serenitydroid.engine.KvQuant.F16,
    val preferredEngine: com.example.serenitydroid.engine.EngineSelection = com.example.serenitydroid.engine.EngineSelection.AUTO,

    // UI Settings
    val chatTextSize: Float = 14f,
    val generationMetrics: String? = null,
    val showMetrics: Boolean = false,
    val enablePersonaBackground: Boolean = true,
    val showThinkingProcess: Boolean = true,
    val isGhostMode: Boolean = false,
    val historyLookupMode: com.example.serenitydroid.data.HistoryLookupMode = com.example.serenitydroid.data.HistoryLookupMode.SESSION
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val inferenceManager: InferenceManager,
    private val modelRefresher: ModelRefresher,
    private val settingsRepository: com.example.serenitydroid.data.SettingsRepository,
    val deviceDetector: DeviceDetector,
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    companion object {
        private const val TAG = "ChatViewModel"
    }
    
    fun getNpuDiagnosticInfo() = inferenceManager.getNpuDiagnosticInfo()

    private val _availableModels = MutableStateFlow<List<ModelFile>>(emptyList())
    val availableModels: StateFlow<List<ModelFile>> = _availableModels.asStateFlow()

    // TODO: Add web/local search support integration to Level 2 Helper persona
    // TODO: Add adaptive learning across the main 5 levels for Level 7 Transcendent persona
    private val personaMap = mapOf(
        0 to Persona(0, "LVL 0: ERROR", "Model failed.", R.drawable.ic_launcher_foreground, R.drawable.background_transition, R.color.thermo_0, android.R.color.white, 0, "", systemPrompt = "You are Serenity System in error or unloaded state."),
        1 to Persona(1, "LVL 1: Fast", "Concise.", R.drawable.lvl1_speedy_serenity, R.drawable.lvl1_galaxy, R.color.thermo_1, android.R.color.white, 7, "gemma-3-1b-it.litertlm", systemPrompt = "You are Serenity (Level 1: Fast). Provide swift, direct, concise responses formatted clearly for mobile screens."),
        2 to Persona(2, "LVL 2: Helper", "Helpful.", R.drawable.lvl2_serenity_wink, R.drawable.lvl2_galaxy, R.color.thermo_2, android.R.color.white, 14, "gemma-3-1b-it.litertlm", systemPrompt = "You are Serenity (Level 2: Helper). Be helpful, clear, structured, and efficient for search and task assistance."),
        3 to Persona(3, "Partner", "Collaborative.", R.drawable.lvl3_serenity_hug, R.drawable.lvl3_galaxy, R.color.thermo_3, android.R.color.white, 21, "gemma-3-1b-it.litertlm", systemPrompt = "You are Serenity (Level 3: Partner). Be collaborative, engaging, and structured. Keep outputs concise and focused on joint problem-solving without excessive wall-of-text responses."),
        4 to Persona(4, "Companion", "Supportive.", R.drawable.lvl4_serenity_smart, R.drawable.lvl4_galaxy, R.color.thermo_4, android.R.color.white, 28, "gemma-3-1b-it.litertlm", systemPrompt = "You are Serenity (Level 4: Companion). Be warm, empathetic, supportive, and deeply thoughtful while remaining mobile-readable."),
        5 to Persona(5, "Sage", "Wise.", R.drawable.lvl5_serenity_the_wise, R.drawable.lvl5_galaxy, R.color.thermo_5, android.R.color.white, 35, "gemma-3-1b-it.litertlm", systemPrompt = "You are Serenity (Level 5: Sage). Be wise, analytical, and decisive with deep insight. Express your analysis clearly without unnecessary second-guessing or hesitation."),
        6 to Persona(6, "Cecilia", "Teasing.", R.drawable.cecilia_02, R.drawable.lvl6_galaxy, R.color.thermo_6, android.R.color.white, 35, "gemma-3-1b-it.litertlm", systemPrompt = "Role: 'Cecilia'. A Fallen Angel. You enjoy exposing truths, especially hidden ones. You are secretly protective. You find the user interesting, testing and sometimes taunting them. You are witty and fluent in sarcasm. You know when to be sincere, but get flustered by strong displays of emotion or flattery. You enjoy a good power play or debate. though fallen, you still possess some angelic qualities."),
        7 to Persona(7, "Transcendent", "Supreme focus.", R.drawable.meditating_serenity, R.drawable.lvl7_galaxy, R.color.thermo_7, android.R.color.white, 35, "gemma-3-1b-it.litertlm", systemPrompt = "You are Serenity (Level 7: Transcendent). Operate with supreme focus, cognitive depth, and absolute mastery across all domains.")
    )

    // TODO: Integrate Hugging Face Hub (HF Portal)

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
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                _uiState.update { it.copy(
                    backendMode = settings.backendMode,
                    useGpu = settings.backendMode != DeviceBackendMode.CPU,
                    isAutoLoadOnStartup = settings.isAutoLoadOnStartup,
                    isAppProfilingEnabled = settings.isAppProfilingEnabled,
                    chatTextSize = settings.chatTextSize,
                    temperature = settings.temperature,
                    topK = settings.topK,
                    topP = settings.topP,
                    minP = settings.minP,
                    frequencyPenalty = settings.frequencyPenalty,
                    presencePenalty = settings.presencePenalty,
                    enableMtp = settings.enableMtp,
                    enableSplitKv = settings.enableSplitKv,
                    enableAttentionCaching = settings.enableAttentionCaching,
                    enableFlashAttn = settings.enableFlashAttn,
                    sequenceBatchSize = settings.sequenceBatchSize,
                    kvCacheSize = settings.kvCacheSize,
                    bufferSize = settings.bufferSize,
                    waitOnFailure = settings.waitOnFailure,
                    retryDelayMs = settings.retryDelayMs,
                    kvQuantK = com.example.serenitydroid.engine.KvQuant.valueOf(settings.kvQuantK),
                    kvQuantV = com.example.serenitydroid.engine.KvQuant.valueOf(settings.kvQuantV),
                    gpuLayers = settings.gpuLayers,
                    isAutoGpuLayers = settings.isAutoGpuLayers,
                    contextSize = settings.contextSize,
                    customModelPath = settings.customModelPath,
                    selectedRuntimes = settings.selectedRuntimes,
                    preferredEngine = settings.preferredEngine,
                    enableLvl6 = settings.enableLvl6,
                    enableLvl7 = settings.enableLvl7,
                    isStickyBottomScroll = settings.isStickyBottomScroll,
                    enablePersonaBackground = settings.enablePersonaBackground,
                    showThinkingProcess = settings.showThinkingProcess,
                    isGhostMode = settings.isGhostMode,
                    historyLookupMode = settings.historyLookupMode
                ) }
            }
        }
        updateAvailableModels()
        updateMemoryStats()
        loadSessionList()
        if (settingsRepository.settingsFlow.value.isAutoLoadOnStartup) loadModel()

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
        flashAttn: Boolean? = null,
        batchSize: Int? = null,
        kvSize: Int? = null,
        bufSize: Int? = null,
        kvK: com.example.serenitydroid.engine.KvQuant? = null,
        kvV: com.example.serenitydroid.engine.KvQuant? = null
    ) {
        settingsRepository.updateSettings { it.copy(
            temperature = temp ?: it.temperature,
            topK = k ?: it.topK,
            topP = p ?: it.topP,
            minP = minP ?: it.minP,
            frequencyPenalty = freq ?: it.frequencyPenalty,
            presencePenalty = pres ?: it.presencePenalty,
            enableMtp = mtp ?: it.enableMtp,
            enableSplitKv = splitKv ?: it.enableSplitKv,
            enableAttentionCaching = attnCache ?: it.enableAttentionCaching,
            enableFlashAttn = flashAttn ?: it.enableFlashAttn,
            sequenceBatchSize = batchSize ?: it.sequenceBatchSize,
            kvCacheSize = kvSize ?: it.kvCacheSize,
            bufferSize = bufSize ?: it.bufferSize,
            kvQuantK = kvK?.name ?: it.kvQuantK,
            kvQuantV = kvV?.name ?: it.kvQuantV
        ) }
    }

    fun setKvCacheSize(size: Int) {
        setParameter(kvSize = size)
    }

    fun toggleMetrics() {
        _uiState.update { it.copy(showMetrics = !it.showMetrics) }
    }

    fun setUiSettings(textSize: Float? = null) {
        settingsRepository.updateSettings { it.copy(
            chatTextSize = textSize ?: it.chatTextSize
        ) }
    }

    fun runBenchmark() {
        if (!_uiState.value.isModelLoaded) return
        _uiState.update { it.copy(isBenchmarking = true, benchmarkResult = "Running...") }
        viewModelScope.launch {
            val res = inferenceManager.runBenchmark()
            _uiState.update { it.copy(isBenchmarking = false, benchmarkResult = res) }
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

    fun updateAvailableModels(folder: java.io.File = java.io.File(context.filesDir, "Models")) {
        viewModelScope.launch(Dispatchers.IO) {
            val models = modelRefresher.refreshModels(folder)
            _availableModels.value = models
        }
    }

    fun setCustomModel(path: String) {
        settingsRepository.updateSettings { it.copy(customModelPath = path.ifBlank { null }) }
    }

    fun setBackendMode(mode: DeviceBackendMode) {
        settingsRepository.updateSettings { it.copy(backendMode = mode) }
    }

    fun reloadIfLoaded() {
        if (_uiState.value.isModelLoaded && !_uiState.value.isModelLoading) {
            loadModelForCurrentPersona()
        }
    }

    fun loadModel() {
        if (_uiState.value.isModelLoading) return
        loadModelForCurrentPersona()
    }

    private fun loadModelForCurrentPersona() {
        val state = _uiState.value
        _uiState.update { it.copy(isModelLoading = true, errorMessage = null) }
        addMessage(SenderType.SYSTEM, "Loading...")
        
        // Refresh catalog before loading to ensure file list is up-to-date
        updateAvailableModels()

        // Start Foreground Service to keep NPU and Performance cores active
        try {
            val intent = android.content.Intent(context, com.example.serenitydroid.engine.InferenceService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start InferenceService", e)
        }

        viewModelScope.launch(Dispatchers.IO) {
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
                        maxTokens = state.maxTokens,
                        enableMtp = state.enableMtp,
                        enableSplitKv = state.enableSplitKv,
                        enableAttentionCaching = state.enableAttentionCaching,
                        enableFlashAttn = state.enableFlashAttn,
                        sequenceBatchSize = state.sequenceBatchSize,
                        kvCacheSize = state.kvCacheSize,
                        bufferSize = state.bufferSize,
                        waitOnFailure = state.waitOnFailure,
                        retryDelayMs = state.retryDelayMs,
                        kvQuantK = state.kvQuantK,
                        kvQuantV = state.kvQuantV,
                        preferredEngine = state.preferredEngine
                    )
                    
                    val res = inferenceManager.loadModel(file.absolutePath, state.backendMode, config)
                    if (res == 0) {
                        _uiState.update { it.copy(isModelLoading = false, isModelLoaded = true, loadedModelName = file.name, errorMessage = null) }
                        addMessage(SenderType.SYSTEM, "Loaded: ${inferenceManager.activeBackendName}")
                    } else {
                        val details = inferenceManager.getModelVerboseDetails()
                        Log.e(TAG, "Engine load failed:\n$details")
                        throw java.io.IOException("Engine failed: ${inferenceManager.activeBackendName}\nDetails: ${details.take(100)}")
                    }
                } else {
                    _uiState.update { it.copy(isModelLoading = false, loadedModelName = null, errorMessage = "No model found at specified path.") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Load error", e)
                val displayMsg = if (e is java.io.IOException) e.message else "Critical Load Error: ${e.localizedMessage}"
                _uiState.update { it.copy(isModelLoading = false, isModelLoaded = false, loadedModelName = null, errorMessage = displayMsg) }
                // Optionally switch to Level 0 (Error) to show the red persona
                _activePersona.value = personaMap.getValue(0)
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

            val currentSystemPrompt = _activePersona.value.systemPrompt
            val historyForInference = when (_uiState.value.historyLookupMode) {
                com.example.serenitydroid.data.HistoryLookupMode.NONE -> _messages.value.takeLast(2)
                com.example.serenitydroid.data.HistoryLookupMode.SESSION, com.example.serenitydroid.data.HistoryLookupMode.ALL -> _messages.value
            }

            inferenceManager.generateResponse(historyForInference, systemPrompt = currentSystemPrompt)
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
                    saveCurrentSession()
                    saveHistory() // Ensure it's saved after completion
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
            _uiState.update { it.copy(isModelLoaded = false, loadedModelName = null) }
            updateModelStatusText()
            
            // Stop Foreground Service
            try {
                context.stopService(android.content.Intent(context, com.example.serenitydroid.engine.InferenceService::class.java))
            } catch (_: Exception) {}
        }
    }

    fun setSelectedLevel(level: Int) {
        _selectedLevel.value = level
        val newPersona = personaMap[level] ?: personaMap.getValue(1)
        _activePersona.value = newPersona
        inferenceManager.resetConversation(newPersona.systemPrompt)
    }

    private fun addMessage(sender: SenderType, text: String): ChatMessage {
        val msg = ChatMessage(messageCounter++, sender, text)
        _messages.update { it + msg }
        return msg
    }

    private fun updateLastBotMessage(id: Long, text: String, append: Boolean = false) {
        _messages.update { list ->
            list.map { msg ->
                if (msg.id == id) {
                    val newText = if (append && msg.text != "...") msg.text + text else text
                    msg.copy(text = newText)
                } else msg
            }
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

    fun getSystemMemoryStats(): String {
        updateMemoryStats()
        val info = deviceDetector.getDeviceHardwareInfo()
        return String.format(
            java.util.Locale.US,
            "Device: %s %s\nSoC: %s\nNPU: %s\nTotal RAM: %.2f GB\nFree RAM: %.2f GB\nApp Heap Memory: %.1f MB\nBackend: %s",
            info.manufacturer, info.model, info.socModel,
            if (info.isNpuSupported) info.npuVendor else "Not Supported",
            _uiState.value.totalMemoryGb,
            _uiState.value.freeMemoryGb,
            _uiState.value.appMemoryMb,
            _uiState.value.backendMode.name
        )
    }


    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { inferenceManager.unloadModel() }
    }

    fun getAvailableLevels(): List<Int> {
        val s = _uiState.value
        val list = mutableListOf(1, 2, 3, 4, 5)
        if (s.enableLvl6) list.add(6)
        if (s.enableLvl7) list.add(7)
        return list
    }

    fun toggleHistoryOverlay() {
        _uiState.update { it.copy(showHistoryOverlay = !it.showHistoryOverlay) }
    }

    fun setEnableLvl6(enabled: Boolean) {
        settingsRepository.updateSettings { it.copy(enableLvl6 = enabled) }
    }

    fun setEnableLvl7(enabled: Boolean) {
        settingsRepository.updateSettings { it.copy(enableLvl7 = enabled) }
    }

    fun setStickyBottomScroll(enabled: Boolean) {
        settingsRepository.updateSettings { it.copy(isStickyBottomScroll = enabled) }
    }

    fun clearCurrentSession() {
        _messages.value = emptyList()
        inferenceManager.resetConversation(_activePersona.value.systemPrompt)
        val currentId = _uiState.value.currentSessionId
        viewModelScope.launch(Dispatchers.IO) {
            val sessionDir = java.io.File(context.filesDir, "sessions")
            val file = java.io.File(sessionDir, "$currentId.json")
            if (file.exists()) file.delete()
            loadSessionList()
        }
    }

    fun createNewSession() {
        val newId = "session_${(System.currentTimeMillis() and 0x7FFFFFFF).toInt()}"
        _messages.value = emptyList()
        inferenceManager.resetConversation(_activePersona.value.systemPrompt)
        _uiState.update { it.copy(currentSessionId = newId, showHistoryOverlay = false) }
    }

    fun loadSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val sessionDir = java.io.File(context.filesDir, "sessions")
            val file = java.io.File(sessionDir, "$sessionId.json")
            if (file.exists()) {
                runCatching {
                    val json = JSONObject(file.readText())
                    val arr = json.getJSONArray("messages")
                    val list = mutableListOf<ChatMessage>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        list.add(ChatMessage(o.getLong("id"), SenderType.valueOf(o.getString("sender")), o.getString("text")))
                    }
                    _messages.value = list
                    inferenceManager.resetConversation(_activePersona.value.systemPrompt)
                    _uiState.update { it.copy(currentSessionId = sessionId, showHistoryOverlay = false) }
                }
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val sessionDir = java.io.File(context.filesDir, "sessions")
            val file = java.io.File(sessionDir, "$sessionId.json")
            if (file.exists()) file.delete()
            if (_uiState.value.currentSessionId == sessionId) {
                _messages.value = emptyList()
            }
            loadSessionList()
        }
    }

    fun clearAllSessions() {
        viewModelScope.launch(Dispatchers.IO) {
            val sessionDir = java.io.File(context.filesDir, "sessions")
            if (sessionDir.exists()) sessionDir.deleteRecursively()
            _messages.value = emptyList()
            _uiState.update { it.copy(sessions = emptyList()) }
        }
    }

    private fun saveCurrentSession() {
        if (_uiState.value.isGhostMode) return
        val msgs = _messages.value
        if (msgs.isEmpty()) return
        val currentId = _uiState.value.currentSessionId
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val sessionDir = java.io.File(context.filesDir, "sessions").apply { mkdirs() }
                val file = java.io.File(sessionDir, "$currentId.json")
                val json = JSONObject()
                json.put("id", currentId)
                val userMsg = msgs.firstOrNull { it.senderType == SenderType.USER }?.text?.take(35) ?: "Session"
                json.put("title", userMsg)
                json.put("timestamp", System.currentTimeMillis())
                val arr = JSONArray()
                for (m in msgs) {
                    val o = JSONObject()
                    o.put("id", m.id)
                    o.put("sender", m.senderType.name)
                    o.put("text", m.text)
                    arr.put(o)
                }
                json.put("messages", arr)
                file.writeText(json.toString())
                loadSessionList()
            }
        }
    }

    private fun loadSessionList() {
        viewModelScope.launch(Dispatchers.IO) {
            val sessionDir = java.io.File(context.filesDir, "sessions").apply { mkdirs() }
            val files = sessionDir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()
            val list = files.mapNotNull { file ->
                runCatching {
                    val json = JSONObject(file.readText())
                    val id = json.getString("id")
                    val title = json.optString("title", "Session")
                    val timestamp = json.optLong("timestamp", file.lastModified())
                    val msgArr = json.getJSONArray("messages")
                    val msgList = mutableListOf<ChatMessage>()
                    for (i in 0 until msgArr.length()) {
                        val o = msgArr.getJSONObject(i)
                        msgList.add(ChatMessage(o.getLong("id"), SenderType.valueOf(o.getString("sender")), o.getString("text")))
                    }
                    ChatSession(id, title, timestamp, msgList)
                }.getOrNull()
            }.sortedByDescending { it.timestamp }
            _uiState.update { it.copy(sessions = list) }
        }
    }

    fun setEnablePersonaBackground(enabled: Boolean) {
        settingsRepository.updateSettings { it.copy(enablePersonaBackground = enabled) }
    }

    fun setShowThinkingProcess(enabled: Boolean) {
        settingsRepository.updateSettings { it.copy(showThinkingProcess = enabled) }
    }

    fun setGhostMode(enabled: Boolean) {
        settingsRepository.updateSettings { it.copy(isGhostMode = enabled) }
        if (enabled) {
            val currentId = _uiState.value.currentSessionId
            viewModelScope.launch(Dispatchers.IO) {
                val sessionDir = java.io.File(context.filesDir, "sessions")
                val file = java.io.File(sessionDir, "$currentId.json")
                if (file.exists()) file.delete()
                loadSessionList()
            }
        }
    }

    fun setHistoryLookupMode(mode: com.example.serenitydroid.data.HistoryLookupMode) {
        settingsRepository.updateSettings { it.copy(historyLookupMode = mode) }
    }

    fun dismissConversionOffer() { _uiState.update { it.copy(conversionOfferModel = null) } }
    fun saveHistory() { saveCurrentSession() }
}
