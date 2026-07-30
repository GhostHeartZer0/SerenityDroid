package com.example.serenitydroid.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.serenitydroid.engine.InferenceManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
        if (uiState.isStickyBottomScroll && messages.isNotEmpty()) {
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
                    IconButton(onClick = { viewModel.clearCurrentSession() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Session", tint = SerenityError)
                    }
                    IconButton(onClick = onOpenParameters) {
                        Icon(Icons.Default.Build, contentDescription = "Parameters", tint = SerenityInference)
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = SerenityAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SerenityBackground)
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
                if (uiState.enablePersonaBackground && activePersona.imageResId != 0) {
                    Image(
                        painter = painterResource(id = activePersona.imageResId),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = 0.12f },
                        contentScale = ContentScale.Fit
                    )
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { message ->
                        ChatMessageItem(message, uiState.chatTextSize)
                    }
                }
                
                // Slim Session History Overlay originating from bottom-right button
                this@Column.AnimatedVisibility(
                    visible = uiState.showHistoryOverlay,
                    enter = fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.2f, transformOrigin = TransformOrigin(0.95f, 0.95f)) + expandIn(expandFrom = Alignment.BottomEnd),
                    exit = fadeOut(animationSpec = tween(180)) + scaleOut(targetScale = 0.2f, transformOrigin = TransformOrigin(0.95f, 0.95f)) + shrinkOut(shrinkTowards = Alignment.BottomEnd),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 60.dp, end = 16.dp)
                ) {
                    SessionHistoryOverlay(viewModel, uiState)
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

                    // Floating History Overlay Toggle Button
                    IconButton(
                        onClick = { viewModel.toggleHistoryOverlay() },
                        modifier = Modifier.background(if (uiState.showHistoryOverlay) SerenityAccent else SerenitySurface, CircleShape).size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = "Session History Overlay",
                            tint = if (uiState.showHistoryOverlay) SerenityBackground else SerenityTextDim,
                            modifier = Modifier.size(18.dp)
                        )
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
                availableLevels = viewModel.getAvailableLevels(),
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
            Surface(color = bgColor, shape = MaterialTheme.shapes.medium, modifier = Modifier.widthIn(max = 350.dp)) {
                Column(modifier = Modifier.padding(10.dp)) {
                    val fullText = InferenceManager.stripControlTags(message.text)
                    
                    // Professional Thought Parser using explicit tag indices
                    val startTag = "<think>"
                    val endTag = "</think>"
                    
                    val startIndex = fullText.indexOf(startTag, ignoreCase = true)
                    val endIndex = fullText.indexOf(endTag, ignoreCase = true)
                    
                    if (startIndex != -1) {
                        val thought: String
                        val answer: String
                        val isThinking: Boolean
                        
                        if (endIndex != -1 && endIndex > startIndex) {
                            // Fully closed block
                            thought = fullText.substring(startIndex + startTag.length, endIndex).trim()
                            // Combine everything before <think> and after </think>
                            val before = fullText.substring(0, startIndex).trim()
                            val after = fullText.substring(endIndex + endTag.length).trim()
                            answer = if (before.isNotEmpty() && after.isNotEmpty()) "$before\n\n$after" else "$before$after"
                            isThinking = false
                        } else {
                            // Currently streaming thought
                            thought = fullText.substring(startIndex + startTag.length).trim()
                            answer = fullText.substring(0, startIndex).trim()
                            isThinking = true
                        }
                        
                        // Show card if there's real content or we are still thinking
                        if (thought.isNotBlank() || isThinking) {
                            var expanded by remember { mutableStateOf(true) }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.35f)),
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
                                            null, tint = SerenityTextDim, modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            if (isThinking) "Thinking..." else "Thought Process",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SerenityTextDim
                                        )
                                    }
                                    AnimatedVisibility(visible = expanded) {
                                        Text(
                                            text = if (thought.isEmpty() && isThinking) "..." else thought,
                                            color = SerenityTextDim,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = (textSize * 0.85f).sp,
                                                fontStyle = FontStyle.Italic,
                                                lineHeight = (textSize * 1.1f).sp
                                            ),
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                        
                        if (answer.isNotEmpty()) {
                            Text(text = answer, color = textColor, style = MaterialTheme.typography.bodyMedium.copy(fontSize = textSize.sp))
                        }
                    } else {
                        // No thought tags
                        Text(text = fullText, color = textColor, style = MaterialTheme.typography.bodyMedium.copy(fontSize = textSize.sp))
                    }
                }
            }
        } else {
            Text(
                message.text,
                modifier = Modifier.padding(vertical = 2.dp),
                color = textColor,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                fontSize = 10.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveModelSelector(viewModel: ChatViewModel, uiState: com.example.serenitydroid.UiState) {
    val availableModels by viewModel.availableModels.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    val selectedModel = uiState.customModelPath ?: ""
    val selectedModelName = if (selectedModel.isBlank()) "Default (Assets)" else selectedModel
    val isDifferentFromLoaded = uiState.isModelLoaded && uiState.loadedModelName != null && selectedModelName != uiState.loadedModelName

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = selectedModelName,
                    onValueChange = {}, readOnly = true, label = { Text("ACTIVE CORE") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true).fillMaxWidth(),
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
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { viewModel.updateAvailableModels() },
                modifier = Modifier.background(SerenitySurface, CircleShape).size(40.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh Catalog", tint = SerenityAccent, modifier = Modifier.size(20.dp))
            }
        }

        if (isDifferentFromLoaded) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp),
                shape = RoundedCornerShape(20.dp),
                color = SerenityInference,
                shadowElevation = 6.dp
            ) {
                Button(
                    onClick = { viewModel.loadModel() },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SerenityInference),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        Icons.Default.SyncAlt,
                        contentDescription = "Swap Core",
                        tint = SerenityBackground,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Swap?",
                        color = SerenityBackground,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
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
fun SessionHistoryOverlay(viewModel: ChatViewModel, uiState: com.example.serenitydroid.UiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SerenitySurface.copy(alpha = 0.98f)),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "SESSION HISTORY",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = SerenityAccent)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { viewModel.createNewSession() }) {
                        Icon(Icons.Default.Add, contentDescription = "New Session", tint = SerenityAccent)
                    }
                    IconButton(onClick = { viewModel.toggleHistoryOverlay() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close Overlay", tint = SerenityTextDim)
                    }
                }
            }

            HorizontalDivider(color = SerenityBackground, modifier = Modifier.padding(vertical = 4.dp))

            if (uiState.sessions.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No saved sessions", color = SerenityTextDim, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(uiState.sessions) { session ->
                        val isSelected = session.id == uiState.currentSessionId
                        val formattedDate = remember(session.timestamp) {
                            java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.US).format(java.util.Date(session.timestamp))
                        }
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = if (isSelected) SerenityAccent.copy(alpha = 0.15f) else SerenityBackground),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.loadSession(session.id) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        session.title,
                                        color = if (isSelected) SerenityAccent else Color.White,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1
                                    )
                                    Text(
                                        "$formattedDate • ${session.messages.size} msgs",
                                        color = SerenityTextDim,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.deleteSession(session.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Session", tint = SerenityError, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = SerenityBackground, modifier = Modifier.padding(vertical = 4.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { viewModel.clearCurrentSession() }) {
                    Text("CLEAR CURRENT", color = SerenityError, fontSize = 11.sp)
                }
                TextButton(onClick = { viewModel.clearAllSessions() }) {
                    Text("CLEAR ALL HISTORY", color = SerenityError, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun SystemControlsRow(
    selectedLevel: Int,
    availableLevels: List<Int>,
    onApplyLevel: (Int) -> Unit
) {
    if (availableLevels.isEmpty()) return
    val currentIndex = availableLevels.indexOf(selectedLevel).coerceAtLeast(0)
    val maxIndex = (availableLevels.size - 1).coerceAtLeast(1)
    val steps = if (availableLevels.size > 2) availableLevels.size - 2 else 0

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("PERFORMANCE LEVEL: LVL $selectedLevel", style = MaterialTheme.typography.labelSmall, color = SerenityAccent, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }
        Slider(
            value = currentIndex.toFloat(),
            onValueChange = { idx ->
                val targetIndex = idx.toInt().coerceIn(0, availableLevels.size - 1)
                onApplyLevel(availableLevels[targetIndex])
            },
            valueRange = 0f..maxIndex.toFloat(),
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(thumbColor = SerenityAccent, activeTrackColor = SerenityAccent)
        )
    }
}
