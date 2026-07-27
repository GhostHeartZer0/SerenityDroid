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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
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
    onOpenSettings: () -> Unit,
    onOpenParameters: () -> Unit = {},
    onOpenDocs: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val activePersona by viewModel.activePersona.collectAsState()
    var userInput by remember { mutableStateOf("") }
    var showInfoDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (uiState.isScrollLocked && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("SERENITY AI CORE", style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = SerenityAccent))
                },
                navigationIcon = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "System Info", tint = SerenityAccent)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenDocs) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Documentation", tint = SerenityInference)
                    }
                    IconButton(onClick = onOpenParameters) {
                        Icon(Icons.Default.Build, contentDescription = "Parameters", tint = SerenityInference)
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = SerenityAccent)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = SerenityBackground)
            )
        },
        containerColor = SerenityBackground
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ActiveModelSelector(viewModel, uiState)

            PersonaHeader(
                persona = activePersona,
                modelStatus = uiState.modelStatusText,
                isModelLoaded = uiState.isModelLoaded
            )

            // Manual Load/Unload Control
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!uiState.isModelLoaded) {
                    Button(
                        onClick = { viewModel.loadModel() },
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isModelLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = SerenityAccent)
                    ) {
                        Text(if (uiState.isModelLoading) "INITIALIZING..." else "LOAD CORE", color = SerenityBackground, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { viewModel.offloadModel() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SerenityError)
                    ) {
                        Text("UNLOAD CORE", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (uiState.isModelLoading || uiState.isGenerating) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    color = if (uiState.isGenerating) SerenityInference else SerenityAccent,
                    trackColor = SerenitySurface
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { message ->
                        ChatMessageItem(message, uiState.chatTextSize)
                    }
                }
                
                // Floating tools column
                Column(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Generation Details Toggle
                    if (uiState.generationMetrics != null) {
                        if (uiState.showMetrics) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SerenitySurface.copy(alpha = 0.95f)),
                                modifier = Modifier.widthIn(max = 220.dp),
                                shape = RoundedCornerShape(8.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                            ) {
                                Text(
                                    uiState.generationMetrics!!,
                                    modifier = Modifier.padding(10.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, color = SerenityAccent, fontSize = 10.sp)
                                )
                            }
                        }
                        IconButton(
                            onClick = { viewModel.toggleMetrics() },
                            modifier = Modifier.background(SerenitySurface, CircleShape).size(36.dp)
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = "Metrics", tint = SerenityInference, modifier = Modifier.size(18.dp))
                        }
                    }

                    IconButton(
                        onClick = { viewModel.toggleScrollLock() },
                        modifier = Modifier.background(if (uiState.isScrollLocked) SerenityAccent else SerenitySurface, CircleShape).size(36.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Scroll Lock", tint = if (uiState.isScrollLocked) SerenityBackground else SerenityTextDim, modifier = Modifier.size(18.dp))
                    }
                }
            }

            ChatInputArea(
                value = userInput,
                onValueChange = { userInput = it },
                onSend = {
                    if (userInput.isNotBlank()) {
                        viewModel.sendMessage(userInput)
                        userInput = ""
                    }
                },
                isEnabled = uiState.isModelLoaded && !uiState.isGenerating
            )
            
            SystemControlsRow(
                selectedLevel = activePersona.level,
                onApplyLevel = { viewModel.setSelectedLevel(it) }
            )
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("HARDWARE INFO", color = SerenityAccent) },
            text = { Text(viewModel.getSystemMemoryStats(), color = Color.White) },
            confirmButton = { TextButton(onClick = { showInfoDialog = false }) { Text("OK") } },
            containerColor = SerenitySurface
        )
    }

    if (uiState.conversionOfferModel != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissConversionOffer() },
            title = { Text("REGISTER MODEL?", color = SerenityInference) },
            text = { Text("Model ${uiState.conversionOfferModel?.name} failed to load. Register it properly in the catalog?", color = Color.White) },
            confirmButton = {
                Button(onClick = { viewModel.dismissConversionOffer(); onOpenSettings() }, colors = ButtonDefaults.buttonColors(containerColor = SerenityAccent)) {
                    Text("OPEN CATALOG", color = SerenityBackground)
                }
            },
            dismissButton = { TextButton(onClick = { viewModel.dismissConversionOffer() }) { Text("CANCEL") } },
            containerColor = SerenitySurface
        )
    }
}

@Composable
fun PersonaHeader(persona: Persona, modelStatus: String, isModelLoaded: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp).background(SerenitySurface, MaterialTheme.shapes.medium).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = persona.imageResId),
            contentDescription = "Avatar",
            modifier = Modifier.size(48.dp).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(persona.name, style = MaterialTheme.typography.titleSmall.copy(color = SerenityAccent))
            Text(modelStatus, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, color = if (isModelLoaded) SerenityAccent else SerenityInference))
        }
    }
}

@Composable
fun ChatMessageItem(message: ChatMessage, textSize: Float) {
    val alignment = if (message.senderType == SenderType.USER) Alignment.End else Alignment.Start
    val bgColor = when (message.senderType) {
        SenderType.USER -> SerenitySurface
        SenderType.BOT -> Color(0xFF252535)
        else -> Color.Transparent
    }
    val textColor = if (message.senderType == SenderType.SYSTEM) SerenityAccent else Color.White

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        if (message.senderType != SenderType.SYSTEM) {
            Surface(color = bgColor, shape = MaterialTheme.shapes.medium, modifier = Modifier.widthIn(max = 300.dp)) {
                Column(modifier = Modifier.padding(10.dp)) {
                    val processedText = message.text
                    val hasThinkStart = processedText.contains("<think>")
                    val hasThinkEnd = processedText.contains("</think>")
                    
                    if (hasThinkStart) {
                        val thought = if (hasThinkEnd) {
                            processedText.substringAfter("<think>").substringBefore("</think>")
                        } else {
                            processedText.substringAfter("<think>")
                        }
                        
                        val answer = if (hasThinkEnd) {
                            processedText.substringAfter("</think>").trim()
                        } else ""
                        
                        var expanded by remember { mutableStateOf(true) }
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
                                ) {
                                    Icon(
                                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = SerenityTextDim,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Thought Process", style = MaterialTheme.typography.labelSmall, color = SerenityTextDim)
                                }
                                AnimatedVisibility(visible = expanded) {
                                    Text(
                                        text = thought.trim(),
                                        color = SerenityTextDim,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = (textSize * 0.85f).sp, fontStyle = FontStyle.Italic),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                        if (answer.isNotEmpty()) {
                            Text(text = answer, color = textColor, style = MaterialTheme.typography.bodyMedium.copy(fontSize = textSize.sp))
                        }
                    } else {
                        Text(text = processedText, color = textColor, style = MaterialTheme.typography.bodyMedium.copy(fontSize = textSize.sp))
                    }
                }
            }
        } else {
            Text(message.text, modifier = Modifier.padding(vertical = 2.dp), color = textColor, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), fontSize = 10.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveModelSelector(viewModel: ChatViewModel, uiState: com.example.serenitydroid.UiState) {
    val availableModels by viewModel.availableModels.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    val selectedModel = uiState.customModelPath ?: ""

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = if (selectedModel.isBlank()) "SELECT MODEL" else selectedModel,
                onValueChange = {}, readOnly = true, label = { Text("ACTIVE CORE") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SerenityAccent, unfocusedBorderColor = SerenitySurface, disabledTextColor = Color.White),
                enabled = !uiState.isModelLoading && !uiState.isGenerating
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(SerenitySurface)) {
                DropdownMenuItem(text = { Text("Default (Assets)", color = Color.White) }, onClick = { viewModel.setCustomModel(""); expanded = false })
                availableModels.forEach { modelFile ->
                    DropdownMenuItem(
                        text = { Row { Text(modelFile.name, modifier = Modifier.weight(1f), color = Color.White); Text(modelFile.getTopCompatibleEngine(LocalContext.current), style = MaterialTheme.typography.labelSmall, color = SerenityInference) } },
                        onClick = { viewModel.setCustomModel(modelFile.file.name); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
fun ChatInputArea(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit, isEnabled: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value, onValueChange = onValueChange, modifier = Modifier.weight(1f),
            placeholder = { Text("Command Serenity...", color = SerenityTextDim) },
            enabled = isEnabled,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SerenityAccent, unfocusedBorderColor = SerenitySurface, cursorColor = SerenityAccent, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
            shape = MaterialTheme.shapes.medium
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onSend, enabled = isEnabled && value.isNotBlank(), modifier = Modifier.background(if (isEnabled && value.isNotBlank()) SerenityAccent else SerenitySurface, CircleShape)) {
            Icon(painter = painterResource(id = android.R.drawable.ic_menu_send), contentDescription = "Send", tint = SerenityBackground)
        }
    }
}

@Composable
fun SystemControlsRow(selectedLevel: Int, onApplyLevel: (Int) -> Unit) {
    Slider(
        value = selectedLevel.toFloat(),
        onValueChange = { onApplyLevel(it.toInt()) },
        valueRange = 1f..5f,
        steps = 3,
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
        colors = SliderDefaults.colors(thumbColor = SerenityAccent, activeTrackColor = SerenityAccent)
    )
}
