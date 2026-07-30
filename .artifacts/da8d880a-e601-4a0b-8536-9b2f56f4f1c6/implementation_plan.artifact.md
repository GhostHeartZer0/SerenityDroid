# Implementation Plan - Fix Dependency Conflicts and Native Crashes

The app is experiencing two types of crashes: a `NoSuchMethodError` (Java) and a `SIGABRT` (Native). This plan addresses the dependency mismatch and potential memory/VRAM issues.

## User Review Required

> [!IMPORTANT]
> I am upgrading `kotlinx-coroutines` to `1.11.0`. This is necessary to resolve the `NoSuchMethodError: SendChannel.close$default` which occurs because the LiteRT-LM SDK was likely compiled against a newer version of the coroutines library than the one currently in the project (1.8.1).

> [!WARNING]
> I am pinning `litertlm-android` to version `0.14.0` instead of `latest.release`. Using `latest.release` can cause non-deterministic builds and sudden breaks when new versions are published with breaking changes.

## Proposed Changes

### [Dependencies]

#### [MODIFY] [libs.versions.toml](file:///S:/AndroidStudioProjects/SerenityDroid/gradle/libs.versions.toml)
- Update `kotlinxCoroutinesAndroid` from `1.8.1` to `1.11.0`.

#### [MODIFY] [build.gradle.kts](file:///S:/AndroidStudioProjects/SerenityDroid/app/build.gradle.kts) (App Module)
- Change `com.google.ai.edge.litertlm:litertlm-android:latest.release` to `com.google.ai.edge.litertlm:litertlm-android:0.14.0`.

### [Core Engine]

#### [MODIFY] [LiteRtManager.kt](file:///S:/AndroidStudioProjects/SerenityDroid/app/src/main/java/com/example/serenitydroid/engine/LiteRtManager.kt)
- Add a small delay or `yield()` in the response flow to prevent UI thread saturation if the model streams too fast.
- Ensure `generateResponse` is called on `Dispatchers.IO` to avoid blocking the main thread during initialization of the internal flow.

## Verification Plan

### Automated Tests
- Check for build success after dependency updates.
- Verify that `NoSuchMethodError` no longer appears in Logcat when a response completes.

### Manual Verification
- Send "heyy, wassup?" and verify that the app doesn't crash after "Understood.".
- Monitor memory usage to see if `SIGABRT` is triggered by VRAM exhaustion.
