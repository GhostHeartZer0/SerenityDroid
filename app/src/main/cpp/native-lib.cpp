#include <jni.h>
#include <string>
#include "llama.cpp/include/llama.h" // This is the header from the submodule

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_serenitythetravelguide_LlamaEngine_loadModel(
        JNIEnv* env,
        jobject /* this */,
        jstring model_path) {

    const char *path = env->GetStringUTFChars(model_path, nullptr);

    // Here is where we will eventually initialize the llama_context
    // For now, we're just checking if the path is valid
    bool success = (path != nullptr);

    env->ReleaseStringUTFChars(model_path, path);
    return success;
}//
// Created by ccrg6 on 4/6/2026.
//
