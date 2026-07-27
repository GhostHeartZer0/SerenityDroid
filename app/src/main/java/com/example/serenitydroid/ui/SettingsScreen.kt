package com.example.serenitydroid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.serenitydroid.ChatViewModel
import com.example.serenitydroid.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onOpenParameters: () -> Unit,
    onDownloadMore: () -> Unit = {},
    onBrowseFileExplorer: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SYSTEM SETTINGS", fontFamily = FontFamily.Monospace, color = SerenityAccent) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = SerenityAccent) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SerenityBackground)
            )
        },
        containerColor = SerenityBackground
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("HARDWARE BACKEND", style = MaterialTheme.typography.titleMedium, color = SerenityAccent, fontWeight = FontWeight.Bold)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.example.serenitydroid.engine.DeviceBackendMode.entries.forEach { mode ->
                    val isSelected = uiState.backendMode == mode
                    Button(
                        onClick = { viewModel.setBackendMode(mode) },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isSelected) SerenityAccent else SerenitySurface),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Text(mode.name, color = if (isSelected) SerenityBackground else SerenityAccent, fontSize = 10.sp)
                    }
                }
            }

            HorizontalDivider(color = SerenitySurface)

            Text("MODEL PARAMETERS", style = MaterialTheme.typography.titleMedium, color = SerenityAccent, fontWeight = FontWeight.Bold)
            Card(colors = CardDefaults.cardColors(containerColor = SerenitySurface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Fine-tune sampling, caches, and hardware acceleration flags.", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onOpenParameters,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SerenityAccent)
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, tint = SerenityBackground, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("OPEN PARAMETER CENTER", color = SerenityBackground, fontWeight = FontWeight.Bold)
                    }
                }
            }

            HorizontalDivider(color = SerenitySurface)

            Text("PERFORMANCE BENCHMARK", style = MaterialTheme.typography.titleMedium, color = SerenityAccent, fontWeight = FontWeight.Bold)
            Card(colors = CardDefaults.cardColors(containerColor = SerenitySurface)) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Active Engine Speed", style = MaterialTheme.typography.labelSmall, color = SerenityInference)
                        Text(uiState.benchmarkResult ?: "No result", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    }
                    Button(
                        onClick = { viewModel.runBenchmark() },
                        enabled = uiState.isModelLoaded && !uiState.isBenchmarking,
                        colors = ButtonDefaults.buttonColors(containerColor = SerenityInference)
                    ) {
                        Text(if (uiState.isBenchmarking) "RUNNING..." else "START BENCH", color = SerenityBackground)
                    }
                }
            }

            HorizontalDivider(color = SerenitySurface)

            Text("UI CUSTOMIZATION", style = MaterialTheme.typography.titleMedium, color = SerenityAccent, fontWeight = FontWeight.Bold)
            Card(colors = CardDefaults.cardColors(containerColor = SerenitySurface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Chat Text Size", color = Color.White)
                        Text("${uiState.chatTextSize.toInt()}sp", color = SerenityAccent)
                    }
                    Slider(
                        value = uiState.chatTextSize,
                        onValueChange = { viewModel.setUiSettings(textSize = it) },
                        valueRange = 10f..24f,
                        steps = 14,
                        colors = SliderDefaults.colors(thumbColor = SerenityAccent, activeTrackColor = SerenityAccent)
                    )
                }
            }

            HorizontalDivider(color = SerenitySurface)

            Text("MODEL ACQUISITION", style = MaterialTheme.typography.titleMedium, color = SerenityAccent, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onBrowseFileExplorer, colors = ButtonDefaults.buttonColors(containerColor = SerenitySurface), modifier = Modifier.weight(1f)) {
                    Text("FILE EXPLORER", color = SerenityAccent)
                }
                Button(onClick = onDownloadMore, colors = ButtonDefaults.buttonColors(containerColor = SerenityAccent), modifier = Modifier.weight(1f)) {
                    Text("DOWNLOADS", color = SerenityBackground)
                }
            }

            HorizontalDivider(color = SerenitySurface)

            Button(
                onClick = { viewModel.clearModels() },
                colors = ButtonDefaults.buttonColors(containerColor = SerenityError),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("PURGE LOCAL CATALOG", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
