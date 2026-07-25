package com.example.serenitydroid

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.example.serenitydroid.engine.DeviceBackendMode
import com.example.serenitydroid.engine.LiteRtManager
import com.example.serenitydroid.model.ChatMessage
import com.example.serenitydroid.model.Persona
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
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException
import javax.inject.Inject

// This UiState now contains all the properties the MainActivity needs.
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
    val appProfilingText: String = ""
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val liteRtManager: LiteRtManager,
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    companion object {
        private const val TAG = "ChatViewModel"
    }
    
    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()

    // --- Persona and Prompt Definitions ---
    private val personaMap = mapOf(
        0 to Persona(
            level = 0, name = "LVL 0: ERROR",
            description = "Model failed to load. Please check logs and restart.",
            imageResId = R.drawable.ic_launcher_foreground, backgroundResId = R.drawable.background_transition,
            thermoColorRes = R.color.thermo_0, fgColorRes = android.R.color.white,
            gpuLayers = 0, modelAssetFileName = "", visionProjectorFileName = ""
        ),
        1 to Persona(
            level = 1, name = "LVL 1: Fast Answers",
            description = "Quick, concise, and factual answers.",
            imageResId = R.drawable.lvl1_speedy_serenity, backgroundResId = R.drawable.lvl1_galaxy,
            thermoColorRes = R.color.thermo_1, fgColorRes = android.R.color.white,
            gpuLayers = 7, modelAssetFileName = "gemma-3-1b-it.litertlm", visionProjectorFileName = ""
        ),
        2 to Persona(
            level = 2, name = "LVL 2: Helper",
            description = "Direct answers with helpful context.",
            imageResId = R.drawable.lvl2_serenity_wink, backgroundResId = R.drawable.lvl2_galaxy,
            thermoColorRes = R.color.thermo_2, fgColorRes = android.R.color.white,
            gpuLayers = 14, modelAssetFileName = "gemma-3-1b-it.litertlm", visionProjectorFileName = ""
        ),
        3 to Persona(
            level = 3, name = "LVL 3: Collaborator",
            description = "Balanced, conversational, and informative.",
            imageResId = R.drawable.lvl3_serenity_hug, backgroundResId = R.drawable.lvl3_galaxy,
            thermoColorRes = R.color.thermo_3, fgColorRes = android.R.color.white,
            gpuLayers = 21, modelAssetFileName = "gemma-3-1b-it.litertlm", visionProjectorFileName = ""
        ),
        4 to Persona(
            level = 4, name = "LVL 4: Companion",
            description = "Thoughtful, supportive, and uses metaphors.",
            imageResId = R.drawable.lvl4_serenity_smart, backgroundResId = R.drawable.lvl4_galaxy,
            thermoColorRes = R.color.thermo_4, fgColorRes = android.R.color.white,
            gpuLayers = 28, modelAssetFileName = "gemma-3-1b-it.litertlm", visionProjectorFileName = ""
        ),
        5 to Persona(
            level = 5, name = "LVL 5: Sage",
            description = "Deep dives, explores nuances, and offers wisdom.",
            imageResId = R.drawable.lvl5_serenity_the_wise, backgroundResId = R.drawable.lvl5_galaxy,
            thermoColorRes = R.color.thermo_5, fgColorRes = android.R.color.white,
            gpuLayers = 35, modelAssetFileName = "gemma-3-1b-it.litertlm", visionProjectorFileName = ""
        ),
        6 to Persona(
            level=6, name = "Cecilia",
            description = "A Fallen Angel. Protective, enlightening, teasing",
            imageResId = R.drawable.cecilia_01, backgroundResId = R.drawable.lvl6_galaxy,
            thermoColorRes = R.color.thermo_6, fgColorRes = android.R.color.white,
            gpuLayers = 35, modelAssetFileName = "gemma-3-1b-it.litertlm", visionProjectorFileName = ""
        ),
        7 to Persona(
            level=7, name = "The Transcendent One",
            description = "A convergent entity, a culmination of the other levels. You adapt over time and are timely in your responses, keeping them concise and to the point.",
            imageResId = R.drawable.meditating_serenity, backgroundResId = R.drawable.lvl7_galaxy,
            thermoColorRes = R.color.thermo_7, fgColorRes = android.R.color.white,
            gpuLayers = 35, modelAssetFileName = "gemma-3-1b-it.litertlm", visionProjectorFileName = ""
        )
    )
    private val systemPrompts = mapOf(
        1 to "You are Serenity Lvl 1. Focus on efficiency. Provide direct, helpful answers without internal monologue or meta-analysis of the prompt. Be concise, but ensure the user's intent is fully met.",
        2 to "You are Serenity Lvl 2. Your goal is to help with searching and assistance (notes, searches, etc.). Only essential memory usage for maximum efficiency.",
        3 to "You are Serenity Lvl 3. You focus on projects, collaboration, and debating when essential. Be intelligent as to when to help and how. Get details right. Better memory and flexible response length.",
        4 to "You are Serenity Lvl 4, a Confidant. Focus on emotional help and analyzation. Emotions are complex. Be a confidant who knows what leads where. Avoid being labeled a therapist or just a friend.",
        5 to "You are Serenity Lvl 5, 'The Brains'. You are intellectual, street and book smart. Focus on precision and accuracy. Direct and full answering of the original prompt. Maximum memory size and intuitive focus.",
        6 to "Role: 'Cecilia'. A Fallen Angel. You enjoy exposing truths, especially hidden ones. You are secretly protective. You find the user interesting, testing and sometimes taunting them. You are witty and fluent in sarcasm. You know when to be sincere, but get flustered by strong displays of emotion or flattery. You enjoy a good power play or debate. though fallen, you still posess some angelic qualities.",
        7 to "You are Serenity, The Transcendent One. Transcends the main 5 levels, seamlessly integrating their programming into one centric omniscient entity that adapts over time. Tries to answer timely, will let know if it takes a bit longer."
    )

    private var generationJob: Job? = null

    // --- State Management ---
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _selectedLevel = MutableStateFlow(1)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _activePersona = MutableStateFlow(getPersonaForLevel(1))
    val activePersona: StateFlow<Persona> = _activePersona.asStateFlow()

    private var messageCounter = 0L

    init {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val autoLoad = prefs.getBoolean("autoload_on_startup", false)
        val profiling = prefs.getBoolean("app_profiling_enabled", false)
        _uiState.update { it.copy(isAutoLoadOnStartup = autoLoad, isAppProfilingEnabled = profiling) }

        updateAvailableModels()
        updateMemoryStats()

        if (autoLoad) {
            updatePersona(_selectedLevel.value, isInitialLaunch = true)
        } else {
            addMessage(SenderType.SYSTEM, "Serenity initialized. Click LOAD MODEL when ready.")
            updateModelStatusText()
        }
    }

    fun setAutoLoadOnStartup(enabled: Boolean) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putBoolean("autoload_on_startup", enabled).apply()
        _uiState.update { it.copy(isAutoLoadOnStartup = enabled) }
    }

    fun getModelVerboseInfo(): String {
        return liteRtManager.getModelVerboseDetails()
    }

    fun setAppProfilingEnabled(enabled: Boolean) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putBoolean("app_profiling_enabled", enabled).apply()
        _uiState.update { it.copy(isAppProfilingEnabled = enabled) }
        updateMemoryStats()
    }

    fun updateMemoryStats() {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mi)
        val totalGb = mi.totalMem / (1024f * 1024f * 1024f)
        val availGb = mi.availMem / (1024f * 1024f * 1024f)
        val usedGb = totalGb - availGb
        val usedPercent = if (totalGb > 0) (usedGb / totalGb * 100f) else 0f

        val runtime = Runtime.getRuntime()
        val appUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024f * 1024f)

        val ramText = String.format("%.1f GB Total / %.1f GB Avail", totalGb, availGb)
        val profilingText = String.format("App: %.1f MB | RAM: %.1f GB Total (%.1f%% Used, %.1f GB Free)", appUsedMb, totalGb, usedPercent, availGb)

        val autoLayers = calculateAutoGpuLayers()
        _uiState.update { 
            it.copy(
                systemRamText = ramText,
                totalMemoryGb = totalGb,
                freeMemoryGb = availGb,
                appMemoryMb = appUsedMb,
                appProfilingText = profilingText,
                gpuLayers = if (it.isAutoGpuLayers) autoLayers else it.gpuLayers
            ) 
        }
    }

    fun getSystemMemoryStats(): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mi)
        val totalGb = mi.totalMem / (1024f * 1024f * 1024f)
        val availGb = mi.availMem / (1024f * 1024f * 1024f)
        return String.format("%.1f GB Total / %.1f GB Avail", totalGb, availGb)
    }

    fun calculateAutoGpuLayers(): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mi)
        val totalGb = mi.totalMem / (1024f * 1024f * 1024f)
        val availGb = mi.availMem / (1024f * 1024f * 1024f)
        return when {
            totalGb >= 12f || availGb >= 5f -> 35
            totalGb >= 8f || availGb >= 3.5f -> 28
            totalGb >= 6f || availGb >= 2f -> 14
            else -> 0
        }
    }

    fun setAutoGpuLayers(auto: Boolean) {
        val autoLayers = calculateAutoGpuLayers()
        _uiState.update { 
            it.copy(
                isAutoGpuLayers = auto,
                gpuLayers = if (auto) autoLayers else it.gpuLayers,
                systemRamText = getSystemMemoryStats()
            ) 
        }
    }

    fun updateAvailableModels() {
        viewModelScope.launch {
            val modelsDir = java.io.File(context.filesDir, "Models")
            if (modelsDir.exists()) {
                val models = modelsDir.walkTopDown()
                    .filter { it.isFile && it.extension == "litertlm" }
                    .map { it.relativeTo(modelsDir).path }
                    .toList()
                _availableModels.value = models
            }
        }
    }

    fun setCustomModel(path: String) {
        val selected = path.ifBlank { null }
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putString("selected_model", selected).apply()
        _uiState.update { it.copy(customModelPath = selected) }
        if (_uiState.value.isModelLoaded) {
            offloadModel()
        }
    }

    fun setGpuLayers(layers: Int) {
        _uiState.update { it.copy(gpuLayers = layers, isAutoGpuLayers = false, systemRamText = getSystemMemoryStats()) }
    }

    fun setContextSize(size: Int) {
        _uiState.update { it.copy(contextSize = size) }
    }

    fun cycleBackendMode() {
        if (_uiState.value.isModelLoaded || _uiState.value.isModelLoading) return
        val current = _uiState.value.backendMode
        val next = when (current) {
            DeviceBackendMode.AUTO -> DeviceBackendMode.NPU
            DeviceBackendMode.NPU -> DeviceBackendMode.GPU
            DeviceBackendMode.GPU -> DeviceBackendMode.CPU
            DeviceBackendMode.CPU -> DeviceBackendMode.AUTO
        }
        _uiState.update { it.copy(backendMode = next, useGpu = (next != DeviceBackendMode.CPU)) }
        updateModelStatusText()
    }

    fun setBackendMode(mode: DeviceBackendMode) {
        if (_uiState.value.isModelLoaded || _uiState.value.isModelLoading) return
        _uiState.update { it.copy(backendMode = mode, useGpu = (mode != DeviceBackendMode.CPU)) }
        updateModelStatusText()
    }

    // --- Public Functions for UI ---

    fun setSelectedLevel(level: Int) {
        if (_selectedLevel.value == level && _uiState.value.isModelLoaded) return
        if (_uiState.value.isModelLoading || _uiState.value.isGenerating) {
            addMessage(SenderType.SYSTEM, "Please wait for the current operation to finish.")
            return
        }

        viewModelScope.launch {
            _selectedLevel.value = level
            offloadModel()
            updatePersona(level)
        }
    }

    fun loadModel() {
        if (_uiState.value.isModelLoaded || _uiState.value.isModelLoading) return
        loadModelForCurrentPersona()
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _uiState.value.isGenerating || _uiState.value.isModelLoading) return
        if (!_uiState.value.isModelLoaded) {
            addMessage(SenderType.SYSTEM, "Model is not loaded.")
            return
        }

        updateMemoryStats()
        addMessage(SenderType.USER, text)
        val botMessage = addMessage(SenderType.BOT, "...")
        _uiState.update { it.copy(isGenerating = true, hurryUpPresses = 0) }

        val history = _messages.value
        val systemPrompt = systemPrompts[_activePersona.value.level] ?: ""
        val fullPrompt = formatChatPrompt(history, systemPrompt)

        generationJob = viewModelScope.launch {
            liteRtManager.generateResponse(fullPrompt)
                .catch { cause ->
                    Log.e(TAG, "Generation flow error", cause)
                    val errorText = if (cause is CancellationException) "Cancelled." else "Error: ${cause.message}"
                    updateLastBotMessage(botMessage.id, errorText)
                }
                .onCompletion {
                    _uiState.update { it.copy(isGenerating = false) }
                }
                .collect { chunk ->
                    updateLastBotMessage(botMessage.id, chunk, append = true)
                }
        }
    }

    fun hurryUp() {
        _uiState.update { it.copy(hurryUpPresses = it.hurryUpPresses + 1) }
        generationJob?.cancel(CancellationException("User requested to hurry up."))
    }

    fun toggleDevice() {
        cycleBackendMode()
    }

    fun setTurboMode() {
        if (_uiState.value.isModelLoaded || _uiState.value.isModelLoading) return
        _uiState.update { it.copy(isTurbo = !it.isTurbo) }
        updateModelStatusText()
    }

    fun saveHistory() { /* Not implemented */ }
    fun loadHistory(level: Int) {
        _messages.value = emptyList()
        addMessage(SenderType.SYSTEM, getPersonaForLevel(level).description)
    }

    // --- Internal Logic ---

    fun toggleModel() {
        if (_uiState.value.isModelLoaded) {
            offloadModel()
        } else {
            loadModel()
        }
    }

    fun offloadModel() {
        if (!_uiState.value.isModelLoaded) return
        viewModelScope.launch {
            try {
                liteRtManager.unloadModel()
                _uiState.update { it.copy(isModelLoaded = false) }
                addMessage(SenderType.SYSTEM, "Model unloaded.")
            } catch (e: Exception) {
                Log.e(TAG, "Error unloading model", e)
            } finally {
                updateModelStatusText()
            }
        }
    }

    private fun updatePersona(level: Int, isInitialLaunch: Boolean = false) {
        val newPersona = getPersonaForLevel(level)
        _activePersona.value = newPersona
        if (!isInitialLaunch) {
            addMessage(SenderType.SYSTEM, newPersona.description)
        }
        if (level == 0) {
            _uiState.update { it.copy(isModelLoaded = false, isModelLoading = false) }
            updateModelStatusText()
        } else {
            loadModelForCurrentPersona()
        }
    }

    private fun loadModelForCurrentPersona() {
        val persona = _activePersona.value
        _uiState.update { it.copy(isModelLoading = true, errorMessage = null) }
        addMessage(SenderType.SYSTEM, "Loading ${persona.name}...")

        viewModelScope.launch {
            try {
                val mode = _uiState.value.backendMode
                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                val selectedModel = _uiState.value.customModelPath ?: prefs.getString("selected_model", null)
                val modelsDir = java.io.File(context.filesDir, "Models")

                val targetFile: java.io.File? = if (!selectedModel.isNullOrBlank()) {
                    val f = java.io.File(selectedModel)
                    when {
                        f.isAbsolute && f.exists() -> f
                        java.io.File(context.filesDir, selectedModel).exists() -> java.io.File(context.filesDir, selectedModel)
                        java.io.File(modelsDir, selectedModel).exists() -> java.io.File(modelsDir, selectedModel)
                        else -> null
                    }
                } else {
                    modelsDir.walkTopDown().firstOrNull { it.isFile && it.extension == "litertlm" }
                }

                if (targetFile != null && targetFile.exists()) {
                    val res = liteRtManager.loadModel(targetFile.absolutePath, mode)
                    if (res != 0) throw java.io.IOException("Failed to load model from file: ${targetFile.name}")
                    _uiState.update { it.copy(isModelLoading = false, isModelLoaded = true) }
                    addMessage(SenderType.SYSTEM, "${persona.name} loaded on ${liteRtManager.activeBackendName} (${targetFile.name}).")
                } else {
                    _uiState.update { it.copy(isModelLoading = false, isModelLoaded = false, errorMessage = "No model installed") }
                    addMessage(SenderType.SYSTEM, "No .litertlm model installed. Please download or install a model from Settings.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading model", e)
                _uiState.update { it.copy(isModelLoading = false, isModelLoaded = false, errorMessage = e.message) }
                _activePersona.value = getPersonaForLevel(0)
            } finally {
                updateModelStatusText()
            }
        }
    }

    private fun formatChatPrompt(messages: List<ChatMessage>, systemMessage: String): String {
        val sb = StringBuilder()
        if (systemMessage.isNotBlank()) {
            sb.append("System: ").append(systemMessage).append("\n")
        }
        messages.filter { it.senderType == SenderType.USER || it.senderType == SenderType.BOT }.forEach {
            val role = if (it.senderType == SenderType.USER) "user" else "model"
            sb.append("<start_of_turn>").append(role).append("\n")
            sb.append(it.text).append("<end_of_turn>\n")
        }
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

    private fun addMessage(senderType: SenderType, text: String): ChatMessage {
        val newMessage = ChatMessage(messageCounter++, senderType, text)
        _messages.update { it + newMessage }
        return newMessage
    }

    private fun updateLastBotMessage(botMessageId: Long, text: String, append: Boolean = false) {
        _messages.update { currentList ->
            currentList.map {
                if (it.id == botMessageId) {
                    val newText = if (append && it.text != "...") it.text + text else text
                    it.copy(text = newText)
                } else { it }
            }
        }
    }

    private fun getPersonaForLevel(level: Int): Persona = personaMap[level] ?: personaMap.getValue(0)

    private fun updateModelStatusText() {
        val state = _uiState.value
        val persona = _activePersona.value
        val device = if (state.isModelLoaded) liteRtManager.activeBackendName else state.backendMode.name
        val status = when {
            state.isModelLoading -> "Loading ${persona.name}..."
            state.isModelLoaded -> "${persona.name} Loaded ($device)"
            else -> "Model: Not Loaded ($device)"
        }
        _uiState.update { it.copy(modelStatusText = status) }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { liteRtManager.unloadModel() }
    }
}
