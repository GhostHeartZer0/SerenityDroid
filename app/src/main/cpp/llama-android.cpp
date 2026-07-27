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
#include "llama.cpp/include/llama.h"

#define TAG "LlamaAndroid"

#define ANDROID_LOG_DEBUG(msg) __android_log_print(ANDROID_LOG_DEBUG, TAG, "%s", msg)
#define ANDROID_LOG_ERROR(msg) __android_log_print(ANDROID_LOG_ERROR, TAG, "%s", msg)

// Global state for simplicity in this example. In a real app, you'd manage this better.
static struct llama_model *g_model = nullptr;
static struct llama_context *g_ctx = nullptr;
static std::mutex g_mutex;
static std::string g_generated_token;
static bool g_has_token = false;

extern "C" JNIEXPORT jint JNICALL
Java_com_example_serenityai_jni_LlamaAndroid_loadModel(
        JNIEnv *env,
        jobject /* this */,
        jstring model_path_jni,
        jint n_gpu_layers,
        jstring mmproj_path_jni
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_model != nullptr) {
        return 0; // Already loaded
    }

    llama_backend_init();

    struct llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = n_gpu_layers;

    const char *model_path = env->GetStringUTFChars(model_path_jni, 0);
    g_model = llama_model_load_from_file(model_path, mparams);
    env->ReleaseStringUTFChars(model_path_jni, model_path);

    if (mmproj_path_jni != nullptr) {
        const char *mmproj_path = env->GetStringUTFChars(mmproj_path_jni, 0);
        ANDROID_LOG_DEBUG((std::string("MMProj Path: ") + mmproj_path).c_str());
        // TODO: clip_model_load(mmproj_path)
        env->ReleaseStringUTFChars(mmproj_path_jni, mmproj_path);
    }

    if (g_model == nullptr) {
        ANDROID_LOG_ERROR("Failed to load model");
        return -1;
    }

    struct llama_context_params lparams = llama_context_default_params();
    // Configure context params here (e.g., context size)
    g_ctx = llama_init_from_model(g_model, lparams);

    if (g_ctx == nullptr) {
        ANDROID_LOG_ERROR("Failed to create context");
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

// Dummy implementation for completion
extern "C" JNIEXPORT void JNICALL
Java_com_example_serenityai_jni_LlamaAndroid_completion_1init(
        JNIEnv *env,
        jobject /* this */,
        jstring prompt_jni
) {
   (void)env;
   (void)prompt_jni;
   // Setup generation
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_serenityai_jni_LlamaAndroid_completion_1loop(
        JNIEnv *env,
        jobject /* this */
) {
   // Return next token
   return env->NewStringUTF("[EOS]");
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
