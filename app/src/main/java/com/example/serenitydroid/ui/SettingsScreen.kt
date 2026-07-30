package com.example.serenitydroid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
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
    onOpenDocs: () -> Unit = {},
    onDownloadMore: () -> Unit = {},
    onBrowseFileExplorer: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    var showNpuDialog by remember { mutableStateOf(false) }

    if (showNpuDialog) {
        val diag = viewModel.getNpuDiagnosticInfo()
        AlertDialog(
            onDismissRequest = { showNpuDialog = false },
            title = { Text("NPU DIAGNOSTICS & READINESS", color = SerenityAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Overall NPU Capable: ${if (diag.isNpuCapable) "YES" else "NO"}", color = if (diag.isNpuCapable) SerenityAccent else SerenityError, fontWeight = FontWeight.Bold)
                    Text("• Dynamic Feature Installed: ${diag.genieXModuleInstalled}")
                    Text("• libgeniex.so Present: ${diag.hasGenieXCore}")
                    Text("• libgeniex_plugin_llama_cpp.so: ${diag.hasLlamaPlugin}")
                    Text("• libgeniex_plugin_qairt.so: ${diag.hasQairtPlugin}")
                    Text("• libQnnHtp.so Present: ${diag.hasQnnHtp}")
                    Text("• OpenMP (libomp.so) Loaded: ${diag.hasOpenMp}")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Native Lib Dir: ${diag.nativeLibDir}", fontSize = 10.sp, color = SerenityTextDim, fontFamily = FontFamily.Monospace)
                }
            },
            confirmButton = {
                TextButton(onClick = { showNpuDialog = false }) {
                    Text("CLOSE", color = SerenityAccent)
                }
            },
            containerColor = SerenitySurface
        )
    }

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
            Text("HARDWARE DEVICE BACKEND", style = MaterialTheme.typography.titleMedium, color = SerenityAccent, fontWeight = FontWeight.Bold)
            Text("Select preferred accelerator. The engine (Genie/LiteRT/Llama) will be chosen automatically based on model format.", style = MaterialTheme.typography.bodySmall, color = SerenityTextDim)
            
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

            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = { showNpuDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SerenityAccent)
            ) {
                Text("RUN NPU READINESS DIAGNOSTICS", fontWeight = FontWeight.Bold)
            }

            HorizontalDivider(color = SerenitySurface)

            Text("KV CACHE SIZE (CONTEXT MEMORY)", style = MaterialTheme.typography.titleMedium, color = SerenityAccent, fontWeight = FontWeight.Bold)
            Text("Lower values (2048-4096) reduce RAM usage & prevent speed bottlenecks on mobile CPU.", style = MaterialTheme.typography.bodySmall, color = SerenityTextDim)
            
            val kvOptions = listOf(2048, 4096, 8192, 16384, 32768, 65536)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                kvOptions.forEach { size ->
                    val isSelected = uiState.kvCacheSize == size
                    Button(
                        onClick = { viewModel.setKvCacheSize(size) },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isSelected) SerenityAccent else SerenitySurface),
                        modifier = Modifier.weight(1f).height(38.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        Text(if (size >= 1024) "${size / 1024}K" else "$size", color = if (isSelected) SerenityBackground else SerenityAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text("KV CACHE QUANTIZATION", style = MaterialTheme.typography.titleMedium, color = SerenityAccent, fontWeight = FontWeight.Bold)
            KvQuantSelector("K Cache", uiState.kvQuantK) { viewModel.setParameter(kvK = it) }
            KvQuantSelector("V Cache", uiState.kvQuantV) { viewModel.setParameter(kvV = it) }

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
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sticky Bottom Scroll", color = Color.White, fontWeight = FontWeight.Medium)
                            Text("Automatically scroll to bottom when new messages arrive", style = MaterialTheme.typography.bodySmall, color = SerenityTextDim)
                        }
                        Switch(
                            checked = uiState.isStickyBottomScroll,
                            onCheckedChange = { viewModel.setStickyBottomScroll(it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = SerenityAccent)
                        )
                    }
                    HorizontalDivider(color = SerenityBackground)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Faded Persona Background", color = Color.White, fontWeight = FontWeight.Medium)
                            Text("Display active level background image behind chat area", style = MaterialTheme.typography.bodySmall, color = SerenityTextDim)
                        }
                        Switch(
                            checked = uiState.enablePersonaBackground,
                            onCheckedChange = { viewModel.setEnablePersonaBackground(it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = SerenityAccent)
                        )
                    }
                    HorizontalDivider(color = SerenityBackground)
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

            Text("DOCUMENTATION & MARKDOWN VIEWER", style = MaterialTheme.typography.titleMedium, color = SerenityAccent, fontWeight = FontWeight.Bold)
            Card(colors = CardDefaults.cardColors(containerColor = SerenitySurface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("View system documentation, release notes, and markdown files.", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onOpenDocs,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SerenityInference)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Open Book", tint = SerenityBackground, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("OPEN MARKDOWN VIEWER", color = SerenityBackground, fontWeight = FontWeight.Bold)
                    }
                }
            }

            HorizontalDivider(color = SerenitySurface)

            Text("MODEL ACQUISITION", style = MaterialTheme.typography.titleMedium, color = SerenityAccent, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.updateAvailableModels() }, colors = ButtonDefaults.buttonColors(containerColor = SerenitySurface), modifier = Modifier.weight(1f)) {
                    Text("REFRESH CATALOG", color = SerenityAccent)
                }
                Button(onClick = onDownloadMore, colors = ButtonDefaults.buttonColors(containerColor = SerenityAccent), modifier = Modifier.weight(1f)) {
                    Text("DOWNLOADS", color = SerenityBackground)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onBrowseFileExplorer, colors = ButtonDefaults.buttonColors(containerColor = SerenitySurface), modifier = Modifier.weight(1f)) {
                    Text("FILE EXPLORER", color = SerenityAccent)
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
