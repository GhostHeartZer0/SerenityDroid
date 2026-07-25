## Known Issues

- Model downloading and file path resolution for local/remote GGUF models.
- Hardware key handling during active LLM token streaming.
- Unhandled UI action stubs for Live Mode and MultiModal file attachments (`SerenityHomeScreen.kt`).

## Unverified Features

- JNI completion loop with GPU layer acceleration (`nGpuLayers`) on physical ARM64 hardware vs x86_64 emulator.
- Audio stream wake-word detection (`processAudioForWakeWord`) performance and accuracy on-device.
- Native prompt level auto-selection (`autoSelectLevelForPrompt`).
- Ephemeral chat state cleanup across unexpected app termination.
- SAF / URI content copy performance for large GGUF files in `ModelInstallerActivity`.

## To-Do List

- Add living avatar and setting. Reactive to internal environment and indicative of it.
- Consider feasibility of reacting to external stimuli (cross reference with Serenity Minimal and SerenityAI Android Version).
- Create a new settings section for avatar and environmental reactivity.
- Review hardware key handling.
- Resolve model downloading and file management flow.