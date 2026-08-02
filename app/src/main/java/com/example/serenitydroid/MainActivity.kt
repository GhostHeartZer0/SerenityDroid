package com.example.serenitydroid

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.serenitydroid.ui.SerenityHomeScreen
import com.example.serenitydroid.ui.theme.SerenityDroidTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            var currentScreen by remember { mutableStateOf("Home") }
            
            SerenityDroidTheme {
                if (currentScreen == "Home") {
                    SerenityHomeScreen(
                        viewModel = viewModel,
                        onOpenSettings = {
                            startActivity(Intent(this, SettingsActivity::class.java))
                        },
                        onOpenParameters = { currentScreen = "Parameters" },
                        onOpenDocs = {
                            startActivity(Intent(this, com.example.serenity_md_viewer.MdViewerActivity::class.java))
                        }
                    )
                } else if (currentScreen == "Parameters") {
                    com.example.serenitydroid.ui.ModelParametersScreen(
                        viewModel = viewModel,
                        onBack = { currentScreen = "Home" }
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.saveHistory()
    }
}
