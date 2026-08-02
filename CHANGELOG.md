# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## Version 1.0.0 - TBD
Stable release

## Version 0.9.0 - TBD
Clear TODO list

## Version 0.8.0 - TBD
Resolve multimodal and MTP
Fully fix the thought channel

## Version 0.7.1 - 2026-08-02
- Updated lvl 6 persona to the correct one, from SerenityPC
- excluded library components from constraints

## Version 0.7.0 - 2026-08-01
**Resolved engine support:**
- **Build & Submodule Restoration Diagnosis**:
  - Diagnosed `CMake` build failure (`CMakeLists.txt` missing in `app/src/main/cpp/llama.cpp`). Wiped by prior `.gitignore` untrack commit (`69b6cbb4`) and branch switch.
- NPU (Genie X & llama.cpp)
- GPU (OpenCL & Genie X)
- CPU (llama.cpp & Genie X)
**Cleaned up tags and output**
- Fixed regex and start/stop tag cleaning and an issue where the model would persist tags or responses.
- Removed unused `ai-hub-apps` directory and cleaned up `.gitignore`.

- **Data Escape & Ghost Mode Storage Patch**:
  - Enhanced `stripThoughtBlocks` in `InferenceManager.kt` to filter trailing unclosed `<think>` / `<thought>` tags and leading orphan closing tags from multi-turn context.
  - Implemented dynamic session disk purge in `ChatViewModel.kt` when `isGhostMode` is toggled `true` mid-session.

## Version 0.6.4 - 2026-08-01

- **Gemma BOS Token & Control Tag Leakage Fix**:
  - Added `<bos>` token prefix to `applyChatTemplate` in [InferenceManager.kt](file:///s:/AndroidStudioProjects/SerenityDroid/app/src/main/java/com/example/serenitydroid/engine/InferenceManager.kt) so Gemma models align turn boundaries in `llama.cpp`.
  - Fixed `CONTROL_TAG_REGEX` regex in `InferenceManager.kt` to avoid stripping `<s` from `<start_of_turn>` (resolving `tart_of_` leakage).
  - Added `start_of_turn` fallback checks to `isStopTag` in `InferenceManager.kt` and native `completion_loop` in [llama-android.cpp](file:///s:/AndroidStudioProjects/SerenityDroid/app/src/main/cpp/llama-android.cpp).
- **Chat History Contamination & Prompt Repetition Fix**:
  - Sanitized control tags and orphan role headers (`user\n`, `system\n`, `model\n`) in `InferenceManager.kt` (`CONTROL_TAG_REGEX`, `stripControlTags`, `applyChatTemplate`) to prevent model output leakage from polluting context memory in multi-turn chats.
  - Fixed typo (`art_of`) in stop tag patterns and made stop tag detection (`isStopTag` and C++ `completion_loop` in `llama-android.cpp`) precise to turn boundaries (`<start_of_turn>`, `<end_of_turn>`, `<|eot_id|>`, etc.).
  - Added `resetConversation()` to `InferenceManager.kt` and wired it into `ChatViewModel.kt` (`clearCurrentSession`, `createNewSession`, `loadSession`, `setSelectedLevel`) to reset LiteRT-LM conversation state when switching or clearing chats.
  - Fixed Jinja chat template newline escaping in LiteRT-LM reflection overrides.
- **Thinking Channel Separation & Parsing**:
  - Stopped `InferenceManager.kt` (`CONTROL_TAG_REGEX`) from stripping `<think>`/`</think>` and `<thought>`/`</thought>` tags during token streaming so reasoning tokens reach UI state.
  - Expanded `SerenityHomeScreen.kt` thought parser to handle both `<think>` and `<thought>` tag formats for collapsible "Thought Process" cards.
  - Added `stripThoughtBlocks` in `applyChatTemplate` to strip scratchpad thinking blocks from assistant history turns, preserving context window tokens and avoiding reasoning recursion.
- **Ghost Mode (Ephemeral Session)**: Added toggle in `SettingsScreen.kt` and `SettingsRepository.kt` (`isGhostMode`) preventing session and message JSON persistence to disk when enabled.
- **History Lookup Mode (ALL, SESSION, NONE)**: Added chip selector in `SettingsScreen.kt` and state handling in `ChatViewModel.kt` (`historyLookupMode`) allowing users to restrict prompt context history to ALL, SESSION only, or NONE (zero past turns).
- **GenieX Null Result Filtering & Configurable Max Tokens**: Enhanced `GenieXSdk` stream collection in `InferenceManager.kt` to handle null/empty token payloads cleanly and wired dynamic `maxTokens` configuration from `ModelConfig` into `GenerationConfig`.
- **Persona Prompt Refinements & Response Window Control**: Refined Level 1-7 system prompts in `ChatViewModel.kt` to keep responses structured and mobile-friendly (Level 1 fast/concise, Level 3 collaborative/non-bloated, Level 5 analytical/decisive). Added TODOs for Level 2 web/local search support and Level 7 adaptive cross-level learning.

## Version 0.6.3 - 2026-07-31

- **Control Tag Cutoff Resolution**: Implemented partial tag hold-back buffer (`PARTIAL_TAG_REGEX`) and expanded stop tag helper (`isStopTag`) in [InferenceManager.kt](file:///s:/AndroidStudioProjects/SerenityDroid/app/src/main/java/com/example/serenitydroid/engine/InferenceManager.kt) to hold back unclosed trailing tag prefixes (`<end_of_`, `<start_of_`, `<art_of_`) during streaming, preventing split tokens from leaking to the UI before stream termination.
- **Native Fragment Stop Detection**: Updated `completion_loop` in [llama-android.cpp](file:///s:/AndroidStudioProjects/SerenityDroid/app/src/main/cpp/llama-android.cpp) to case-insensitively intercept fragment stop tokens (`end_of`, `start_of`, `art_of`, `of_turn`, `turn|`, `</s>`, `eot_id`, `im_end`).

- **OpenCL Linker Fix**: Added stub implementation `libOpenCL_stub.c` to satisfy linker and conditionally built it in `CMakeLists.txt` for `llama.cpp`. Configured `build.gradle.kts` to exclude dummy `libOpenCL.so` from APK to use device system library at runtime.
- **Unified Hardware Acceleration**: Refactored JNI backend (`g_last_accel_state`) to support NPU (Hexagon/HTP) alongside OpenCL. Status reporting dynamically identifies `llama.cpp (Hardware Accelerated)`.
- **NPU (Hexagon/HTP) Readiness**: Set `-DGGML_HEXAGON=ON` and packaged `libggml-htp-*.so` libraries for Snapdragon 8 Elite/Gen 3 native kernels.
- **Full NPU & GPU Fallback Cascade**:
  - **Genie X QAIRT Plugin Handling**: Configured `loadGenieModel` to set `runtime_id = "qairt"` (or `GenieXSdk.PLUGIN_ID_QAIRT`) and zero out `nCtx` & `nGpuLayers` for `.bin`, `.dlc`, and `.zip` Qualcomm AI Hub bundles per official specifications.
  - **Genie X OpenCL GPU Offloading**: Re-enabled `ComputeUnitValue.GPU` in `loadGenieModel` for GPU backend mode requests.
  - **Native llama.cpp HTP & OpenCL Cascade**: Updated `loadLlamaModel` and `Smart Select` cascade logic in `InferenceManager.kt` to iterate cleanly across **Genie X NPU** -> **Native llama.cpp HTP (NPU)** -> **Genie X GPU (OpenCL)** -> **Native llama.cpp OpenCL (GPU)** -> **CPU Fallback**.
- **Hexagon SDK Path Resolution**: Updated `-DHEXAGON_SDK_ROOT` in `app/build.gradle.kts` to dynamically evaluate `${System.getenv("HEXAGON_SDK_ROOT")}` or fall back to installed Qualcomm AI Stack path `C:/Qualcomm/AIStack/QAIRT/2.31.0.250130`.

## Version 0.6.2 - 2026-07-31

- **NPU & GPU Acceleration Alignment**:
  - **NPU via Genie X (llama.cpp)**: Routed GGUF model execution on NPU to Qualcomm Genie X SDK (`GenieXSdk.PLUGIN_ID_LLAMA_CPP`), leveraging Hexagon 8 Tensor Processor (HTP) hardware acceleration.
  - **GPU via OpenCL (llama.cpp)**: Configured GPU acceleration for `llama.cpp` using native OpenCL backend via JNI (`serenity_llama`), explicitly disabling Vulkan (`GGML_VULKAN=OFF`) to ensure Adreno GPU stability.
  - **Dynamic Layer Offloading**: Implemented real-time system RAM checks upon loading to dynamically set offload layer targets (16, 32, or 999/full offload) for Genie X NPU/GPU execution.
  - **OpenCL GPU Auto Routing**: Fixed `LLAMA_CPP` engine and `AUTO` backend mode in `InferenceManager.kt` which previously hardcoded `requestedLayers = 0` and `backendHint = "CPU"`. `AUTO` mode now attempts OpenCL GPU acceleration first (`requestedLayers = 999`, `backendHint = "OpenCL"`), falling back to CPU only if GPU initialization fails.
  - **Dual Runtime Support**: Retained LiteRT-LM for `.litertlm` and `.tflite` model formats as a secondary execution engine alongside `llama.cpp`.

## Version 0.6.1 - 2026-07-31

- **OpenCL GPU Backend Migration**: Switched `llama.cpp` GPU build backend from Vulkan (`-DGGML_VULKAN=OFF`) to OpenCL (`-DGGML_OPENCL=ON`) in `app/build.gradle.kts` and updated JNI backend hints in `llama-android.cpp` and `InferenceManager.kt` to resolve Adreno driver instability and Vulkan shader crashes.

- **Accurate Backend Status Reporting**: Replaced hardcoded `#if defined(GGML_USE_VULKAN)` macro check in `llama-android.cpp`'s `getBackendName()` with runtime `g_last_vulkan_state` verification, eliminating false "llama.cpp (Vulkan GPU)" label reports when running on CPU or NPU fallback.
- **Vulkan Driver Crash Resolution**: Set explicit `GGML_DISABLE_VULKAN=1` environment variable in `llama-android.cpp` whenever CPU or NPU modes are targeted, preventing `ggml-vulkan` compute pipeline shader creation from failing and triggering SIGABRT on Adreno GPUs.
- **Smart Engine AUTO Routing**: Hard-coded strict hardware fallback cascade for GGUF models: **Priority 1: Genie X / QNN NPU** -> **Priority 2: llama.cpp Vulkan GPU** -> **Priority 3: llama.cpp CPU** (only if Vulkan pipeline creation fails).
- **Main Thread Stutter / Jank Elimination**: Refactored `InferenceManager.kt` native library pre-loader to execute lazily on background `Dispatchers.IO` threads, removing 19 synchronous `System.loadLibrary` calls from the UI main thread during app launch.
- **Double BOS Token Warning**: Removed manual `<bos>` tag prepending from `applyChatTemplate` in `InferenceManager.kt` and disabled `add_special` tokenization in `llama-android.cpp` when handling pre-formatted prompt templates.

## Version 0.6.0 - 2026-07-30

### The "Snapdragon Elite" Update

This release brings massive performance improvements and a much-requested overhaul to how you interact with Serenity. We've moved beyond "just working" to feeling truly snappy on the latest hardware.

- **GPU & CPU Hardware Acceleration Restored**:
    - Re-enabled `-DGGML_VULKAN=ON` build flag in `app/build.gradle.kts` for native Vulkan GPU offloading on Adreno/Mali GPUs in `llama.cpp`.
    - Added automatic `glslc` NDK executable resolution in `app/src/main/cpp/CMakeLists.txt` (`${ANDROID_NDK}/shader-tools/${ANDROID_HOST_TAG}/glslc`) and argument double-quoting in `vulkan-shaders-gen.cpp` to resolve Windows `CreateProcessA` failures across 427 Vulkan shader targets.
    - Optimized CPU inference thread count in `llama-android.cpp` and `InferenceManager.kt` to match performance core count (4 threads), eliminating thread contention and cache thrashing on big.LITTLE ARM chips.
    - Updated `InferenceManager.kt` routing to honor `preferredEngine` overrides and route explicit `GPU`/`CPU` requests directly to native `llama.cpp` while preserving the Genie X NPU magic pipeline.
    - Aligned chat prompt tags and control tag stripping across `llama.cpp`, `LiteRT-LM`, and `Genie X`.
- **NPU Power Unlocked**: Full support for Snapdragon 8 Elite (SM8750) and 8 Gen 3 (SM8650) via the Qualcomm AI Stack. If you're on a modern flagship, expect blazing fast, power-efficient inference that doesn't melt your battery.
- **Smart Engine Routing**: Serenity now automatically picks the best tool for the job. Whether it's LiteRT-LM, Genie X, or llama.cpp, we route your models to the engine that will run them the fastest on your specific device.
- **Session History & Management**: Your chats are now saved automatically! Swipe open the new session overlay to switch between different conversations, or start fresh whenever you want.
- **Advanced Persona Levels**: Unlocked Levels 6 (Cecilia) and 7 (Transcendent) with dedicated system prompts and a dynamic performance slider that adapts to your settings.
- **UI Polish & Control**:
    - **Load on Command**: We decoupled model loading from the settings—no more accidental RAM spikes. Tweak your settings, then hit "LOAD CORE" when you're ready.
    - **System Diagnostics**: Added an NPU Readiness tool to help you troubleshoot hardware acceleration issues.
    - **Visual Updates**: New faded persona backgrounds (optional) and a cleaner, more intuitive chat interface.
- **The Under-the-Hood Stuff**:
    - Broadened `.gitignore` glob patterns (`**/llama.cpp/`, `**/LiteRT-LM/`, etc.) to prevent accidental tracking of submodules.
    - Aggressive control tag stripping to keep your chat logs clean.
    - Optimized KV Cache management with quantization support (F16 down to Q4_0).
    - Better error handling for model loading and engine fallbacks.

## Version 0.5.4 Alpha - 2026-07-30

### Added

- Added **Session History Management** with JSON persistence (`context.filesDir/sessions/`), saving sessions automatically and allowing session switching, creation, and deletion.
- Added floating **Session History Overlay Button** on chat screen replacing green lock button, toggling a slim, clean session list panel.
- Added **Clear Session Button** in top app bar and history overlay for clearing current active chat messages.
- Added **Level 6 (Cecilia, `cecilia_02.png`)** and **Level 7 (Transcendent, `meditating_serenity.png`)** toggle switches at the bottom of Core Parameters / Hardware tab after a short margin.
- Implemented dynamic performance level slider indexing: slider dynamically adapts steps to active enabled levels, allowing Level 7 to occupy Level 6's physical slot on slider when Level 6 is disabled.
- Moved **Sticky Bottom Scroll** (auto-scroll toggle) to System Settings under UI Customization.
- Cleaned up UI components, spacing, and header layouts across main chat screen and parameter views.
- Updated **Session History Overlay Animation**: transition now anchors to and expands/scales directly out from the bottom-right history toggle button.
- Integrated **Persona System Prompts**: added distinct system prompts for all 8 levels (0 through 7) in `Persona.kt` and `ChatViewModel.kt`, passing system prompts down into `InferenceManager.kt` dispatches for LiteRT-LM, Genie X, and llama.cpp.
- Added **Faded Persona Background**: optional faded active level image wallpaper in chat screen background, togglable via UI Customization setting.
- Migrated **Markdown Viewer**: removed docs button from home top app bar, added Markdown Viewer button to System Settings with open book icon (`MenuBook`), and registered intent-filter service in `AndroidManifest.xml` for `.md` files.
- Deferred **Model Loading Control**: removed automatic model reloading from performance level slider, backend mode selector, engine picker, and KV cache size option. Model reloads **only** when pressing "LOAD CORE" on home screen, or upon exiting System Settings or Core Parameters screens.
- Relocated **KV Cache Quantization** selectors (`K Cache` & `V Cache`) from Core Parameters / Hardware tab directly into `SettingsScreen.kt` right under KV Cache Size, and removed redundant Model Parameters Center button.
- Decoupled **Active Core Selection** from automatic loading: selecting a new core in dropdown no longer triggers automatic load or offload. Added overlay **"Swap?"** button on left of Active Core box when selected core differs from currently loaded model to trigger model swap.

### Fixed

- Enhanced prompt template tag stripping with regex-based `CONTROL_TAG_REGEX` handling turn/role/bos/eos/pause tags in [InferenceManager.kt](file:///s:/AndroidStudioProjects/SerenityDroid/app/src/main/java/com/example/serenitydroid/engine/InferenceManager.kt), added full accumulated text tag cleaning in [ChatViewModel.kt](file:///s:/AndroidStudioProjects/SerenityDroid/app/src/main/java/com/example/serenitydroid/ChatViewModel.kt), updated `GENIE_X` and `LLAMA_CPP` streaming stop tag emission logic, and added fallback tag stripping in `ChatMessageItem` in [SerenityHomeScreen.kt](file:///s:/AndroidStudioProjects/SerenityDroid/app/src/main/java/com/example/serenitydroid/ui/SerenityHomeScreen.kt).
- Fixed chat template control tag leakage (`<start_of_turn>`, `</end_of_turn>`, `<start_of_turn>model`) in streaming response streams in [InferenceManager.kt](file:///s:/AndroidStudioProjects/SerenityDroid/app/src/main/java/com/example/serenitydroid/engine/InferenceManager.kt) by enforcing immediate stream termination on turn boundary tags and token sanitization across Genie X, LiteRT-LM, and llama.cpp runtimes.

- Fixed `AnimatedVisibility` implicit receiver call error in [SerenityHomeScreen.kt](file:///S:/AndroidStudioProjects/SerenityDroid/app/src/main/java/com/example/serenitydroid/ui/SerenityHomeScreen.kt) inside `Box` scope by using explicit `this@Column.` receiver.
- Resolved missing reference `getSystemMemoryStats` in [SerenityHomeScreen.kt](file:///S:/AndroidStudioProjects/SerenityDroid/app/src/main/java/com/example/serenitydroid/ui/SerenityHomeScreen.kt) by implementing `getSystemMemoryStats()` on [ChatViewModel.kt](file:///S:/AndroidStudioProjects/SerenityDroid/app/src/main/java/com/example/serenitydroid/ChatViewModel.kt).

## Version 0.5.3 Alpha - 2026-07-30

### Fixed

- Updated `CMakeLists.txt` to build `llama.cpp` as shared libraries (`BUILD_SHARED_LIBS ON`), outputting `libggml-base.so`, `libggml.so`, and `libllama.so` to satisfy Genie X dynamic linking requirements.
- Linked OpenMP flags (`OpenMP_CXX_FLAGS`) explicitly to `serenity_llama` target in `CMakeLists.txt`.
- Added packaging rules in `app/build.gradle.kts` (`copyQnnLibs`, `extractAarJniLibs`, and `jniLibs.pickFirsts`) to extract `liblitert_dispatch.so` and `liblitert_lm_dispatch.so` directly from AAR dependencies during Gradle builds.
- Refined native library preloading order in `InferenceManager.kt` (`omp`, `ggml-base`, `ggml`, `llama`, `litert_dispatch`, `geniex_plugin_llama_cpp`) to resolve OpenMP `__kmpc_dispatch_deinit` symbol lookup errors.
- Added `LOGCAT_RECOMMENDED_FILTER` constant in `InferenceManager.kt` (`adb logcat -s InferenceManager:V GenieXSdk:V LlamaNative:V *:S`) for noise reduction.

## Version 0.5.2 Alpha - 2026-07-29

### Fixed

- Fixed `InferenceManager.kt` engine routing to strictly enforce that `.litertlm` and `.tflite` model files always route to `LITERT_LM` engine, ignoring preferred engine setting to avoid GGUF magic character error.
- Enforced strict format validation in `InferenceManager.kt` preventing `.gguf` files from attempting `LITERT_LM` load.
- Expanded native pre-loader in `InferenceManager.kt` companion object and `EngineRegistry.kt` to bind `iomp5`, `gomp`, `geniex_plugin_llama_cpp`, `geniex_plugin_qairt`, `litert_dispatch`, and `QnnHtp` for Qualcomm OpenMP symbol resolution (`__kmpc_dispatch_deinit`).
- Added `llama_log_set` callback in `llama-android.cpp` to forward llama.cpp internal logs to Android Logcat (`LlamaNative` tag).
- Fixed `isNpuSupported()` in `InferenceManager.kt` to verify native bridge library presence (`libgeniex.so`, `libgeniex_plugin_llama_cpp.so`, `libgeniex_plugin_qairt.so`) before attempting Genie X initialization, preventing `Invalid plugin` errors.
- Added OpenMP (`libomp.so`) pre-loader in `InferenceManager.kt` companion object to fix Genie X `dlopen` missing symbol error (`__kmpc_dispatch_deinit`).
- Enabled `-DGGML_OPENCL=ON` and `-DGGML_HEXAGON=ON` CMake build flags in `app/build.gradle.kts` for GPU/NPU acceleration in llama.cpp.
- Updated `loadLlamaModel` in `InferenceManager.kt` to allow layer offloading when `DeviceBackendMode.NPU` is selected.
- Updated KV Cache Size slider in `ModelParametersScreen.kt` to range `2048..65536` in steps of `2048`.
- Implemented real prompt tokenization, context decoding, and sampler decoding in `llama-android.cpp` (`completion_init` and `completion_loop`), resolving token generation getting stuck.
- Added `llama_sampler_init_penalties` (repetition penalty = 1.15), `top_p` (0.9), and `temperature` (0.7) in `llama-android.cpp` to eliminate infinite repetition loops during response generation.
- Added **"RUN NPU READINESS DIAGNOSTICS"** button and live AlertDialog in `SettingsScreen.kt` & `InferenceManager.kt` (`getNpuDiagnosticInfo`) to inspect dynamic feature installation, Genie X core libraries (`libgeniex.so`), plugin bridges, `libQnnHtp.so`, and OpenMP symbols.
- Added **INFERENCE ENGINE SELECTION** picker (`AUTO`, `LLAMA_CPP`, `GENIE_X`, `LITERT_LM`) in `SettingsScreen.kt`, `SettingsRepository.kt`, and `InferenceManager.kt` (`EngineSelection`), allowing users to explicitly select `LLAMA_CPP` and bypass Genie X completely.
- Added **KV CACHE SIZE** quick-select bar (`2K`, `4K`, `8K`, `16K`, `32K`, `64K`) on `SettingsScreen.kt` to prevent memory bottlenecks and 128k context allocation slowdowns.
- Integrated `KvQuant` enum (`F16`, `Q8_0`, `Q4_0`, `Q4_1`, `Q5_0`, `Q5_1`) and passed `kvQuantK` / `kvQuantV` parameters down to `LlamaAndroid.loadModel`.
- Added Gemma prompt template formatting (`<start_of_turn>user\n...<end_of_turn>\n<start_of_turn>model\n`) in `applyLlamaTemplate` for `llama.cpp` inference.
- Created technical implementation plan `implementation_plan.md` for full inference engine SPI refactoring.

## Version 0.5.1 Alpha - 2026-07-28

### Added

- Integrated **Qualcomm Genie X / GGML QNN NPU runtime** mapping for Snapdragon 8 Elite (`SM8750`) and 8 Gen 3 (`SM8650`) hardware.
- Enabled native Hexagon HTP NPU hardware acceleration for standard `.gguf` models (e.g., Gemma-4 E2B & E4B QAT GGUFs) via `GenieEngine` and `libggml-qnn.so`.
- Updated `ModelRefresher.kt` dynamic engine matching logic to prioritize NPU execution for GGUF models on NPU-capable Snapdragon devices.

## Version 0.5.0 Alpha - 2026-07-25

### Added

- Replaced static `LITERT_LM` badge in model selection dropdowns (`SerenityHomeScreen.kt`, `SettingsScreen.kt`) with top compatible hardware inference engine (`NPU`, `GPU`, `CPU`) evaluated dynamically per model and device capabilities.
- Updated `qualcommNPUsetup.md` grounding model installation, runtime engine dispatches, and NPU delegates directly in official Google AI Edge LiteRT and Qualcomm AI Hub documentation specs.
- Added `GgufConverterWizard.kt` engine and `RuntimeWizardDialog.kt` step-by-step Jetpack Compose dialog titled **"gguf to LiteRT-LM"** with target quantization presets (`INT8`, `INT4`, `FP16`) and live hardware compatibility checking.
- Enabled all runtimes by default in `ChatViewModel.kt` (`RuntimeOption.ALL_LEAF_RUNTIMES`).
- Added **"gguf to LiteRT-LM"** conversion wizard launcher buttons in `SettingsScreen.kt` and `ModelInstallerActivity.kt`.
- Updated `ModelRefresher.kt` and `ChatViewModel.kt` to dynamically filter discovered models by selected runtime file extensions.
- Added multi-modal vision support (Ask Image) with image attachment UI and Bitmap passing into LiteRT engine.
- Implemented generation stats tracking in `ChatViewModel.kt` displaying Time-To-First-Token (TTFT), Decode Speed (t/s), and Latency during streaming.
- Enabled splittable K and V cache configuration internally in `LiteRtManager.kt` via reflection for Edge models that support it.

## Version 0.4.21 Alpha - 2026-07-25

### Added

- Added `ModelRefresher` component in `com.example.serenitydroid.engine` to recursively scan and refresh available models in a specified folder.
- Added `REFRESH MODELS` action button in `SettingsScreen.kt` for manual directory rescanning.

### Fixed

- Fixed `Invalid catalog definition` error in `gradle/libs.versions.toml` by pointing `androidx-recyclerview` `version.ref` to `recyclerview` instead of `recyclerviewVersion`.
- Fixed post-response app crash in `LiteRtManager.kt` by launching response flow collection within callbackFlow scope instead of an unmanaged `CoroutineScope(Dispatchers.IO)`.

## Version 0.4.20 Alpha - 2026-07-24

### Added

- Integrated Qualcomm AI Engine Direct Delegate (`com.qualcomm.qti:qnn-runtime:2.34.0` and `com.qualcomm.qti:qnn-litert-delegate:2.34.0`) per `qualcommNPUsetup.md` for Snapdragon 8 Gen 1-3 & 8 Elite (SM8750) HTP NPU hardware acceleration.

- Resolved Kotlin 2.0 parameter annotation warnings (`-Xannotation-default-target=param-property`) by using explicit `@param:ApplicationContext` in `ChatViewModel.kt` and `LiteRtManager.kt`.
- Added double-tap gesture on `CORE DEPTH` text in `SerenityHomeScreen.kt` to unlock Level 6 (Cecilia) and Level 7 (The Transcendent One). Secret levels automatically lock and hide when sliding back to levels 0-5.
- Added **Level 0** slider position (`0f`) to offload model and activate Level 0 error/unloaded persona state.
- Added top-left Info button (`HardwareInfoDialog`) displaying system RAM usage, app heap memory, hardware acceleration mode, and verbose model details with a "BACK" button.
- Added `App Profiling (Resource Monitor)` setting switch and chat screen overlay box displaying real-time memory usage and percentages upon interaction.
- Integrated persona system prompts and descriptions from `serenity_resources.py` into `ChatViewModel.kt`.
- Added file existence, readability, and non-empty validation checks in `LiteRtManager.kt` before initializing native model engine.
- Initialized Git repository and tagged version `v1.4.20-alpha`.

## Version 0.4.0 - 2026-07-24

### Added

- Downgraded `kotlinx-coroutines` to version `1.8.1` in `libs.versions.toml` and `app/build.gradle.kts` to resolve `NoSuchMethodError: SendChannel.close$default` crash during LiteRT-LM streaming response completion (`Conversation.sendMessageAsync`).
- Added `Autoload Model on Startup` setting switch in `SettingsScreen.kt` and `ChatViewModel.kt`, allowing users to disable automatic model loading into RAM/GPU on app launch (defaulted to disabled for fast startup).
- Added automatic Qualcomm SM8750 HTP NPU backend routing (`isQualcommModel`) in `LiteRtManager.kt` so QNN-compiled models (e.g. `qualcomm_sm8750.litertlm` on Galaxy S25+ Snapdragon 8 Elite) immediately target `Backend.NPU(nativeLibraryDir)`.
- Added `outFile.parentFile?.mkdirs()` and URI path prefix sanitization (`substringAfterLast(":")`) in `ModelInstallerActivity.kt` to resolve local file import `FileNotFoundException` when copying `.litertlm` models into subfolder structures under `filesDir/Models/`.
- Added `System.loadLibrary("litertlm_jni")` / `System.loadLibrary("litertlm")` companion object `init` pre-loader in `LiteRtManager.kt` to resolve JNI native symbol link errors (`NativeLibraryLoader.nativeCheckLoaded`).
- Added NPU dispatch library detection in `LiteRtManager.kt` so backend selection checks for native dispatch drivers before attempting NPU initialization.
- Updated `loadModel` exception handling in `LiteRtManager.kt` to catch `Throwable` (handling native link/dispatch errors gracefully) so backend selection falls back cleanly: **NPU > GPU > CPU**.
- Set `useLegacyPackaging = true` under `jniLibs` in `app/build.gradle.kts` so native `.so` shared libraries are extracted directly into `nativeLibraryDir` for `dlopen` access.
- Added `INTERNET` and `ACCESS_NETWORK_STATE` permissions in `AndroidManifest.xml` for Google Drive model downloads.
- Added AUTO RAM mode for GPU Offload Layers in `ChatViewModel.kt` and `SettingsScreen.kt`, querying system RAM via `ActivityManager.MemoryInfo` to auto-calculate optimal GPU layer offload.
- Added System RAM stats display (`Total GB / Avail GB`) in `SettingsScreen.kt`.
- Replaced legacy `ModelInstallerActivity.kt` layout with full Jetpack Compose UI including top margin, Google Drive downloader input, live progress counter (`MB / MB`), and local file picker.
- Added 2048-step increments to Context Window slider in `SettingsScreen.kt`.
- Added `OpenableColumns.DISPLAY_NAME` resolution in `ModelInstallerActivity.kt` to preserve full filenames when importing local `.litertlm` files.
- Added `DeviceBackendMode` hardware acceleration selector (`AUTO`, `NPU`, `GPU`, `CPU`) with automatic fallback priority: **NPU > GPU > CPU**.
- Added NPU hardware native library bindings (`context.applicationInfo.nativeLibraryDir`) for AICore / NPU hardware execution in `LiteRtManager.kt`.
- Added recursive subfolder model scanning (`walkTopDown()`) in `ChatViewModel.kt` to auto-discover nested `.litertlm` models in `context.filesDir/Models/`.
- Added `GoogleDriveDownloader.kt` helper to download multi-gigabyte `.litertlm` models from Google Drive public folder (`SerenityDroidModels`) directly into `context.filesDir/Models/`.
- Added explicit **LOAD MODEL** button to `SerenityHomeScreen.kt` system controls to decouple level slider dragging from immediate model reloads.

### Changed

- Specified `@param:` annotation targets (`@param:DrawableRes`, `@param:ColorRes`) in `Persona.kt` to resolve Kotlin 2.0 compiler annotation target warnings.
- Added `@Suppress("DEPRECATION")` to status bar color configuration in `Theme.kt` to resolve Android Java deprecation warnings.
- Updated model persistence in `ChatViewModel.kt` to save custom model selection in SharedPreferences and gracefully display system messages when no `.litertlm` models are installed.

- Migrated core LLM inference engine from custom C++ NDK JNI (`serenity_llama` / llama.cpp) to Google LiteRT-LM Android Maven SDK (`com.google.ai.edge.litertlm:litertlm-android`).
- Removed multi-gigabyte model assets from `app/src/main/assets/models/` to resolve Android Zip32 4GB APK packaging error and keep base build lightweight.
- Created `LiteRtManager.kt` engine manager handling `.litertlm` models with native GPU/NPU acceleration.
- Refactored `ChatViewModel.kt` to collect asynchronous Kotlin Flow streams from LiteRT-LM.
- Updated `AndroidManifest.xml` with native OpenCL and VNDK library references for GPU support.

## Version 0.3.0 - 2026-07-17

### Added

- Initial Android (Kotlin/MVVM) port of the Serenity PC desktop application.
- Ephemeral chat setting via `SwitchPreferenceCompat` in a new Settings screen.
- Dynamic text color feedback on buttons (white/electric blue for enabled/disabled).
- Slider enabled at launch, defaulting to Level 1.
- "The" moniker dynamic persona rename for Turbo Mode.
- Material Toolbar with settings menu.
- `SettingsActivity` and `SettingsFragment` for the preferences UI.
- `README.md`, `LICENSE.md`, and `CHANGELOG.md`.
