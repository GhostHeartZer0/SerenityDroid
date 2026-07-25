package com.example.serenitydroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.serenitydroid.ui.SettingsScreen
import com.example.serenitydroid.ui.theme.SerenityAITheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SerenityAITheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onDownloadMore = {
                        startActivity(android.content.Intent(this, ModelInstallerActivity::class.java))
                    }
                )
            }
        }
    }
}