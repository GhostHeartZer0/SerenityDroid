package com.example.serenitydroid.model

enum class RuntimeOption(
    val displayName: String,
    val isChildOfGenieX: Boolean = false,
    val extensions: Set<String> = emptySet()
) {
    GENIEX_QAIRT(
        displayName = "GenieX - QAIRT",
        isChildOfGenieX = true,
        extensions = setOf("bin", "dlc", "so")
    ),
    GENIEX_LLAMA_CPP(
        displayName = "GenieX - llama.cpp",
        isChildOfGenieX = true,
        extensions = setOf("gguf", "bin")
    ),
    TFLITE(
        displayName = "TFLite",
        isChildOfGenieX = false,
        extensions = setOf("tflite", "litertlm", "task")
    ),
    QUALCOMM_AI_RUNTIME(
        displayName = "Qualcomm® AI Runtime",
        isChildOfGenieX = false,
        extensions = setOf("dlc", "bin", "so")
    ),
    ONNX_RUNTIME(
        displayName = "ONNX Runtime",
        isChildOfGenieX = false,
        extensions = setOf("onnx", "ort")
    ),
    QUALCOMM_VOICE_AI(
        displayName = "Qualcomm® Voice AI",
        isChildOfGenieX = false,
        extensions = setOf("wav", "tflite", "bin")
    );

    companion object {
        val ALL_LEAF_RUNTIMES = entries.toSet()
        val GENIEX_CHILDREN = entries.filter { it.isChildOfGenieX }.toSet()
    }
}
