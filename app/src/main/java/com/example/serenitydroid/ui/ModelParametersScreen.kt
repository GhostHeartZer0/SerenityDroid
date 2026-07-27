package com.example.serenitydroid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults
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
fun ModelParametersScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CORE PARAMETERS", fontFamily = FontFamily.Monospace, color = SerenityAccent) },
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
        Column(modifier = Modifier.padding(padding)) {
            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = SerenitySurface,
                contentColor = SerenityAccent,
                indicator = {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(selectedTab),
                        color = SerenityAccent
                    )
                }
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("SAMPLING") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("HARDWARE") })
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (selectedTab == 0) {
                    SamplingTab(viewModel, uiState)
                } else {
                    HardwareTab(viewModel, uiState)
                }
            }
        }
    }
}

@Composable
fun SamplingTab(viewModel: ChatViewModel, state: com.example.serenitydroid.UiState) {
    ParameterSlider(
        label = "Temperature",
        value = state.temperature,
        range = 0f..2f,
        onValueChange = { viewModel.setParameter(temp = it) },
        description = "Higher = more creative, Lower = more factual"
    )

    ParameterSlider(
        label = "Top-K",
        value = state.topK.toFloat(),
        range = 1f..100f,
        steps = 100,
        onValueChange = { viewModel.setParameter(k = it.toInt()) },
        description = "Sample from the top K most likely tokens"
    )

    ParameterSlider(
        label = "Top-P (Nucleus)",
        value = state.topP,
        range = 0f..1f,
        onValueChange = { viewModel.setParameter(p = it) },
        description = "Cumulative probability threshold for sampling"
    )

    ParameterSlider(
        label = "Min-P",
        value = state.minP,
        range = 0f..1f,
        onValueChange = { viewModel.setParameter(minP = it) },
        description = "Minimum probability relative to the most likely token"
    )

    ParameterSlider(
        label = "Frequency Penalty",
        value = state.frequencyPenalty,
        range = 0f..2f,
        onValueChange = { viewModel.setParameter(freq = it) },
        description = "Penalize tokens based on their frequency in text so far"
    )

    ParameterSlider(
        label = "Presence Penalty",
        value = state.presencePenalty,
        range = 0f..2f,
        onValueChange = { viewModel.setParameter(pres = it) },
        description = "Penalize tokens based on whether they have appeared at all"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HardwareTab(viewModel: ChatViewModel, state: com.example.serenitydroid.UiState) {
    Text("ENGINE OPTIMIZATION", color = SerenityAccent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Multi-Token Prediction (MTP)", color = Color.White)
            Text("Accelerates decoding on supported hardware", style = MaterialTheme.typography.bodySmall, color = SerenityInference)
        }
        Switch(
            checked = state.enableMtp,
            onCheckedChange = { viewModel.setParameter(mtp = it) },
            colors = SwitchDefaults.colors(checkedTrackColor = SerenityAccent)
        )
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Split KV Cache", color = Color.White)
            Text("Optimizes memory usage for long contexts", style = MaterialTheme.typography.bodySmall, color = SerenityInference)
        }
        Switch(
            checked = state.enableSplitKv,
            onCheckedChange = { viewModel.setParameter(splitKv = it) },
            colors = SwitchDefaults.colors(checkedTrackColor = SerenityAccent)
        )
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Attention Caching", color = Color.White)
            Text("Enables faster multi-turn conversations", style = MaterialTheme.typography.bodySmall, color = SerenityInference)
        }
        Switch(
            checked = state.enableAttentionCaching,
            onCheckedChange = { viewModel.setParameter(attnCache = it) },
            colors = SwitchDefaults.colors(checkedTrackColor = SerenityAccent)
        )
    }

    ParameterSlider(
        label = "KV Cache Size",
        value = state.kvCacheSize.toFloat(),
        range = 512f..16384f,
        steps = 31,
        onValueChange = { viewModel.setParameter(kvSize = it.toInt()) },
        description = "Total context memory allocation (tokens)"
    )

    ParameterSlider(
        label = "Internal Buffer Size",
        value = state.bufferSize.toFloat(),
        range = 256f..8192f,
        steps = 31,
        onValueChange = { viewModel.setParameter(bufSize = it.toInt()) },
        description = "Engine scratchpad size for prefill/decode"
    )

    ParameterSlider(
        label = "Sequence Batch Size",
        value = state.sequenceBatchSize.toFloat(),
        range = 1f..16f,
        steps = 15,
        onValueChange = { viewModel.setParameter(batchSize = it.toInt()) },
        description = "Parallel token processing density"
    )

    HorizontalDivider(color = SerenitySurface)

    Text("COMPATIBILITY VERIFICATION", color = SerenityAccent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    
    val hwInfo = viewModel.deviceDetector.getDeviceHardwareInfo()
    Card(colors = CardDefaults.cardColors(containerColor = SerenitySurface)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CompatibilityItem("NPU Support", hwInfo.isNpuSupported)
            CompatibilityItem("High RAM Available", hwInfo.availableRamGb > 4f)
            CompatibilityItem("GPU Acceleration", state.useGpu)
        }
    }
}

@Composable
fun ParameterSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
    description: String
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White, fontWeight = FontWeight.Medium)
            Text(java.util.Locale.US.let { String.format(it, "%.2f", value) }, color = SerenityAccent, fontFamily = FontFamily.Monospace)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(thumbColor = SerenityAccent, activeTrackColor = SerenityAccent)
        )
        Text(description, style = MaterialTheme.typography.bodySmall, color = SerenityTextDim)
    }
}

@Composable
fun CompatibilityItem(label: String, isOk: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(
            if (isOk) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (isOk) SerenityAccent else SerenityError,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}
