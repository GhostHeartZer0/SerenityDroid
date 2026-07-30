# Qualcomm NPU Dispatch Setup Guide (JIT/On-Device Execution)

SerenityDroid uses **JIT (On-Device)** execution to allow dynamic loading and optimization of AI models on Snapdragon hardware (SM8750/SM8650).

## 1. Required Archives
You must obtain the LiteRT NPU runtime libraries using Google's official fetch scripts.
- **Target Archive**: `litert_npu_runtime_libraries_jit.zip`
- **Fetching Script**: 
  ```bash
  ./litert_npu_runtime_libraries/fetch_qualcomm_library.sh
  ```
  *(Target specifically for JIT dynamically)*

## 2. Essential Shared Libraries (.so)
Place these files in `app/src/main/jniLibs/arm64-v8a/`:

| Library Name | Description | Required For |
|---|---|---|
| `liblitert_dispatch.so` | Main Hardware Dispatch Bridge | All NPU Offloading |
| `liblitert_npu_compiler.so` | NPU Graph Compiler | **JIT Execution** |
| `libQnnHtpV79Skel.so` | Hexagon HTP Skeleton Driver | Snapdragon 8 Gen 3/Elite |
| `libQnnSystem.so` | Qualcomm AI Stack System Driver | Discovery |

*Note: Ensure the `Skel` version (V75, V79, V81) matches your device's Hexagon processor version.*

## 3. Environment Configuration
If the engine fails to register the accelerator despite libraries being present, you may need to set the following on the device shell:
```bash
export ADSP_LIBRARY_PATH="/data/app/~~.../lib/arm64; /vendor/lib/rfsa/adsp"
```
SerenityDroid attempts to automate this by setting the `DispatchLibraryDir` to the app's internal `nativeLibraryDir`.

## 4. Model Conversion Note
Ensure your `.litertlm` models are converted with the `--target NPU` flag to include the necessary metadata for the Qualcomm delegate.

## 5. Verification
Check Logcat for:
`Applied NPU dispatch directory using: setDispatchLibraryDir`
`Successfully pre-loaded NPU dispatch/QNN libraries`
`NPU accelerator registered.`
