package com.example.serenitydroid.data

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.example.serenitydroid.engine.DeviceBackendMode
import com.example.serenitydroid.model.RuntimeOption
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

enum class HistoryLookupMode {
    ALL,
    SESSION,
    NONE
}

data class SettingsData(
    val backendMode: DeviceBackendMode = DeviceBackendMode.AUTO,
    val isAutoLoadOnStartup: Boolean = false,
    val isAppProfilingEnabled: Boolean = false,
    val chatTextSize: Float = 14f,
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
    val gpuLayers: Int = 35,
    val isAutoGpuLayers: Boolean = true,
    val contextSize: Int = 4096,
    val customModelPath: String? = null,
    val waitOnFailure: Boolean = true,
    val retryDelayMs: Long = 1000L,
    val kvQuantK: String = "F16",
    val kvQuantV: String = "F16",
    val selectedRuntimes: Set<RuntimeOption> = RuntimeOption.ALL_LEAF_RUNTIMES,
    val preferredEngine: com.example.serenitydroid.engine.EngineSelection = com.example.serenitydroid.engine.EngineSelection.AUTO,
    val enableLvl6: Boolean = false,
    val enableLvl7: Boolean = false,
    val isStickyBottomScroll: Boolean = true,
    val enablePersonaBackground: Boolean = true,
    val showThinkingProcess: Boolean = true,
    val isGhostMode: Boolean = false,
    val historyLookupMode: HistoryLookupMode = HistoryLookupMode.SESSION
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    
    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<SettingsData> = _settingsFlow.asStateFlow()

    private fun loadSettings(): SettingsData {
        val selectedRuntimesStrings = prefs.getStringSet("selected_runtimes", null)
        val selectedRuntimes = selectedRuntimesStrings?.mapNotNull { 
            runCatching { RuntimeOption.valueOf(it) }.getOrNull() 
        }?.toSet() ?: RuntimeOption.ALL_LEAF_RUNTIMES

        val backendMode = runCatching { DeviceBackendMode.valueOf(prefs.getString("backend_mode", DeviceBackendMode.AUTO.name)!!) }.getOrDefault(DeviceBackendMode.AUTO)
        val engineStr = prefs.getString("preferred_engine", "AUTO") ?: "AUTO"
        val preferredEngine = try { com.example.serenitydroid.engine.EngineSelection.valueOf(engineStr) } catch (_: Exception) { com.example.serenitydroid.engine.EngineSelection.AUTO }
        val lookupStr = prefs.getString("history_lookup_mode", HistoryLookupMode.SESSION.name) ?: HistoryLookupMode.SESSION.name
        val historyLookupMode = try { HistoryLookupMode.valueOf(lookupStr) } catch (_: Exception) { HistoryLookupMode.SESSION }

        return SettingsData(
            backendMode = backendMode,
            isAutoLoadOnStartup = prefs.getBoolean("autoload_on_startup", false),
            isAppProfilingEnabled = prefs.getBoolean("app_profiling_enabled", false),
            chatTextSize = prefs.getFloat("chat_text_size", 14f),
            temperature = prefs.getFloat("temperature", 0.8f),
            topK = prefs.getInt("top_k", 40),
            topP = prefs.getFloat("top_p", 0.95f),
            minP = prefs.getFloat("min_p", 0.05f),
            frequencyPenalty = prefs.getFloat("frequency_penalty", 0.0f),
            presencePenalty = prefs.getFloat("presence_penalty", 0.0f),
            enableMtp = prefs.getBoolean("enable_mtp", false),
            enableSplitKv = prefs.getBoolean("enable_split_kv", true),
            enableAttentionCaching = prefs.getBoolean("enable_attention_caching", true),
            sequenceBatchSize = prefs.getInt("sequence_batch_size", 1),
            kvCacheSize = prefs.getInt("kv_cache_size", 2048),
            bufferSize = prefs.getInt("buffer_size", 1024),
            gpuLayers = prefs.getInt("gpu_layers", 35),
            isAutoGpuLayers = prefs.getBoolean("is_auto_gpu_layers", true),
            contextSize = prefs.getInt("context_size", 4096),
            customModelPath = prefs.getString("selected_model", null),
            waitOnFailure = prefs.getBoolean("wait_on_failure", true),
            retryDelayMs = prefs.getLong("retry_delay_ms", 1000L),
            kvQuantK = prefs.getString("kv_quant_k", "F16") ?: "F16",
            kvQuantV = prefs.getString("kv_quant_v", "F16") ?: "F16",
            selectedRuntimes = selectedRuntimes,
            preferredEngine = preferredEngine,
            enableLvl6 = prefs.getBoolean("enable_lvl_6", false),
            enableLvl7 = prefs.getBoolean("enable_lvl_7", false),
            isStickyBottomScroll = prefs.getBoolean("is_sticky_bottom_scroll", true),
            enablePersonaBackground = prefs.getBoolean("enable_persona_background", true),
            showThinkingProcess = prefs.getBoolean("show_thinking_process", true),
            isGhostMode = prefs.getBoolean("is_ghost_mode", false),
            historyLookupMode = historyLookupMode
        )
    }

    fun updateSettings(update: (SettingsData) -> SettingsData) {
        _settingsFlow.update { current ->
            val newData = update(current)
            saveSettings(newData)
            newData
        }
    }

    private fun saveSettings(data: SettingsData) {
        prefs.edit().apply {
            putString("backend_mode", data.backendMode.name)
            putString("preferred_engine", data.preferredEngine.name)
            putBoolean("autoload_on_startup", data.isAutoLoadOnStartup)
            putBoolean("app_profiling_enabled", data.isAppProfilingEnabled)
            putFloat("chat_text_size", data.chatTextSize)
            putFloat("temperature", data.temperature)
            putInt("top_k", data.topK)
            putFloat("top_p", data.topP)
            putFloat("min_p", data.minP)
            putFloat("frequency_penalty", data.frequencyPenalty)
            putFloat("presence_penalty", data.presencePenalty)
            putBoolean("enable_mtp", data.enableMtp)
            putBoolean("enable_split_kv", data.enableSplitKv)
            putBoolean("enable_attention_caching", data.enableAttentionCaching)
            putInt("sequence_batch_size", data.sequenceBatchSize)
            putInt("kv_cache_size", data.kvCacheSize)
            putInt("buffer_size", data.bufferSize)
            putInt("gpu_layers", data.gpuLayers)
            putBoolean("is_auto_gpu_layers", data.isAutoGpuLayers)
            putInt("context_size", data.contextSize)
            putString("selected_model", data.customModelPath)
            putBoolean("wait_on_failure", data.waitOnFailure)
            putLong("retry_delay_ms", data.retryDelayMs)
            putString("kv_quant_k", data.kvQuantK)
            putString("kv_quant_v", data.kvQuantV)
            putStringSet("selected_runtimes", data.selectedRuntimes.map { it.name }.toSet())
            putBoolean("enable_lvl_6", data.enableLvl6)
            putBoolean("enable_lvl_7", data.enableLvl7)
            putBoolean("is_sticky_bottom_scroll", data.isStickyBottomScroll)
            putBoolean("enable_persona_background", data.enablePersonaBackground)
            putBoolean("show_thinking_process", data.showThinkingProcess)
            putBoolean("is_ghost_mode", data.isGhostMode)
            putString("history_lookup_mode", data.historyLookupMode.name)
            apply()
        }
    }
}
