package com.example.serenitydroid

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.example.serenitydroid.ui.SettingsScreen
import com.example.serenitydroid.ui.theme.SerenityDroidTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    private val modelInstallerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.updateAvailableModels()
        }
    }

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { handleFilePickerUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SerenityDroidTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = {
                        viewModel.reloadIfLoaded()
                        finish()
                    },
                    onOpenParameters = {
                        finish()
                    },
                    onOpenDocs = {
                        startActivity(android.content.Intent(this, com.example.serenity_md_viewer.MdViewerActivity::class.java))
                    },
                    onDownloadMore = {
                        modelInstallerLauncher.launch(android.content.Intent(this, ModelInstallerActivity::class.java))
                    },
                    onBrowseFileExplorer = {
                        openDocument.launch(arrayOf("*/*"))
                    }
                )
            }
        }
    }

    private fun handleFilePickerUri(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val displayName = resolveFileName(uri).substringAfterLast(":")
                val modelsDir = File(filesDir, "Models")
                val outFile = File(modelsDir, displayName)
                outFile.parentFile?.mkdirs()
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
                runOnUiThread {
                    Toast.makeText(this@SettingsActivity, "Imported model: ${outFile.name}", Toast.LENGTH_LONG).show()
                    viewModel.updateAvailableModels()
                    viewModel.setCustomModel(outFile.name)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@SettingsActivity, "Failed to import file: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun resolveFileName(uri: Uri): String {
        var name = "custom_model.bin"
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    val resolved = cursor.getString(nameIndex)
                    if (!resolved.isNullOrBlank()) name = resolved
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (name == "custom_model.bin") {
            val lastSegment = uri.lastPathSegment ?: ""
            val clean = lastSegment.substringAfterLast("/").substringAfterLast(":")
            if (clean.isNotBlank()) name = clean
        }
        return name
    }
}