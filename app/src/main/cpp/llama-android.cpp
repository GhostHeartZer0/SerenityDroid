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

// Global state for simplicity in this example. In a real app, you'd manage this better.
static struct llama_model *g_model = nullptr;
static struct llama_context *g_ctx = nullptr;
static struct llama_sampler *g_sampler = nullptr;
static bool g_is_done = false;
static std::mutex g_mutex;
static std::string g_generated_token;
static bool g_has_token = false;

static void pin_to_perf_cores() {
    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    for (int i = 4; i <= 7; ++i) {
        CPU_SET(i, &cpuset);
    }
    if (sched_setaffinity(0, sizeof(cpu_set_t), &cpuset) != 0) {
        // Silently fail if not possible, but log once
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

extern "C" JNIEXPORT jint JNICALL
Java_com_example_serenityai_jni_LlamaAndroid_loadModel(
        JNIEnv *env,
        jobject /* this */,
        jstring model_path_jni,
        jint n_gpu_layers,
        jint n_context,
        jint n_threads,
        jstring mmproj_path_jni,
        jint kv_quant_k,
        jint kv_quant_v
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_model != nullptr) {
        ANDROID_LOG_DEBUG("Model already loaded, unloading first...");
    }

    llama_log_set(llama_android_log_callback, nullptr);
    llama_backend_init();

    pin_to_perf_cores();

    struct llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = n_gpu_layers;
    mparams.load_mode = LLAMA_LOAD_MODE_MMAP; // Ensure weights are not in JVM heap
    mparams.check_tensors = false; // Speed up loading

    const char *model_path = env->GetStringUTFChars(model_path_jni, 0);
    g_model = llama_model_load_from_file(model_path, mparams);

    if (g_model == nullptr) {
        ANDROID_LOG_ERROR((std::string("Failed to load model from path: ") + model_path).c_str());
        env->ReleaseStringUTFChars(model_path_jni, model_path);
        return -1;
    }
    env->ReleaseStringUTFChars(model_path_jni, model_path);

    if (mmproj_path_jni != nullptr) {
        const char *mmproj_path = env->GetStringUTFChars(mmproj_path_jni, 0);
        ANDROID_LOG_DEBUG((std::string("MMProj Path: ") + mmproj_path).c_str());
        // TODO: clip_model_load(mmproj_path)
        env->ReleaseStringUTFChars(mmproj_path_jni, mmproj_path);
    }

    struct llama_context_params lparams = llama_context_default_params();
    lparams.n_ctx = n_context > 0 ? n_context : 2048;
    lparams.n_threads = n_threads > 0 ? n_threads : std::thread::hardware_concurrency();
    lparams.n_threads_batch = lparams.n_threads;
    lparams.type_k = (enum ggml_type)kv_quant_k;
    lparams.type_v = (enum ggml_type)kv_quant_v;

    ANDROID_LOG_DEBUG((std::string("Initializing context with n_ctx=") + std::to_string(lparams.n_ctx) +
                       ", n_threads=" + std::to_string(lparams.n_threads) +
                       ", n_gpu_layers=" + std::to_string(n_gpu_layers) +
                       ", type_k=" + std::to_string(kv_quant_k) +
                       ", type_v=" + std::to_string(kv_quant_v)).c_str());

    g_ctx = llama_init_from_model(g_model, lparams);

    if (g_ctx == nullptr) {
        ANDROID_LOG_ERROR("Failed to create context from model");
        llama_model_free(g_model);
        g_model = nullptr;
        return -1;
    }

    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_serenityai_jni_LlamaAndroid_unloadModel(
        JNIEnv *env,
        jobject /* this */
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    ANDROID_LOG_DEBUG("Unloading model and freeing backend...");
    if (g_sampler != nullptr) {
        llama_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    g_is_done = true;
    if (g_ctx != nullptr) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    llama_backend_free();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_serenityai_jni_LlamaAndroid_isModelLoaded(
        JNIEnv *env,
        jobject /* this */
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return g_model != nullptr && g_ctx != nullptr;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_serenityai_jni_LlamaAndroid_getModelContextLength(
        JNIEnv *env,
        jobject /* this */
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx) {
        return (jint)llama_n_ctx(g_ctx);
    }
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_serenityai_jni_LlamaAndroid_completion_1init(
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

    pin_to_perf_cores();

    if (g_sampler != nullptr) {
        llama_sampler_free(g_sampler);
        g_sampler = nullptr;
    }

    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    const char *prompt = env->GetStringUTFChars(prompt_jni, 0);

    int n_prompt = -llama_tokenize(vocab, prompt, strlen(prompt), NULL, 0, true, true);
    if (n_prompt <= 0) n_prompt = 512;

    std::vector<llama_token> prompt_tokens(n_prompt);
    int n_tokens = llama_tokenize(vocab, prompt, strlen(prompt), prompt_tokens.data(), prompt_tokens.size(), true, true);
    if (n_tokens < 0) n_tokens = 0;
    prompt_tokens.resize(n_tokens);

    env->ReleaseStringUTFChars(prompt_jni, prompt);

    auto sparams = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(sparams);
    // Expand penalty window from 64 to 512 to avoid over-biasing toward EOS
    llama_sampler_chain_add(g_sampler, llama_sampler_init_penalties(512, penalty_repeat, penalty_freq, penalty_present));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(top_k));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_min_p(min_p, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(temp));

    // Use dynamic seed if temperature is > 0 to ensure varied responses
    uint32_t seed = (temp > 0.0f) ? (uint32_t)time(NULL) : LLAMA_DEFAULT_SEED;
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(seed));

    if (!prompt_tokens.empty()) {
        llama_memory_clear(llama_get_memory(g_ctx), true);
        llama_batch batch = llama_batch_get_one(prompt_tokens.data(), prompt_tokens.size());
        if (llama_decode(g_ctx, batch) != 0) {
            ANDROID_LOG_ERROR("llama_decode prompt failed");
            g_is_done = true;
            return;
        }
    }
    g_is_done = false;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_serenityai_jni_LlamaAndroid_completion_1loop(
        JNIEnv *env,
        jobject /* this */
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_is_done || !g_model || !g_ctx || !g_sampler) {
        return env->NewStringUTF("[EOS]");
    }

    pin_to_perf_cores();

    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    llama_token new_token_id = llama_sampler_sample(g_sampler, g_ctx, -1);

    if (llama_vocab_is_eog(vocab, new_token_id)) {
        g_is_done = true;
        return env->NewStringUTF("[EOS]");
    }

    char buf[256];
    int n = llama_token_to_piece(vocab, new_token_id, buf, sizeof(buf), 0, true);
    std::string token_str = (n > 0) ? std::string(buf, n) : "";

    if (token_str.find("<end_of_turn>") != std::string::npos) {
        g_is_done = true;
        return env->NewStringUTF("[EOS]");
    }

    llama_batch batch = llama_batch_get_one(&new_token_id, 1);
    if (llama_decode(g_ctx, batch) != 0) {
        g_is_done = true;
    }

    return env->NewStringUTF(token_str.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_serenityai_jni_LlamaAndroid_autoSelectLevelForPrompt(
        JNIEnv *env,
        jobject /* this */,
        jstring prompt_jni
) {
    return 1;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_serenityai_jni_LlamaAndroid_initWakeWord(
        JNIEnv *env,
        jobject /* this */
) {
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_serenityai_jni_LlamaAndroid_processAudioForWakeWord(
        JNIEnv *env,
        jobject /* this */,
        jshortArray audioData
) {
    (void)env;
    (void)audioData;
    return false;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_serenityai_jni_LlamaAndroid_setThreadAffinity(
        JNIEnv * /* env */,
        jobject /* this */
) {
    pin_to_perf_cores();
}
