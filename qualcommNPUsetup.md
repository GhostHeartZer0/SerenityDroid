# Qualcomm® AI Hub & Google AI Edge LiteRT Official Grounding Documentation

This document grounds SerenityDroid's model installation, runtime engine dispatches, and hardware acceleration architecture directly in official documentation from **Google AI Edge (LiteRT)** and **Qualcomm® Technologies, Inc. (Qualcomm AI Hub)**.

---

## 1. Google AI Edge LiteRT-LM Android SDK

SerenityDroid uses the official Google LiteRT-LM SDK for on-device LLM inference and streaming execution on Android.

- **Official Docs**: [Google AI Edge LiteRT for Android](https://ai.google.dev/edge/litert/android) & [LiteRT Next Overview](https://developers.google.com/edge/litert/next/overview)
- **Maven Dependency**:
  ```kotlin
  implementation("com.google.ai.edge.litertlm:litertlm-android:1.0.0")
  ```
- **Native Runtime Libraries**:
  - `liblitertlm_jni.so` (JNI binding for LiteRT-LM C++ engine)
  - `liblitert_dispatch.so` (Native Hardware Dispatch Delegate for NPU/GPU offloading)

---

## 2. Qualcomm® AI Engine Direct Delegate & QNN Runtime

SerenityDroid integrates the official Qualcomm AI Engine Direct Delegate for hardware NPU acceleration on Snapdragon mobile platforms (SM8450 Snapdragon 8 Gen 1 up to SM8750 Snapdragon 8 Elite).

- **Official Docs**: [Qualcomm AI Engine Direct Delegate Setup](https://docs.qualcomm.com/bundle/publicresource/topics/80-63442-50/tflite_delegate.html)
- **Maven Dependencies**:
  ```kotlin
  implementation("com.qualcomm.qti:qnn-runtime:2.34.0")
  implementation("com.qualcomm.qti:qnn-litert-delegate:2.34.0")
  ```
- **Native NPU Shared Libraries**:
  - `libQnnHtp.so` (Hexagon Tensor Processor HTP NPU backend)
  - `libQnnHtpV75Skel.so` / `libQnnHtpV79Skel.so` (HTP hardware skeleton drivers)
  - `libQnnSystem.so` (Qualcomm AI Stack system driver)

### Official Delegate Initialization Pattern

```java
try {
    QnnDelegate.Options options = new QnnDelegate.Options();
    options.setBackendType(QnnDelegate.Options.BackendType.HTP_BACKEND);
    options.setSkelLibraryDir(activity.getApplicationInfo().nativeLibraryDir);
    qnnDelegate = new QnnDelegate(options);
    tfliteOptions.addDelegate(qnnDelegate);
} catch (UnsupportedOperationException e) {
    // Graceful fallback to GPU/CPU
}
```

---

## 3. Qualcomm® AI Hub GenieX Dual-Runtime Architecture

- **Official Docs**: [Qualcomm AI Hub Getting Started](https://aihub.qualcomm.com/get-started) & [Qualcomm AI Hub Android Sample Apps](https://github.com/quic/ai-hub-apps/tree/main/apps/android)

GenieX provides a single SDK with dual runtime plugins:

1. **GenieX - QAIRT Plugin**:
   - Executes pre-compiled `.litertlm`, `.bin`, and `.dlc` bundles compiled via Qualcomm AI Hub.
   - Routes model computation directly to Hexagon NPU via Qualcomm AI Runtime (QAIRT).

2. **GenieX - llama.cpp Plugin**:
   - Executes `.gguf` community models from Hugging Face on NPU (GGML HTP kernels), GPU (Adreno OpenCL), and CPU (ARM NEON).

---

## 4. Official Performance Comparison

Pre-optimized models on Qualcomm AI Hub (e.g. MobileNetV2, FFNet-40s) demonstrate significant latency improvements on Snapdragon NPU vs GPU and CPU:

| Snapdragon Device | NPU (Qualcomm HTP NPU) | GPU (Adreno GPUv2) | CPU (XNNPACK) |
|---|---|---|---|
| Samsung S25 (SM8750 Snapdragon 8 Elite) | **0.3 ms** | 1.8 ms | 2.8 ms |
| Samsung S24 (SM8650 Snapdragon 8 Gen 3) | **0.4 ms** | 2.3 ms | 3.6 ms |
| Samsung S23 (SM8550 Snapdragon 8 Gen 2) | **0.6 ms** | 2.7 ms | 4.1 ms |

---

## Official Documentation References

1. **Google AI Edge LiteRT Documentation**: https://ai.google.dev/edge/litert/android
2. **Qualcomm AI Hub Platform**: https://aihub.qualcomm.com/get-started
3. **Qualcomm AI Engine Direct Delegate Guide**: https://docs.qualcomm.com/bundle/publicresource/topics/80-63442-50/tflite_delegate.html
4. **Qualcomm AI Hub GitHub Repository**: https://github.com/quic/ai-hub-apps