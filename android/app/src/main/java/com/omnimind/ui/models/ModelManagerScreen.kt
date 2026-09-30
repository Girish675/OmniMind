package com.omnimind.ui.models

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.omnimind.data.entity.ModelEntity
import com.omnimind.domain.util.StorageUtils
import com.omnimind.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerScreen(
    viewModel: ModelManagerViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    var modelToRename by remember { mutableStateOf<ModelEntity?>(null) }
    var modelToDelete by remember { mutableStateOf<ModelEntity?>(null) }

    // Android Document Picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importModel(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Model Manager", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { viewModel.scanDiskForModels() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Scan Disk")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                icon = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                text = { Text("Import GGUF") },
                containerColor = PrimaryTeal,
                contentColor = BackgroundDark
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Success / Error Banners
            AnimatedVisibility(visible = uiState.successMessage != null) {
                Surface(
                    color = SuccessGreen.copy(alpha = 0.15f),
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
                            text = uiState.successMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = SuccessGreen,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.dismissMessage() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = SuccessGreen)
                        }
                    }
                }
            }

            AnimatedVisibility(visible = uiState.errorMessage != null) {
                Surface(
                    color = ErrorRed.copy(alpha = 0.15f),
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
                            color = ErrorRed,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.dismissMessage() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = ErrorRed)
                        }
                    }
                }
            }

            // Storage Overview Card
            StorageOverviewCard(
                availableBytes = uiState.availableDiskSpace,
                totalBytes = uiState.totalDiskSpace
            )

            // Import Progress Banner (if active)
            if (uiState.isImporting) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Importing Model File...",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(onClick = { viewModel.cancelImport() }) {
                                Text("Cancel", color = ErrorRed)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { uiState.importProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = PrimaryTeal,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = uiState.importStatusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Models List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp)
            ) {
                if (uiState.models.isEmpty() && !uiState.isImporting) {
                    item {
                        EmptyModelsState(onImportClick = { filePickerLauncher.launch(arrayOf("*/*")) })
                    }
                }

                items(uiState.models) { model ->
                    val isLoaded = model.id == uiState.loadedModelId
                    ModelCard(
                        model = model,
                        isLoaded = isLoaded,
                        onLoad = { viewModel.loadModel(model.id) },
                        onUnload = { viewModel.unloadModel() },
                        onInspect = { viewModel.openMetadataInspection(model) },
                        onRename = { modelToRename = model },
                        onSetDefault = { viewModel.setDefaultModel(model.id) },
                        onDelete = { modelToDelete = model }
                    )
                }
            }
        }
    }

    // Rename Dialog
    modelToRename?.let { model ->
        var newName by remember { mutableStateOf(model.name) }
        AlertDialog(
            onDismissRequest = { modelToRename = null },
            title = { Text("Rename Model") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.renameModel(model.id, newName)
                    modelToRename = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { modelToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    modelToDelete?.let { model ->
        val isLoaded = model.id == uiState.loadedModelId
        AlertDialog(
            onDismissRequest = { modelToDelete = null },
            title = { Text(if (isLoaded) "Model is in Use" else "Delete Model?") },
            text = {
                if (isLoaded) {
                    Text("This model is currently loaded in memory. You must unload it before deleting it to prevent memory corruption.")
                } else {
                    Text("Are you sure you want to delete '${model.name}'? This will free ${StorageUtils.formatBytes(model.fileSize)} of storage.")
                }
            },
            confirmButton = {
                if (!isLoaded) {
                    Button(
                        onClick = {
                            viewModel.deleteModel(model)
                            modelToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                    ) {
                        Text("Delete", color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { modelToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Metadata Inspection Dialog
    uiState.inspectionModel?.let { model ->
        MetadataInspectionDialog(
            model = model,
            onDismiss = { viewModel.closeMetadataInspection() }
        )
    }
}

@Composable
fun StorageOverviewCard(
    availableBytes: Long,
    totalBytes: Long
) {
    val usedBytes = (totalBytes - availableBytes).coerceAtLeast(0L)
    val usedRatio = if (totalBytes > 0) usedBytes.toFloat() / totalBytes.toFloat() else 0f
    val isLowStorage = availableBytes < 2 * 1024 * 1024 * 1024L // < 2 GB

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Device Storage",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${StorageUtils.formatBytes(availableBytes)} free",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isLowStorage) WarningAmber else PrimaryTeal
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { usedRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (isLowStorage) WarningAmber else SecondaryIndigo,
                trackColor = MaterialTheme.colorScheme.surface
            )

            if (isLowStorage) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Low free space. Keep at least 2 GB free for inference KV cache.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningAmber,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ModelCard(
    model: ModelEntity,
    isLoaded: Boolean,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onInspect: () -> Unit,
    onRename: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isLoaded) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        ),
        border = if (isLoaded) androidx.compose.foundation.BorderStroke(1.dp, PrimaryTeal) else null,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(if (isLoaded) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }

                if (model.isDefault) {
                    Surface(
                        color = SecondaryIndigo.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "DEFAULT",
                            color = SecondaryIndigo,
                            style = MaterialTheme.typography.labelMedium,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Badges row
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                model.quantization?.let { q ->
                    MetaPill(text = q, color = PrimaryTeal)
                }
                model.architecture?.let { arch ->
                    MetaPill(text = arch.uppercase(), color = SecondaryIndigo)
                }
                MetaPill(text = StorageUtils.formatBytes(model.fileSize), color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (model.status == "corrupted") {
                    MetaPill(text = "CORRUPTED", color = ErrorRed)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onInspect, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Info, contentDescription = "Inspect", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onRename, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename", modifier = Modifier.size(18.dp))
                    }
                    if (!model.isDefault) {
                        IconButton(onClick = onSetDefault, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.StarBorder, contentDescription = "Set Default", modifier = Modifier.size(18.dp))
                        }
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed, modifier = Modifier.size(18.dp))
                    }
                }

                if (isLoaded) {
                    Button(
                        onClick = onUnload,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Unload", color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp)
                    }
                } else {
                    Button(
                        onClick = onLoad,
                        enabled = model.status != "corrupted",
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Load", color = BackgroundDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun MetaPill(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun MetadataInspectionDialog(
    model: ModelEntity,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Model Metadata") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                MetaRow("Name", model.name)
                MetaRow("File", model.fileName)
                MetaRow("Size", StorageUtils.formatBytes(model.fileSize))
                MetaRow("Architecture", model.architecture ?: "Unknown")
                MetaRow("Quantization", model.quantization ?: "Unknown")
                MetaRow("Parameters", model.parameterCount?.let { String.format("%,d", it) } ?: "Unknown")
                MetaRow("Context Length", model.contextLength?.let { "$it tokens" } ?: "Unknown")
                MetaRow("License", model.license ?: "Not specified")
                MetaRow("Author", model.author ?: "Not specified")
                MetaRow("SHA-256", model.sha256?.take(16)?.plus("...") ?: "Not computed")
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun MetaRow(label: String, value: String) {
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
fun EmptyModelsState(onImportClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 60.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                Icons.Default.Storage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(54.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text("No GGUF Models Found", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Import your reference model (e.g. Qwen3-4B-GGUF Q4_K_M) using the button below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(18.dp))
            Button(onClick = onImportClick) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import GGUF File")
            }
        }
    }
}
