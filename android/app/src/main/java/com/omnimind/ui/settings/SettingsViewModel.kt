package com.omnimind.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnimind.data.repository.SettingsRepository
import com.omnimind.domain.model.BackendInfo
import com.omnimind.domain.model.GenerationSettings
import com.omnimind.native.LlamaEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: GenerationSettings = GenerationSettings(),
    val availableBackends: List<BackendInfo> = emptyList(),
    val selectedBackend: String = "CPU",
    val isSavedMessage: Boolean = false
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val s = settingsRepository.getGenerationSettings()
            val backend = settingsRepository.getSelectedBackend()
            val backends = LlamaEngine.getAvailableBackends()
            _uiState.update {
                it.copy(
                    settings = s,
                    availableBackends = backends,
                    selectedBackend = backend
                )
            }
        }
    }

    fun updateSettings(newSettings: GenerationSettings) {
        _uiState.update { it.copy(settings = newSettings) }
        viewModelScope.launch {
            settingsRepository.updateGenerationSettings(newSettings)
        }
    }

    fun selectBackend(backend: String) {
        _uiState.update { it.copy(selectedBackend = backend) }
        viewModelScope.launch {
            settingsRepository.setSelectedBackend(backend)
        }
    }

    fun resetToDefaults() {
        val defaults = GenerationSettings()
        updateSettings(defaults)
    }
}
