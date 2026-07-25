package com.example.serenitydroid

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // Populate the model selection list from files stored in the app's filesDir
        val listPref = findPreference<ListPreference>("selected_model")
        listPref?.let { pref ->
            val files = requireContext().filesDir.listFiles()?.filter { it.extension == "gguf" } ?: emptyList()
            if (files.isEmpty()) {
                pref.entries = arrayOf("No models installed")
                pref.entryValues = arrayOf("")
                pref.isEnabled = false
            } else {
                pref.entries = files.map { it.name }.toTypedArray()
                pref.entryValues = files.map { it.name }.toTypedArray()
                pref.isEnabled = true
            }
        }
    }
}

