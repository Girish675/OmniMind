package com.omnimind.ui.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.omnimind.data.entity.BenchmarkEntity
import com.omnimind.domain.util.StorageUtils
import com.omnimind.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshHardwareState()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics & Benchmarks", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { viewModel.refreshHardwareState() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Warnings Banner if any detected
            if (uiState.warningMessages.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("System Advisories", style = MaterialTheme.typography.titleMedium, color = WarningAmber)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            uiState.warningMessages.forEach { warn ->
                                Text(
                                    text = "• $warn",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Real Measured Performance Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Active Generation Metrics", style = MaterialTheme.typography.titleMedium)
                            Surface(
                                color = PrimaryTeal.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "REAL HARDWARE CLOCK",
                                    color = PrimaryTeal,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        val stats = uiState.latestStats
                        if (stats != null) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                MetricItem("Generation Rate", String.format("%.2f t/s", stats.generationTokensPerSec), PrimaryTeal)
                                MetricItem("Time to First Token", "${stats.timeToFirstTokenMs} ms", SecondaryIndigo)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                MetricItem("Prompt Processing", String.format("%.2f t/s", stats.promptTokensPerSec), AccentCyan)
                                MetricItem("Total Generated", "${stats.generatedTokens} tokens", MaterialTheme.colorScheme.onSurface)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                MetricItem("Model Load Latency", "${stats.loadTimeMs} ms", MaterialTheme.colorScheme.onSurfaceVariant)
                                MetricItem("Total Duration", "${stats.totalTimeMs} ms", MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Text(
                                text = "Run a generation in Chat to record live throughput and latency measurements.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Active Execution Configuration
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Active Engine Configuration", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(10.dp))

                        DiagRow("Active Model", uiState.activeModel?.name ?: "None loaded")
                        DiagRow("Quantization", uiState.activeModel?.quantization ?: "N/A")
                        DiagRow("Active Backend", uiState.activeBackend)
                        DiagRow("Thread Allocation", "${uiState.activeSettings.threads} threads (Snapdragon 7s Gen 2 Cortex-A78)")
                        DiagRow("Context Window", "${uiState.activeSettings.contextSize} tokens")
                        DiagRow("Batch Size", "${uiState.activeSettings.batchSize}")
                        DiagRow("GPU Offload Layers", "${uiState.activeSettings.gpuLayers} (0 = CPU ARM64)")
                    }
                }
            }

            // Device Resource Utilization
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Device Resource Utilization", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(10.dp))

                        DiagRow("Native Heap (C++/mmap)", StorageUtils.formatBytes(uiState.nativeHeapAllocatedBytes))
                        DiagRow("JVM Heap (Kotlin UI)", "${StorageUtils.formatBytes(uiState.jvmHeapUsedBytes)} / ${StorageUtils.formatBytes(uiState.jvmHeapMaxBytes)}")
                        DiagRow("Device Available RAM", "${StorageUtils.formatBytes(uiState.deviceAvailMemBytes)} / ${StorageUtils.formatBytes(uiState.deviceTotalMemBytes)}")
                        DiagRow("Free Internal Storage", StorageUtils.formatBytes(uiState.availableStorageBytes))
                        DiagRow("Thermal Status", if (uiState.isThermalThrottling) "Throttled" else "Nominal")
                    }
                }
            }

            // Benchmark History
            item {
                Text(
                    text = "Benchmark History",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (uiState.recentBenchmarks.isEmpty()) {
                item {
                    Text(
                        text = "No saved benchmark runs yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(uiState.recentBenchmarks) { bm ->
                    BenchmarkHistoryCard(bm)
                }
            }
        }
    }
}

@Composable
fun MetricItem(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun BenchmarkHistoryCard(benchmark: BenchmarkEntity) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${benchmark.backend} • ${benchmark.threads} threads • ${benchmark.quantization}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${benchmark.contextSize} ctx • ${benchmark.thermalState}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                benchmark.generationTokensPerSec?.let { tps ->
                    Text(
                        text = String.format("%.2f t/s", tps),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryTeal
                    )
                }
                benchmark.timeToFirstTokenMs?.let { ttft ->
                    Text(
                        text = "TTFT: ${ttft}ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryIndigo
                    )
                }
            }
        }
    }
}
