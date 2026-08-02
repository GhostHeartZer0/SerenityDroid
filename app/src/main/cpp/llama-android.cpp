#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <jni.h>
#include <string>
#include <vector>
#include <thread>
#include <unistd.h>
#include <unordered_map>
#include <mutex>
#include <sched.h>
#include <ctime>
#include "llama.cpp/include/llama.h"

#define TAG "LlamaAndroid"

#define ANDROID_LOG_DEBUG(msg) __android_log_print(ANDROID_LOG_DEBUG, TAG, "%s", msg)
#define ANDROID_LOG_ERROR(msg) __android_log_print(ANDROID_LOG_ERROR, TAG, "%s", msg)

// Global state TODO: refactor this to make it better
static struct llama_model *g_model = nullptr;
static struct llama_context *g_ctx = nullptr;
static struct llama_sampler *g_sampler = nullptr;
static bool g_is_done = false;
static bool g_backend_initialized = false;
static bool g_last_accel_state = false;
static std::string g_last_backend_name = "CPU";
static std::mutex g_mutex;

static void unload_model_internal() {
    if (g_sampler != nullptr) {
        llama_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    if (g_ctx != nullptr) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_is_done = true;
}

static void pin_to_perf_cores() {
    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    for (int i = 4; i <= 7; ++i) {
        CPU_SET(i, &cpuset);
    }
    if (sched_setaffinity(0, sizeof(cpu_set_t), &cpuset) != 0) {
        // Silently fail if not possible
    }
}

static void llama_android_log_callback(ggml_log_level level, const char * text, void * user_data) {
    (void) user_data;
    if (!text || text[0] == '\0') return;
    int android_level = ANDROID_LOG_INFO;
    if (level == GGML_LOG_LEVEL_WARN) android_level = ANDROID_LOG_WARN;
    else if (level == GGML_LOG_LEVEL_ERROR) android_level = ANDROID_LOG_ERROR;
    else if (level == GGML_LOG_LEVEL_DEBUG) android_level = ANDROID_LOG_DEBUG;
    __android_log_print(android_level, "LlamaNative", "%s", text);
}

extern "C" {

JNIEXPORT jint JNICALL
Java_com_example_serenitydroid_jni_LlamaAndroid_loadModel(
        JNIEnv *env,
        jobject /* this */,
        jstring model_path_jni,
        jint n_gpu_layers,
        jint n_context,
        jint n_threads,
        jstring mmproj_path_jni,
        jint kv_quant_k,
        jint kv_quant_v,
        jstring backend_hint_jni
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_model != nullptr) {
        ANDROID_LOG_DEBUG("Model already loaded, unloading first to release resources...");
        unload_model_internal();
    }

    // Handle backend hint
    const char *backend_hint = backend_hint_jni ? env->GetStringUTFChars(backend_hint_jni, nullptr) : nullptr;
    bool use_accel = (backend_hint && n_gpu_layers > 0 && (strcmp(backend_hint, "OpenCL") == 0 || strcmp(backend_hint, "HTP") == 0));

    llama_log_set(llama_android_log_callback, nullptr);

    // Hardware mode change check
    std::string current_hint = (backend_hint && n_gpu_layers > 0) ? backend_hint : "CPU";
    if (g_backend_initialized && (use_accel != g_last_accel_state || current_hint != g_last_backend_name)) {
        ANDROID_LOG_DEBUG("Hardware backend mode changed, deinitializing for clean state...");
        llama_backend_free();
        g_backend_initialized = false;
    }

    // Force re-init if backend was deinitialized or if we want to change environment
    if (!g_backend_initialized) {
        try {
            if (use_accel) {
                ANDROID_LOG_DEBUG((std::string("Initializing llama.cpp with acceleration: ") + current_hint).c_str());
            } else {
                ANDROID_LOG_DEBUG("Initializing llama.cpp (CPU)");
            }
            llama_backend_init();
            g_backend_initialized = true;
            g_last_accel_state = use_accel;
            g_last_backend_name = current_hint;
        } catch (const std::exception& e) {
            ANDROID_LOG_ERROR((std::string("CRITICAL: llama_backend_init failed with exception: ") + e.what()).c_str());
            if (use_accel) {
                ANDROID_LOG_DEBUG("Attempting emergency fallback to CPU mode...");
                llama_backend_init();
                g_backend_initialized = true;
                g_last_accel_state = false;
                g_last_backend_name = "CPU";
            } else {
                if (backend_hint) env->ReleaseStringUTFChars(backend_hint_jni, backend_hint);
                return -2;
            }
        } catch (...) {
            ANDROID_LOG_ERROR("CRITICAL: llama_backend_init failed with unknown exception");
            if (backend_hint) env->ReleaseStringUTFChars(backend_hint_jni, backend_hint);
            return -3;
        }
    }

    pin_to_perf_cores();

    struct llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = n_gpu_layers;
    mparams.load_mode = LLAMA_LOAD_MODE_MMAP;
    mparams.check_tensors = false;

    const char *model_path = env->GetStringUTFChars(model_path_jni, nullptr);
    g_model = llama_model_load_from_file(model_path, mparams);

    if (g_model == nullptr) {
        ANDROID_LOG_ERROR((std::string("Failed to load model from path: ") + model_path).c_str());
        env->ReleaseStringUTFChars(model_path_jni, model_path);
        if (backend_hint) env->ReleaseStringUTFChars(backend_hint_jni, backend_hint);
        return -1;
    }
    env->ReleaseStringUTFChars(model_path_jni, model_path);

    if (mmproj_path_jni != nullptr) {
        const char *mmproj_path = env->GetStringUTFChars(mmproj_path_jni, nullptr);
        ANDROID_LOG_DEBUG((std::string("MMProj Path: ") + mmproj_path).c_str());
        // TODO: clip_model_load(mmproj_path)
        env->ReleaseStringUTFChars(mmproj_path_jni, mmproj_path);
    }

    int hw_threads = (int)std::thread::hardware_concurrency();
    int default_threads = (hw_threads > 0 && hw_threads < 4) ? hw_threads : 4;
    struct llama_context_params lparams = llama_context_default_params();
    lparams.n_threads = n_threads > 0 ? n_threads : default_threads;
    lparams.n_threads_batch = lparams.n_threads;
    lparams.n_ctx = (uint32_t)n_context;
    lparams.type_k = (enum ggml_type)kv_quant_k;
    lparams.type_v = (enum ggml_type)kv_quant_v;

    ANDROID_LOG_DEBUG((std::string("Initializing context with n_ctx=") + std::to_string(lparams.n_ctx) +
                       ", n_threads=" + std::to_string(lparams.n_threads) +
                       ", n_gpu_layers=" + std::to_string(n_gpu_layers) +
                       ", type_k=" + std::to_string(kv_quant_k) +
                       ", type_v=" + std::to_string(kv_quant_v)).c_str());

    g_ctx = llama_init_from_model(g_model, lparams);

    if (backend_hint) env->ReleaseStringUTFChars(backend_hint_jni, backend_hint);

    if (g_ctx == nullptr) {
        ANDROID_LOG_ERROR("Failed to create context from model");
        llama_model_free(g_model);
        g_model = nullptr;
        return -1;
    }

    return 0;
}

JNIEXPORT void JNICALL
Java_com_example_serenitydroid_jni_LlamaAndroid_unloadModel(
        JNIEnv *env,
        jobject /* this */
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    ANDROID_LOG_DEBUG("Unloading model and freeing resources...");
    unload_model_internal();
}

JNIEXPORT void JNICALL
Java_com_example_serenitydroid_jni_LlamaAndroid_deinitBackend(
        JNIEnv *env,
        jobject /* this */
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    ANDROID_LOG_DEBUG("Deinitializing llama.cpp backend completely...");
    llama_backend_free();
    g_backend_initialized = false;
}

JNIEXPORT jboolean JNICALL
Java_com_example_serenitydroid_jni_LlamaAndroid_isModelLoaded(
        JNIEnv *env,
        jobject /* this */
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return (jboolean)(g_model != nullptr && g_ctx != nullptr);
}

JNIEXPORT jint JNICALL
Java_com_example_serenitydroid_jni_LlamaAndroid_getModelContextLength(
        JNIEnv *env,
        jobject /* this */
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx) {
        return (jint)llama_n_ctx(g_ctx);
    }
    return 0;
}

JNIEXPORT void JNICALL
Java_com_example_serenitydroid_jni_LlamaAndroid_completion_1init(
        JNIEnv *env,
        jobject /* this */,
        jstring prompt_jni,
        jfloat temp,
        jint top_k,
        jfloat top_p,
        jfloat min_p,
        jfloat penalty_repeat,
        jfloat penalty_freq,
        jfloat penalty_present
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model || !g_ctx) {
        g_is_done = true;
        return;
    }

    if (g_sampler != nullptr) {
        llama_sampler_free(g_sampler);
        g_sampler = nullptr;
    }

    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    const char *prompt = env->GetStringUTFChars(prompt_jni, nullptr);

    int n_prompt = -llama_tokenize(vocab, prompt, strlen(prompt), NULL, 0, false, true);
    if (n_prompt <= 0) n_prompt = 512;

    std::vector<llama_token> prompt_tokens(n_prompt);
    int n_tokens = llama_tokenize(vocab, prompt, strlen(prompt), prompt_tokens.data(), (int)prompt_tokens.size(), false, true);
    if (n_tokens < 0) n_tokens = 0;
    prompt_tokens.resize(n_tokens);

    env->ReleaseStringUTFChars(prompt_jni, prompt);

    auto sparams = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(g_sampler, llama_sampler_init_penalties(512, penalty_repeat, penalty_freq, penalty_present));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(top_k));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_min_p(min_p, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(temp));

    uint32_t seed = (temp > 0.0f) ? (uint32_t)time(NULL) : LLAMA_DEFAULT_SEED;
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(seed));

    if (!prompt_tokens.empty()) {
        llama_memory_clear(llama_get_memory(g_ctx), true);
        llama_batch batch = llama_batch_get_one(prompt_tokens.data(), (int32_t)prompt_tokens.size());
        if (llama_decode(g_ctx, batch) != 0) {
            ANDROID_LOG_ERROR("llama_decode prompt failed");
            g_is_done = true;
            return;
        }
    }
    g_is_done = false;
}

JNIEXPORT jstring JNICALL
Java_com_example_serenitydroid_jni_LlamaAndroid_completion_1loop(
        JNIEnv *env,
        jobject /* this */
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_is_done || !g_model || !g_ctx || !g_sampler) {
        return env->NewStringUTF("[EOS]");
    }

    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    llama_token new_token_id = llama_sampler_sample(g_sampler, g_ctx, -1);

    if (llama_vocab_is_eog(vocab, new_token_id)) {
        g_is_done = true;
        return env->NewStringUTF("[EOS]");
    }

    char buf[256];
    int n = llama_token_to_piece(vocab, new_token_id, buf, sizeof(buf), 0, true);
    std::string token_str;
    if (n > 0) {
        if (n <= (int)sizeof(buf)) {
            token_str = std::string(buf, n);
        } else {
            // Buffer was too small, allocate exactly what's needed
            std::vector<char> large_buf(n);
            llama_token_to_piece(vocab, new_token_id, large_buf.data(), n, 0, true);
            token_str = std::string(large_buf.data(), n);
        }
    }

    // Gemma/Gemma2/Llama3/Qwen specific stop tags - precise tag matching
    std::string lower_token = token_str;
    std::transform(lower_token.begin(), lower_token.end(), lower_token.begin(), ::tolower);
    if (lower_token.find("<end_of_turn>") != std::string::npos ||
        lower_token.find("<start_of_turn>") != std::string::npos ||
        lower_token.find("start_of_turn") != std::string::npos ||
        lower_token.find("<turn|") != std::string::npos ||
        lower_token.find("</s>") != std::string::npos ||
        lower_token.find("<|eot_id|>") != std::string::npos ||
        lower_token.find("<|eom_id|>") != std::string::npos ||
        lower_token.find("<|im_end|>") != std::string::npos ||
        lower_token.find("<|start_header_id|>") != std::string::npos) {
        g_is_done = true;
        return env->NewStringUTF("[EOS]");
    }

    llama_batch batch = llama_batch_get_one(&new_token_id, 1);
    if (llama_decode(g_ctx, batch) != 0) {
        g_is_done = true;
    }

    return env->NewStringUTF(token_str.c_str());
}

JNIEXPORT jint JNICALL
Java_com_example_serenitydroid_jni_LlamaAndroid_autoSelectLevelForPrompt(
        JNIEnv *env,
        jobject /* this */,
        jstring prompt_jni
) {
    return 1;
}

JNIEXPORT void JNICALL
Java_com_example_serenitydroid_jni_LlamaAndroid_initWakeWord(
        JNIEnv *env,
        jobject /* this */
) {
}

JNIEXPORT jboolean JNICALL
Java_com_example_serenitydroid_jni_LlamaAndroid_processAudioForWakeWord(
        JNIEnv *env,
        jobject /* this */,
        jshortArray audioData
) {
    (void)env;
    (void)audioData;
    return (jboolean)false;
}

JNIEXPORT jstring JNICALL
Java_com_example_serenitydroid_jni_LlamaAndroid_getBackendName(
        JNIEnv *env,
        jobject /* this */
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model) return env->NewStringUTF("NOT LOADED");

    bool has_opencl = false;
    bool has_htp = false;
    bool has_vulkan = false;

#ifdef GGML_USE_OPENCL
    has_opencl = true;
#endif
#ifdef GGML_USE_HEXAGON
    has_htp = true;
#endif
#ifdef GGML_USE_VULKAN
    has_vulkan = true;
#endif

    std::string backend_desc = "llama.cpp (CPU)";

    if (g_last_accel_state) {
        if (g_last_backend_name == "OpenCL" && has_opencl) {
            backend_desc = "llama.cpp (OpenCL GPU)";
        } else if (g_last_backend_name == "HTP" && has_htp) {
            backend_desc = "llama.cpp (Hexagon HTP NPU)";
        } else if (g_last_backend_name == "Vulkan" && has_vulkan) {
            backend_desc = "llama.cpp (Vulkan GPU)";
        } else {
            // Fallback description if the requested hint isn't actually compiled in
            if (has_opencl) backend_desc = "llama.cpp (OpenCL GPU Fallback)";
            else if (has_vulkan) backend_desc = "llama.cpp (Vulkan GPU Fallback)";
            else backend_desc = "llama.cpp (CPU - No Accelerator Compiled)";
        }
    }

    return env->NewStringUTF(backend_desc.c_str());
}

JNIEXPORT void JNICALL
Java_com_example_serenitydroid_jni_LlamaAndroid_setThreadAffinity(
        JNIEnv * /* env */,
        jobject /* this */
) {
    pin_to_perf_cores();
}

} // extern "C"
