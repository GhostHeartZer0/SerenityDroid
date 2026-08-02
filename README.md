# SerenityAI - Android Version (v3.0)

**A chatbot, a companion.**

(TODO: update this)

Based off the Serenity PC desktop application, retooled from the original Python/Tkinter version. Built with Kotlin following modern Android architecture patterns (MVVM).

This version implements key user feedback from the original, adding significant features like an ephemeral chat setting, improved UI state feedback, and a more intuitive startup flow.

---

## Project Structure

| Path | Description |
|------|-------------|
| `app/build.gradle.kts` | Includes the AndroidX Preference library for the settings screen |
| `app/src/main/AndroidManifest.xml` | Updated to include `SettingsActivity` |
| `app/src/main/java/com/example/serenitydroid/` | Core application code |

### Core Files

- **`MainActivity.kt`** — Toolbar, settings menu, dynamic text colors on controls.
- **`ChatViewModel.kt`** — Ephemeral chat setting, initial app launch state, "The" moniker for Turbo mode.
- **`SettingsActivity.kt`** & **`SettingsFragment.kt`** — Settings screen components.

### Resources

- `layout/activity_main.xml` — Material Toolbar to host the settings menu.
- `menu/main_menu.xml` — Settings icon in the toolbar.
- `xml/preferences.xml` — "Ephemeral Chat" switch for the settings screen.
- `values/strings.xml` — Text for the settings screen and simplified button labels.

---

## Python → Kotlin Translation

| Python README Request | Android (Kotlin) Equivalent |
|---|---|
| "make history vanish" | `SwitchPreferenceCompat` in Settings controlling history saving in `ChatViewModel` |
| "Load Model should say 'Begin'…" | String resource changed from "Load Model" to "Start Chatting" |
| "make slider work right away" | `MainActivity`/`ChatViewModel` enable slider at launch, default Level 1 |
| "white/electric blue text tells when a feature is available" | `MainActivity` sets button text color by enabled/disabled state |
| "The 'The' moniker is used as a turbo mode sensor" | `ChatViewModel` dynamically alters Persona name on Turbo Mode activation |

---

## License

This project is licensed under the Apache License 2.0 — see [LICENSE.md](LICENSE.md) for details.
