package com.example.serenitydroid

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.serenitydroid.net.GoogleDriveDownloader
import com.example.serenitydroid.ui.theme.SerenityDroidTheme
import com.example.serenitydroid.ui.theme.SerenityAccent
import com.example.serenitydroid.ui.theme.SerenityBackground
import com.example.serenitydroid.ui.theme.SerenityInference
import com.example.serenitydroid.ui.theme.SerenitySurface
import com.example.serenitydroid.ui.theme.SerenityText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class ModelInstallerActivity : ComponentActivity() {

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { handleUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SerenityDroidTheme {
                ModelInstallerScreen(
                    onBack = { finish() },
                    onSelectLocalFile = { openDocument.launch(arrayOf("*/*")) },
                    onDownloadDriveFile = { urlOrId, fileName, onProgress, onComplete, onError ->
                        downloadFromDrive(urlOrId, fileName, onProgress, onComplete, onError)
                    }
                )
            }
        }
    }

    private fun downloadFromDrive(
        driveUrlOrId: String,
        targetFileName: String,
        onProgress: (Long, Long) -> Unit,
        onComplete: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val downloadedFile = GoogleDriveDownloader.downloadFile(
                    context = this@ModelInstallerActivity,
                    fileIdOrUrl = driveUrlOrId,
                    targetFileName = targetFileName
                ) { bytesDownloaded, totalBytes ->
                    onProgress(bytesDownloaded, totalBytes)
                }
                runOnUiThread {
                    Toast.makeText(this@ModelInstallerActivity, "Model downloaded: ${downloadedFile.name}", Toast.LENGTH_LONG).show()
                    setResult(Activity.RESULT_OK, Intent().apply { putExtra("installed", downloadedFile.name) })
                    onComplete(downloadedFile)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@ModelInstallerActivity, "Drive download failed: ${e.message}", Toast.LENGTH_LONG).show()
                    onError(e.message ?: "Download failed")
                }
            }
        }
    }

    private fun handleUri(uri: Uri) {
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
                    Toast.makeText(this@ModelInstallerActivity, "Model installed: ${outFile.name}", Toast.LENGTH_LONG).show()
                    setResult(Activity.RESULT_OK, Intent().apply { putExtra("installed", outFile.name) })
                    finish()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@ModelInstallerActivity, "Failed to install model: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun resolveFileName(uri: Uri): String {
        var name = "model.litertlm"
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
        if (name == "model.litertlm") {
            val lastSegment = uri.lastPathSegment ?: ""
            val clean = lastSegment.substringAfterLast("/").substringAfterLast(":")
            if (clean.isNotBlank()) name = clean
        }
        return name
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelInstallerScreen(
    onBack: () -> Unit,
    onSelectLocalFile: () -> Unit,
    onDownloadDriveFile: (
        driveUrlOrId: String,
        targetFileName: String,
        onProgress: (Long, Long) -> Unit,
        onComplete: (File) -> Unit,
        onError: (String) -> Unit
    ) -> Unit
) {
    var driveUrl by remember { mutableStateOf("https://drive.google.com/drive/folders/137vu7Zw9HxOtsw-ChwNTyBZb3yAUZRc6") }
    var targetFileName by remember { mutableStateOf("gemma-4-E2B-it_qualcomm_sm8750.litertlm") }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadedBytes by remember { mutableStateOf(0L) }
    var totalBytes by remember { mutableStateOf(0L) }
    var statusText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MODEL INSTALLER & DRIVE", fontFamily = FontFamily.Monospace, color = SerenityAccent) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = SerenityAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SerenityBackground)
            )
        },
        containerColor = SerenityBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text("GOOGLE DRIVE MODEL DOWNLOADER", style = MaterialTheme.typography.titleMedium, color = SerenityAccent, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = driveUrl,
                onValueChange = { driveUrl = it },
                label = { Text("Google Drive Folder/File URL or ID") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SerenityAccent,
                    unfocusedBorderColor = SerenitySurface
                )
            )

            OutlinedTextField(
                value = targetFileName,
                onValueChange = { targetFileName = it },
                label = { Text("Target Save Filename (.litertlm)") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SerenityAccent,
                    unfocusedBorderColor = SerenitySurface
                )
            )

            Button(
                onClick = {
                    if (driveUrl.isNotBlank() && targetFileName.isNotBlank()) {
                        isDownloading = true
                        statusText = "Downloading model..."
                        onDownloadDriveFile(
                            driveUrl,
                            targetFileName,
                            { downloaded, total ->
                                downloadedBytes = downloaded
                                totalBytes = total
                            },
                            { file ->
                                isDownloading = false
                                statusText = "Downloaded: ${file.name}"
                            },
                            { error ->
                                isDownloading = false
                                statusText = "Error: $error"
                            }
                        )
                    }
                },
                enabled = !isDownloading && driveUrl.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = SerenityAccent),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(
                    if (isDownloading) "DOWNLOADING..." else "DOWNLOAD FROM GOOGLE DRIVE",
                    color = SerenityBackground,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (isDownloading || statusText.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SerenitySurface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(statusText, color = SerenityText, style = MaterialTheme.typography.bodyMedium)
                        if (isDownloading) {
                            if (totalBytes > 0) {
                                LinearProgressIndicator(
                                    progress = { (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = SerenityInference,
                                    trackColor = SerenityBackground
                                )
                                val downloadedMb = String.format("%.1f", downloadedBytes / (1024f * 1024f))
                                val totalMb = String.format("%.1f", totalBytes / (1024f * 1024f))
                                Text("$downloadedMb MB / $totalMb MB", color = SerenityAccent, style = MaterialTheme.typography.labelSmall)
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = SerenityInference,
                                    trackColor = SerenityBackground
                                )
                                val downloadedMb = String.format("%.1f", downloadedBytes / (1024f * 1024f))
                                Text("$downloadedMb MB downloaded", color = SerenityAccent, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = SerenitySurface)

            Text("LOCAL STORAGE (PHONE / SD CARD)", style = MaterialTheme.typography.titleMedium, color = SerenityAccent, fontWeight = FontWeight.Bold)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSelectLocalFile,
                    colors = ButtonDefaults.buttonColors(containerColor = SerenitySurface),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("SELECT LOCAL FILE", color = SerenityAccent, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
                Button(
                    onClick = onSelectLocalFile,
                    colors = ButtonDefaults.buttonColors(containerColor = SerenityAccent),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("gguf to LiteRT-LM", color = SerenityBackground, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        }
    }
}
