package com.omnimind.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnimind.data.entity.MessageEntity
import com.omnimind.ui.models.ModelPickerBottomSheet
import com.omnimind.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var inputText by remember { mutableStateOf("") }
    var showModelPicker by remember { mutableStateOf(false) }
    var showTuneDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // Auto-scroll to bottom when messages update or streaming text arrives
    LaunchedEffect(uiState.messages.size, uiState.streamingText) {
        if (uiState.messages.isNotEmpty() || uiState.streamingText.isNotEmpty()) {
            val totalItems = uiState.messages.size + (if (uiState.isGenerating) 1 else 0)
            if (totalItems > 0) {
                listState.animateScrollToItem(totalItems - 1)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.conversationTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showModelPicker = true }
                                .padding(vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (uiState.loadedModel != null) SuccessGreen else WarningAmber,
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = uiState.loadedModel?.name ?: "No Model (Tap to Select)",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (uiState.loadedModel != null) PrimaryTeal else WarningAmber,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.newConversation() }) {
                        Icon(Icons.Default.AddComment, contentDescription = "New Conversation", tint = PrimaryTeal)
                    }
                    IconButton(onClick = { showTuneDialog = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "Tuning", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Regenerate Response") },
                                onClick = {
                                    showMenu = false
                                    viewModel.regenerate()
                                },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                enabled = !uiState.isGenerating && uiState.messages.isNotEmpty()
                            )
                            DropdownMenuItem(
                                text = { Text("Clear Conversation") },
                                onClick = {
                                    showMenu = false
                                    viewModel.clearConversation()
                                },
                                leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Error banner if any
            AnimatedVisibility(visible = uiState.errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.dismissError() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Chat Messages List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                if (uiState.messages.isEmpty() && !uiState.isGenerating) {
                    item {
                        EmptyChatState(
                            modelName = uiState.loadedModel?.name,
                            onModelClick = { showModelPicker = true }
                        )
                    }
                }

                items(uiState.messages) { message ->
                    MessageBubble(message = message)
                }

                // Active Streaming Response
                if (uiState.isGenerating && uiState.streamingText.isNotEmpty()) {
                    item {
                        ActiveStreamingBubble(
                            text = uiState.streamingText,
                            stats = uiState.activeStats
                        )
                    }
                }
            }

            // Input Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                "Message OmniMind (offline)...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                fontSize = 14.sp
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp, max = 120.dp),
                        enabled = true
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    if (uiState.isGenerating) {
                        IconButton(
                            onClick = { viewModel.stopGeneration() },
                            modifier = Modifier
                                .size(44.dp)
                                .background(ErrorRed, CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = "Stop",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    val textToSend = inputText
                                    inputText = ""
                                    viewModel.sendMessage(textToSend)
                                }
                            },
                            enabled = inputText.isNotBlank(),
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    if (inputText.isNotBlank()) PrimaryTeal else MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape
                                )
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = "Send",
                                tint = if (inputText.isNotBlank()) BackgroundDark else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Model Picker Bottom Sheet
    if (showModelPicker) {
        ModelPickerBottomSheet(
            models = uiState.availableModels,
            activeModelId = uiState.loadedModel?.id,
            onModelSelected = { modelId -> viewModel.loadModel(modelId) },
            onUnloadClicked = { viewModel.unloadModel() },
            onDismiss = { showModelPicker = false }
        )
    }

    // Quick Tuning Dialog
    if (showTuneDialog) {
        TuneSettingsDialog(
            systemPrompt = uiState.systemPrompt,
            temperature = uiState.temperature,
            contextLength = uiState.contextLength,
            onSave = { prompt, temp, ctx ->
                viewModel.updateSystemPrompt(prompt)
                viewModel.updateTemperature(temp)
                viewModel.updateContextLength(ctx)
                showTuneDialog = false
            },
            onDismiss = { showTuneDialog = false }
        )
    }
}

@Composable
fun MessageBubble(message: MessageEntity) {
    val isUser = message.role == "user"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isUser) UserBubbleDark else AssistantBubbleDark,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp
                )

                if (!isUser && message.generationStatsJson != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = formatStatsJson(message.generationStatsJson),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveStreamingBubble(
    text: String,
    stats: com.omnimind.domain.model.InferenceStats?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            color = AssistantBubbleDark,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp
                )

                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pulsing dot
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dotAlpha"
                    )

                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(PrimaryTeal.copy(alpha = alpha), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (stats != null && stats.generationTokensPerSec > 0f) {
                            String.format("%.1f tokens/sec", stats.generationTokensPerSec)
                        } else {
                            "Generating..."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = PrimaryTeal,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyChatState(
    modelName: String?,
    onModelClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = CircleShape,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Psychology,
                        contentDescription = null,
                        tint = PrimaryTeal,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "OmniMind Offline Intelligence",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (modelName != null) {
                    "Ready with local model: $modelName"
                } else {
                    "No model loaded. Tap below to select or import."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(onClick = onModelClick) {
                Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (modelName != null) "Change Model" else "Choose Model")
            }
        }
    }
}

@Composable
fun TuneSettingsDialog(
    systemPrompt: String,
    temperature: Float,
    contextLength: Int,
    onSave: (String, Float, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var prompt by remember { mutableStateOf(systemPrompt) }
    var temp by remember { mutableStateOf(temperature) }
    var ctx by remember { mutableStateOf(contextLength.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Conversation Settings") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("System Prompt:", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    textStyle = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text("Temperature: ${String.format("%.2f", temp)}", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = temp,
                    onValueChange = { temp = it },
                    valueRange = 0.0f..1.5f,
                    steps = 14
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text("Context Length: ${ctx.toInt()} tokens", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = ctx,
                    onValueChange = { ctx = it },
                    valueRange = 1024f..8192f,
                    steps = 6
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(prompt, temp, ctx.toInt()) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatStatsJson(json: String): String {
    return try {
        // e.g. {"ttft":240,"gen_tps":18.4,"prompt_tps":45.2,"tokens":142}
        json.removeSurrounding("{", "}").split(",").joinToString(" • ") { part ->
            val kv = part.split(":")
            val key = kv[0].trim().replace("\"", "")
            val value = kv.getOrNull(1)?.trim() ?: ""
            when (key) {
                "ttft" -> "TTFT: ${value}ms"
                "gen_tps" -> "${value} t/s"
                "tokens" -> "${value} tokens"
                else -> "$key: $value"
            }
        }
    } catch (e: Exception) {
        ""
    }
}
