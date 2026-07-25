# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## Version 1.4.20 Alpha - 2026-07-24

### Added

- Resolved Kotlin 2.0 parameter annotation warnings (`-Xannotation-default-target=param-property`) by using explicit `@param:ApplicationContext` in `ChatViewModel.kt` and `LiteRtManager.kt`.
- Added double-tap gesture on `CORE DEPTH` text in `SerenityHomeScreen.kt` to unlock Level 6 (Cecilia). Level 6 automatically locks and hides when sliding away to other levels.
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
- Added 2048 step increments to Context Window slider in `SettingsScreen.kt`.
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
