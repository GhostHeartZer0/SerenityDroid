SerenityAI - Android Version (v3.0)
A chatbot, a companion. 
Based off my Serenity PC desktop application, retooled from the original Python/Tkinter version. It's built with Kotlin and follows modern Android architecture patterns (MVVM).

This version implements key user feedback from the original README.txt, adding significant features like an ephemeral chat setting, improved UI state feedback, and a more intuitive startup flow.

Project Structure
app/build.gradle.kts: Now includes the AndroidX Preference library for the new settings screen.

app/src/main/AndroidManifest.xml: Updated to include the new SettingsActivity.

app/src/main/java/com/example/serenityai/:

MainActivity.kt: Updated to include a Toolbar, settings menu, and logic for dynamic text colors on controls.

ChatViewModel.kt: Major update to handle the ephemeral chat setting, initial app launch state, and the "The" moniker for Turbo mode.

SettingsActivity.kt & SettingsFragment.kt: (New Files) The components for the new settings screen.

app/src/main/res/:

layout/activity_main.xml: Updated with a Material Toolbar to host the settings menu.

menu/main_menu.xml: (New File) Defines the settings icon in the toolbar.

xml/preferences.xml: (New File) Defines the "Ephemeral Chat" switch for the settings screen.

values/strings.xml: Updated with new text for the settings screen and simplified button labels.

How It Translates from Python/Tkinter
Python README Request

Android (Kotlin) Equivalent

"make history vanish"

A SwitchPreferenceCompat in a new Settings screen that controls history saving logic in the ChatViewModel.

"Load Model should say 'Begin'..."

String resource for the button changed from "Load Model" to "Start Chatting".

"maske slider work right away"

MainActivity and ChatViewModel logic adjusted to enable the slider from launch and default to Level 1.

"white/electric blue text tells when a feature is available"

MainActivity now programmatically sets the text color of buttons based on their enabled/disabled state.

"The 'The' moniker is used as a turbo mode sensor"

ChatViewModel now dynamically alters the Persona name when Turbo Mode is activated.