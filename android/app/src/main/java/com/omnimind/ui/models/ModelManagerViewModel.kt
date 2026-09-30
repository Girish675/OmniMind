package com.omnimind.ui.models

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnimind.data.entity.ModelEntity
import com.omnimind.data.repository.ModelRepository
import com.omnimind.data.repository.SettingsRepository
import com.omnimind.domain.util.StorageUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

data class ModelManagerUiState(
    val models: List<ModelEntity> = emptyList(),
    val loadedModelId: String? = null,
    val isImporting: Boolean = false,
    val importProgress: Float = 0.0f,
    val importStatusText: String = "",
    val availableDiskSpace: Long = 0L,
    val totalDiskSpace: Long = 0L,
    val inspectionModel: ModelEntity? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class ModelManagerViewModel(
    private val context: Context,
    private val modelRepository: ModelRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelManagerUiState())
    val uiState: StateFlow<ModelManagerUiState> = _uiState.asStateFlow()

    private var activeImportCancelFlag: AtomicBoolean? = null

    init {
        // Observe models list
        viewModelScope.launch {
            modelRepository.getAllModels().collect { list ->
                _uiState.update { it.copy(models = list) }
            }
        }

        // Observe loaded model
        viewModelScope.launch {
            modelRepository.inferenceState.collect {
                val loaded = modelRepository.getLoadedModel()
                _uiState.update { it.copy(loadedModelId = loaded?.id) }
            }
        }

        refreshDiskSpace()
    }

    fun refreshDiskSpace() {
        _uiState.update {
            it.copy(
                availableDiskSpace = StorageUtils.getAvailableDiskSpace(context),
                totalDiskSpace = StorageUtils.getTotalDiskSpace(context)
            )
        }
    }

    fun importModel(uri: Uri) {
        val cancelFlag = AtomicBoolean(false)
        activeImportCancelFlag = cancelFlag

        _uiState.update {
            it.copy(
                isImporting = true,
                importProgress = 0f,
                importStatusText = "Copying model file to storage...",
                errorMessage = null,
                successMessage = null
            )
        }

        viewModelScope.launch {
            try {
                val model = modelRepository.importModelFromUri(
                    uri = uri,
                    cancelFlag = cancelFlag,
                    onProgress = { copied, total ->
                        val progress = if (total > 0) copied.toFloat() / total.toFloat() else 0f
                        _uiState.update {
                            it.copy(
                                importProgress = progress,
                                importStatusText = "Importing: ${StorageUtils.formatBytes(copied)} / ${StorageUtils.formatBytes(total)}"
                            )
                        }
                    }
                )
                refreshDiskSpace()
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        importProgress = 1f,
                        importStatusText = "",
                        successMessage = "Model '${model.name}' successfully imported!"
                    )
                }
            } catch (e: InterruptedException) {
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        importProgress = 0f,
                        importStatusText = "",
                        errorMessage = "Import cancelled."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        importProgress = 0f,
                        importStatusText = "",
                        errorMessage = e.message ?: "Failed to import model."
                    )
                }
            } finally {
                activeImportCancelFlag = null
            }
        }
    }

    fun cancelImport() {
        activeImportCancelFlag?.set(true)
    }

    fun deleteModel(model: ModelEntity) {
        viewModelScope.launch {
            try {
                modelRepository.deleteModel(model.id)
                refreshDiskSpace()
                _uiState.update { it.copy(successMessage = "Model '${model.name}' deleted.") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun renameModel(id: String, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            modelRepository.renameModel(id, newName.trim())
        }
    }

    fun setDefaultModel(id: String) {
        viewModelScope.launch {
            modelRepository.setDefaultModel(id)
        }
    }

    fun loadModel(modelId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }
            val settings = settingsRepository.getGenerationSettings()
            val success = modelRepository.loadModel(modelId, settings)
            if (!success) {
                _uiState.update { it.copy(errorMessage = "Failed to load model into memory.") }
            }
        }
    }

    fun unloadModel() {
        viewModelScope.launch {
            modelRepository.unloadModel()
        }
    }

    fun openMetadataInspection(model: ModelEntity) {
        _uiState.update { it.copy(inspectionModel = model) }
    }

    fun closeMetadataInspection() {
        _uiState.update { it.copy(inspectionModel = null) }
    }

    fun scanDiskForModels() {
        viewModelScope.launch {
            val discovered = modelRepository.scanLocalModels()
            refreshDiskSpace()
            if (discovered.isNotEmpty()) {
                _uiState.update { it.copy(successMessage = "Found and registered ${discovered.size} model(s).") }
            } else {
                _uiState.update { it.copy(successMessage = "Scan complete. No new models found.") }
            }
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
