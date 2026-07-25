package com.example.serenitydroid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.serenitydroid.ChatViewModel
import com.example.serenitydroid.ui.theme.SerenityAccent
import com.example.serenitydroid.ui.theme.SerenityBackground
import com.example.serenitydroid.ui.theme.SerenityInference
import com.example.serenitydroid.ui.theme.SerenitySurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onDownloadMore: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    var selectedModel by remember { mutableStateOf(uiState.customModelPath ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SYSTEM SETTINGS", fontFamily = FontFamily.Monospace, color = SerenityAccent) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("MODEL SELECTION", style = MaterialTheme.typography.titleMedium, color = SerenityAccent, fontWeight = FontWeight.Bold)

            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = if (selectedModel.isBlank()) "Default (Assets)" else selectedModel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Active Model") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SerenityAccent,
                        unfocusedBorderColor = SerenitySurface
                    )
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(SerenitySurface)
                ) {
                    DropdownMenuItem(
                        text = { Text("Default (Assets)") },
                        onClick = {
                            selectedModel = ""
                            viewModel.setCustomModel("")
                            expanded = false
                        }
                    )
                    availableModels.forEach { modelName ->
                        DropdownMenuItem(
                            text = { Text(modelName) },
                            onClick = {
                                selectedModel = modelName
                                viewModel.setCustomModel(modelName)
                                expanded = false
                            }
                        )
                    }
                }
            }

            Button(
                onClick = onDownloadMore,
                colors = ButtonDefaults.buttonColors(containerColor = SerenitySurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("DOWNLOAD / INSTALL MORE MODELS", color = SerenityAccent)
            }

            HorizontalDivider(color = SerenitySurface)

            Text("HARDWARE ACCELERATION (BACKEND)", style = MaterialTheme.typography.titleMedium, color = SerenityAccent, fontWeight = FontWeight.Bold)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.example.serenitydroid.engine.DeviceBackendMode.values().forEach { mode ->
                    val isSelected = uiState.backendMode == mode
                    Button(
                        onClick = { viewModel.setBackendMode(mode) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) SerenityAccent else SerenitySurface
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            mode.name,
                            color = if (isSelected) SerenityBackground else SerenityAccent,
                            fontSize = androidx.compose.ui.unit.TextUnit.Unspecified
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Autoload Model on Startup", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                    Text("Automatically load model into RAM when app opens", style = MaterialTheme.typography.bodySmall, color = SerenityInference)
                }
                Switch(
                    checked = uiState.isAutoLoadOnStartup,
                    onCheckedChange = { viewModel.setAutoLoadOnStartup(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SerenityBackground,
                        checkedTrackColor = SerenityAccent,
                        uncheckedThumbColor = SerenityAccent,
                        uncheckedTrackColor = SerenitySurface
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("App Profiling (Resource Monitor)", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                    Text("Display memory & resource usage box above chat window upon interaction", style = MaterialTheme.typography.bodySmall, color = SerenityInference)
                }
                Switch(
                    checked = uiState.isAppProfilingEnabled,
                    onCheckedChange = { viewModel.setAppProfilingEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SerenityBackground,
                        checkedTrackColor = SerenityAccent,
                        uncheckedThumbColor = SerenityAccent,
                        uncheckedTrackColor = SerenitySurface
                    )
                )
            }

            HorizontalDivider(color = SerenitySurface)

            Text("ENGINE PARAMETERS & MEMORY", style = MaterialTheme.typography.titleMedium, color = SerenityAccent, fontWeight = FontWeight.Bold)

            if (uiState.systemRamText.isNotBlank()) {
                Text(
                    "System RAM: ${uiState.systemRamText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SerenityInference
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (uiState.isAutoGpuLayers) "GPU Offload (Layers): AUTO (${uiState.gpuLayers})" else "GPU Offload (Layers): ${uiState.gpuLayers}",
                    color = androidx.compose.ui.graphics.Color.White
                )
                Button(
                    onClick = { viewModel.setAutoGpuLayers(!uiState.isAutoGpuLayers) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isAutoGpuLayers) SerenityAccent else SerenitySurface
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        "AUTO RAM",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (uiState.isAutoGpuLayers) SerenityBackground else SerenityAccent
                    )
                }
            }

            var layers by remember(uiState.gpuLayers) { mutableStateOf(uiState.gpuLayers.toFloat()) }
            Slider(
                value = layers,
                onValueChange = { layers = it },
                onValueChangeFinished = { viewModel.setGpuLayers(layers.toInt()) },
                valueRange = 0f..64f,
                steps = 63,
                colors = SliderDefaults.colors(thumbColor = SerenityAccent, activeTrackColor = SerenityAccent)
            )

            var ctx by remember(uiState.contextSize) { mutableStateOf(uiState.contextSize.toFloat()) }
            Text("Context Window: ${ctx.toInt()}", color = androidx.compose.ui.graphics.Color.White)
            Slider(
                value = ctx,
                onValueChange = { ctx = (it / 2048f).toInt() * 2048f },
                onValueChangeFinished = { viewModel.setContextSize(ctx.toInt()) },
                valueRange = 2048f..32768f,
                steps = 14,
                colors = SliderDefaults.colors(thumbColor = SerenityAccent, activeTrackColor = SerenityAccent)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}