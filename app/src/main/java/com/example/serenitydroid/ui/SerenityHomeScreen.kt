package com.example.serenitydroid.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.serenitydroid.ChatViewModel
import com.example.serenitydroid.model.ChatMessage
import com.example.serenitydroid.model.Persona
import com.example.serenitydroid.model.SenderType
import com.example.serenitydroid.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SerenityHomeScreen(
    viewModel: ChatViewModel,
    onOpenSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val activePersona by viewModel.activePersona.collectAsState()
    var userInput by remember { mutableStateOf("") }
    var showInfoDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Scroll to bottom when messages change
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = {
                Text(
                    "HARDWARE & MODEL INFO",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = SerenityAccent
                    )
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "SYSTEM RESOURCE USAGE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = SerenityInference)
                    )
                    Text(
                        "Total System RAM: ${String.format("%.2f GB", uiState.totalMemoryGb)}\n" +
                        "Free System RAM: ${String.format("%.2f GB", uiState.freeMemoryGb)}\n" +
                        "App Heap Memory: ${String.format("%.1f MB", uiState.appMemoryMb)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = SerenityText)
                    )

                    HorizontalDivider(color = SerenitySurface)

                    Text(
                        "HARDWARE ACCELERATION",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = SerenityInference)
                    )
                    Text(
                        "Selected Mode: ${uiState.backendMode.name}\n" +
                        "GPU Offload Layers: ${uiState.gpuLayers}\n" +
                        "Context Size: ${uiState.contextSize}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = SerenityText)
                    )

                    HorizontalDivider(color = SerenitySurface)

                    Text(
                        "ACTIVE MODEL VERBOSE DETAILS",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = SerenityInference)
                    )
                    Text(
                        viewModel.getModelVerboseInfo(),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = SerenityTextDim)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showInfoDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = SerenityAccent)
                ) {
                    Text("BACK", color = SerenityBackground, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = SerenitySurface
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "SERENITY AI CORE",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = SerenityAccent
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "System Info", tint = SerenityAccent)
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO: LIVE MODE */ }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Live", tint = SerenityInference)
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = SerenityAccent)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = SerenityBackground
                )
            )
        },
        containerColor = SerenityBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header: Persona and System Info
            PersonaHeader(
                persona = activePersona,
                modelStatus = uiState.modelStatusText,
                isModelLoaded = uiState.isModelLoaded,
                onSecretTap = {
                    viewModel.setSelectedLevel(6)
                }
            )

            // Progress Indicators (Main screen style from main.py)
            if (uiState.isModelLoading || uiState.isGenerating) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    color = if (uiState.isGenerating) SerenityInference else SerenityAccent,
                    trackColor = SerenitySurface
                )
            }

            // Chat Messages
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    ChatMessageItem(message)
                }
            }

            // App Profiling Overlay Box (shown just above chat input window when enabled)
            if (uiState.isAppProfilingEnabled && uiState.appProfilingText.isNotBlank()) {
                Surface(
                    color = SerenitySurface,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        uiState.appProfilingText,
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = SerenityInference,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            // Input Area
            ChatInputArea(
                value = userInput,
                onValueChange = { userInput = it },
                onSend = {
                    if (userInput.isNotBlank()) {
                        viewModel.sendMessage(userInput)
                        userInput = ""
                    }
                },
                isEnabled = uiState.isModelLoaded && !uiState.isGenerating,
                onBeginHalt = { viewModel.toggleModel() },
                isModelLoaded = uiState.isModelLoaded
            )
            
            // Bottom Controls
            SystemControlsRow(
                uiState = uiState,
                selectedLevel = activePersona.level,
                onApplyLevel = { viewModel.setSelectedLevel(it) },
                onToggleDevice = { viewModel.toggleDevice() },
                onToggleModel = { viewModel.toggleModel() }
            )
        }
    }
}

@Composable
fun PersonaHeader(
    persona: Persona,
    modelStatus: String,
    isModelLoaded: Boolean,
    onSecretTap: () -> Unit
) {
    var tapCount by remember { mutableStateOf(0) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(SerenitySurface, MaterialTheme.shapes.medium)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = persona.imageResId),
            contentDescription = "Avatar",
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            tapCount++
                            if (tapCount >= 3) {
                                tapCount = 0
                                onSecretTap()
                            }
                        }
                    )
                },
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                persona.name,
                style = MaterialTheme.typography.titleMedium.copy(color = SerenityAccent)
            )
            Text(
                persona.description,
                style = MaterialTheme.typography.bodySmall.copy(color = SerenityTextDim),
                maxLines = 2
            )
            Text(
                modelStatus,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = if (isModelLoaded) SerenityAccent else SerenityInference
                )
            )
        }
    }
}

@Composable
fun ChatMessageItem(message: ChatMessage) {
    val alignment = if (message.senderType == SenderType.USER) Alignment.End else Alignment.Start
    val bgColor = when (message.senderType) {
        SenderType.USER -> SerenitySurface
        SenderType.BOT -> Color(0xFF252535)
        SenderType.SYSTEM -> Color.Transparent
    }
    val textColor = when (message.senderType) {
        SenderType.SYSTEM -> SerenityAccent
        else -> SerenityText
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        if (message.senderType != SenderType.SYSTEM) {
            Surface(
                color = bgColor,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Text(
                    message.text,
                    modifier = Modifier.padding(12.dp),
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            Text(
                message.text,
                modifier = Modifier.padding(vertical = 4.dp),
                color = textColor,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun ChatInputArea(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isEnabled: Boolean,
    onBeginHalt: () -> Unit,
    isModelLoaded: Boolean
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Button(
            onClick = onBeginHalt,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isModelLoaded) SerenityError else SerenityAccent,
                contentColor = SerenityBackground
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                if (isModelLoaded) "HALT ENGINE" else "BEGIN INITIALIZATION",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { /* TODO: File / MultiModal */ },
                modifier = Modifier.background(SerenitySurface, CircleShape)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Attachment", tint = SerenityAccent)
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message Serenity...", color = SerenityTextDim) },
                enabled = isEnabled,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SerenityAccent,
                    unfocusedBorderColor = SerenitySurface,
                    cursorColor = SerenityAccent,
                    focusedTextColor = SerenityText,
                    unfocusedTextColor = SerenityText
                ),
                shape = MaterialTheme.shapes.medium
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = isEnabled && value.isNotBlank(),
                modifier = Modifier.background(
                    if (isEnabled && value.isNotBlank()) SerenityAccent else SerenitySurface,
                    CircleShape
                )
            ) {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_menu_send),
                    contentDescription = "Send",
                    tint = SerenityBackground
                )
            }
        }
    }
}

@Composable
fun SystemControlsRow(
    uiState: com.example.serenitydroid.UiState,
    selectedLevel: Int,
    onApplyLevel: (Int) -> Unit,
    onToggleDevice: () -> Unit,
    onToggleModel: () -> Unit
) {
    var secretUnlocked by remember { mutableStateOf(selectedLevel == 6) }
    var draftLevel by remember(selectedLevel) { mutableStateOf(selectedLevel) }

    LaunchedEffect(selectedLevel) {
        if (selectedLevel == 6) secretUnlocked = true
    }

    val maxLevel = if (secretUnlocked || draftLevel == 6) 6f else 5f
    val stepsCount = if (secretUnlocked || draftLevel == 6) 5 else 4

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when {
                    draftLevel != selectedLevel -> if (draftLevel == 0) "LVL 0 (OFFLOAD MODEL)" else "LVL $draftLevel (PRESS LOAD)"
                    selectedLevel == 0 -> "CORE DEPTH: LVL 0 (OFFLOADED)"
                    else -> "CORE DEPTH: LVL $selectedLevel"
                },
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = if (draftLevel != selectedLevel) SerenityInference else SerenityAccent
                ),
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            secretUnlocked = true
                            draftLevel = 6
                        }
                    )
                }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onApplyLevel(draftLevel) },
                    enabled = !uiState.isModelLoading && !uiState.isGenerating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (draftLevel != selectedLevel) SerenityAccent else SerenitySurface
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = when {
                            draftLevel != selectedLevel -> if (draftLevel == 0) "OFFLOAD" else "LOAD LVL $draftLevel"
                            selectedLevel == 0 -> "OFFLOADED"
                            uiState.isModelLoaded -> "RELOAD"
                            else -> "LOAD MODEL"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = if (draftLevel != selectedLevel) SerenityBackground else SerenityAccent
                    )
                }

                Button(
                    onClick = {
                        if (uiState.isModelLoaded) onToggleModel() else onToggleDevice()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SerenitySurface),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        when {
                            uiState.isModelLoaded -> "UNLOAD"
                            uiState.useGpu -> "DEVICE: GPU"
                            else -> "DEVICE: CPU"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            }
        }
        Slider(
            value = draftLevel.toFloat(),
            onValueChange = { value ->
                val newLvl = value.toInt()
                draftLevel = newLvl
                if (newLvl != 6) {
                    secretUnlocked = false
                }
            },
            valueRange = 0f..maxLevel,
            steps = stepsCount,
            colors = SliderDefaults.colors(
                thumbColor = SerenityAccent,
                activeTrackColor = SerenityAccent,
                inactiveTrackColor = SerenitySurface
            )
        )
    }
}

