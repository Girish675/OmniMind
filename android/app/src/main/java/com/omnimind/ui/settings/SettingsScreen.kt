package com.omnimind.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnimind.ui.theme.PrimaryTeal
import com.omnimind.ui.theme.SecondaryIndigo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inference Settings", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { viewModel.resetToDefaults() }) {
                        Icon(Icons.Default.Restore, contentDescription = "Reset Defaults")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Compute Backend Selection
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Compute Backend", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Baseline CPU ARM64 NEON is verified on Snapdragon 7s Gen 2. Optional GPU backends will fallback to CPU if initialization fails.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    uiState.availableBackends.forEach { backend ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = uiState.selectedBackend == backend.name,
                                onClick = { viewModel.selectBackend(backend.name) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(backend.name, style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                Text(backend.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // CPU Thread Allocation
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("CPU Threads", style = MaterialTheme.typography.titleMedium)
                        Text("${uiState.settings.threads} threads", style = MaterialTheme.typography.labelMedium, color = PrimaryTeal)
                    }
                    Text(
                        "Target device has 4 Performance (A78) + 4 Efficiency (A55) cores. 4 threads recommended.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = uiState.settings.threads.toFloat(),
                        onValueChange = { viewModel.updateSettings(uiState.settings.copy(threads = it.toInt())) },
                        valueRange = 1f..8f,
                        steps = 6
                    )
                }
            }

            // Context Size & Batch Size
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Context Window", style = MaterialTheme.typography.titleMedium)
                        Text("${uiState.settings.contextSize} tokens", style = MaterialTheme.typography.labelMedium, color = PrimaryTeal)
                    }
                    Slider(
                        value = uiState.settings.contextSize.toFloat(),
                        onValueChange = { viewModel.updateSettings(uiState.settings.copy(contextSize = it.toInt())) },
                        valueRange = 1024f..8192f,
                        steps = 6
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Batch Size", style = MaterialTheme.typography.titleMedium)
                        Text("${uiState.settings.batchSize}", style = MaterialTheme.typography.labelMedium, color = SecondaryIndigo)
                    }
                    Slider(
                        value = uiState.settings.batchSize.toFloat(),
                        onValueChange = { viewModel.updateSettings(uiState.settings.copy(batchSize = it.toInt())) },
                        valueRange = 128f..1024f,
                        steps = 6
                    )
                }
            }

            // Sampling Parameters
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Sampling Parameters", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Temperature
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Temperature", style = MaterialTheme.typography.bodyMedium)
                        Text(String.format("%.2f", uiState.settings.temperature), style = MaterialTheme.typography.labelMedium, color = PrimaryTeal)
                    }
                    Slider(
                        value = uiState.settings.temperature,
                        onValueChange = { viewModel.updateSettings(uiState.settings.copy(temperature = it)) },
                        valueRange = 0.0f..1.5f,
                        steps = 14
                    )

                    // Top-P
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Top-P (Nucleus)", style = MaterialTheme.typography.bodyMedium)
                        Text(String.format("%.2f", uiState.settings.topP), style = MaterialTheme.typography.labelMedium, color = PrimaryTeal)
                    }
                    Slider(
                        value = uiState.settings.topP,
                        onValueChange = { viewModel.updateSettings(uiState.settings.copy(topP = it)) },
                        valueRange = 0.1f..1.0f,
                        steps = 8
                    )

                    // Top-K
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Top-K", style = MaterialTheme.typography.bodyMedium)
                        Text("${uiState.settings.topK}", style = MaterialTheme.typography.labelMedium, color = PrimaryTeal)
                    }
                    Slider(
                        value = uiState.settings.topK.toFloat(),
                        onValueChange = { viewModel.updateSettings(uiState.settings.copy(topK = it.toInt())) },
                        valueRange = 1f..100f,
                        steps = 19
                    )

                    // Repeat Penalty
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Repeat Penalty", style = MaterialTheme.typography.bodyMedium)
                        Text(String.format("%.2f", uiState.settings.repeatPenalty), style = MaterialTheme.typography.labelMedium, color = PrimaryTeal)
                    }
                    Slider(
                        value = uiState.settings.repeatPenalty,
                        onValueChange = { viewModel.updateSettings(uiState.settings.copy(repeatPenalty = it)) },
                        valueRange = 1.0f..1.5f,
                        steps = 9
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
