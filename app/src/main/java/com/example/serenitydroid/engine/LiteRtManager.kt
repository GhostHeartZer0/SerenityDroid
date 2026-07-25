package com.example.serenitydroid.engine

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class DeviceBackendMode {
    AUTO,
    NPU,
    GPU,
    CPU
}

@Singleton
class LiteRtManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "LiteRtManager"

        init {
            try {
                System.loadLibrary("litertlm_jni")
                Log.i(TAG, "Successfully loaded liblitertlm_jni.so")
            } catch (e: Throwable) {
                Log.w(TAG, "Native load litertlm_jni: ${e.message}")
                try {
                    System.loadLibrary("litertlm")
                    Log.i(TAG, "Successfully loaded liblitertlm.so")
                } catch (e2: Throwable) {
                    Log.w(TAG, "Native load litertlm: ${e2.message}")
                }
            }
        }
    }

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var currentModelPath: String? = null
    var activeBackendName: String = "NOT LOADED"
        private set

    val isModelLoaded: Boolean
        get() = engine != null && conversation != null

    suspend fun loadModel(modelPath: String, backendMode: DeviceBackendMode = DeviceBackendMode.AUTO): Int = withContext(Dispatchers.IO) {
        unloadModelInternal()

        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val isQualcommModel = modelPath.contains("qualcomm", ignoreCase = true) || modelPath.contains("sm8750", ignoreCase = true)

        val backendsToTry = when {
            isQualcommModel -> listOf(
                "NPU (Qualcomm SM8750 HTP)" to Backend.NPU(nativeLibraryDir = nativeLibDir),
                "GPU" to Backend.GPU(),
                "CPU" to Backend.CPU()
            )
            backendMode == DeviceBackendMode.NPU -> listOf(
                "NPU" to Backend.NPU(nativeLibraryDir = nativeLibDir),
                "GPU" to Backend.GPU(),
                "CPU" to Backend.CPU()
            )
            backendMode == DeviceBackendMode.GPU -> listOf(
                "GPU" to Backend.GPU(),
                "CPU" to Backend.CPU()
            )
            backendMode == DeviceBackendMode.CPU -> listOf(
                "CPU" to Backend.CPU()
            )
            else -> listOf(
                "NPU" to Backend.NPU(nativeLibraryDir = nativeLibDir),
                "GPU" to Backend.GPU(),
                "CPU" to Backend.CPU()
            )
        }

        val modelFile = File(modelPath)
        if (!modelFile.exists() || !modelFile.canRead() || modelFile.length() == 0L) {
            Log.e(TAG, "Model file invalid or missing: $modelPath")
            activeBackendName = "INVALID FILE"
            return@withContext -1
        }

        var lastError: Throwable? = null
        for ((name, backend) in backendsToTry) {
            try {
                Log.d(TAG, "Attempting to initialize LiteRT-LM Engine with backend: $name")
                val config = EngineConfig(
                    modelPath = modelPath,
                    backend = backend,
                    cacheDir = context.cacheDir.path
                )

                val newEngine = Engine(config)
                newEngine.initialize()

                val conversationConfig = ConversationConfig()
                val newConversation = newEngine.createConversation(conversationConfig)

                engine = newEngine
                conversation = newConversation
                currentModelPath = modelPath
                activeBackendName = name
                Log.i(TAG, "Successfully initialized LiteRT-LM Engine on backend: $name")
                return@withContext 0
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to initialize backend $name: ${e.message}")
                lastError = e
            }
        }

        activeBackendName = "FAILED"
        lastError?.printStackTrace()
        -1
    }

    fun getModelVerboseDetails(): String {
        val path = currentModelPath ?: return "No model loaded."
        val file = File(path)
        if (!file.exists()) return "Model file missing: $path"
        val sizeMb = file.length() / (1024f * 1024f)
        val sizeGb = sizeMb / 1024f
        return String.format(
            "File: %s\nPath: %s\nSize: %.2f MB (%.2f GB)\nBackend: %s\nEngine State: %s",
            file.name,
            file.absolutePath,
            sizeMb,
            sizeGb,
            activeBackendName,
            if (isModelLoaded) "ACTIVE & READY" else "NOT READY"
        )
    }

    suspend fun loadModelFromAsset(assetFileName: String, backendMode: DeviceBackendMode = DeviceBackendMode.AUTO) {
        val modelFile = copyAssetToStorage(assetFileName)
        val result = loadModel(modelFile.absolutePath, backendMode)
        if (result != 0) {
            throw IOException("Failed to load model from asset: $assetFileName")
        }
    }

    suspend fun unloadModel() = withContext(Dispatchers.IO) {
        unloadModelInternal()
    }

    private fun unloadModelInternal() {
        try {
            conversation?.close()
            engine?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            conversation = null
            engine = null
            currentModelPath = null
            activeBackendName = "NOT LOADED"
        }
    }

    fun generateResponse(prompt: String): Flow<String> = callbackFlow {
        val activeConversation = conversation
        if (activeConversation == null) {
            close(IllegalStateException("Model engine conversation is not loaded."))
            return@callbackFlow
        }

        val flow = activeConversation.sendMessageAsync(prompt)
        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                flow.collect { message ->
                    trySend(message.toString())
                }
                close()
            } catch (e: Exception) {
                close(e)
            }
        }

        awaitClose {
            job.cancel()
        }
    }

    private fun copyAssetToStorage(assetName: String): File {
        val file = File(context.filesDir, assetName)
        if (file.exists() && file.length() > 0) {
            return file
        }

        context.assets.open(assetName).use { inputStream ->
            FileOutputStream(file).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        return file
    }
}
