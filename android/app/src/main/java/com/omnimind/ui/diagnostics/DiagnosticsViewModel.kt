package com.omnimind.ui.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.PowerManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnimind.data.entity.BenchmarkEntity
import com.omnimind.data.entity.ModelEntity
import com.omnimind.data.repository.BenchmarkRepository
import com.omnimind.data.repository.ModelRepository
import com.omnimind.data.repository.SettingsRepository
import com.omnimind.domain.model.GenerationSettings
import com.omnimind.domain.model.InferenceStats
import com.omnimind.domain.util.StorageUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DiagnosticsUiState(
    val activeModel: ModelEntity? = null,
    val activeSettings: GenerationSettings = GenerationSettings(),
    val activeBackend: String = "CPU",
    val latestStats: InferenceStats? = null,
    val recentBenchmarks: List<BenchmarkEntity> = emptyList(),
    // Hardware Diagnostics
    val jvmHeapUsedBytes: Long = 0L,
    val jvmHeapMaxBytes: Long = 0L,
    val nativeHeapAllocatedBytes: Long = 0L,
    val deviceAvailMemBytes: Long = 0L,
    val deviceTotalMemBytes: Long = 0L,
    val isLowMemoryDevice: Boolean = false,
    val isThermalThrottling: Boolean = false,
    val isStorageLow: Boolean = false,
    val availableStorageBytes: Long = 0L,
    val warningMessages: List<String> = emptyList()
)

class DiagnosticsViewModel(
    private val context: Context,
    private val modelRepository: ModelRepository,
    private val settingsRepository: SettingsRepository,
    private val benchmarkRepository: BenchmarkRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosticsUiState())
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    init {
        // Observe loaded model
        viewModelScope.launch {
            modelRepository.inferenceState.collect {
                val model = modelRepository.getLoadedModel()
                _uiState.update { it.copy(activeModel = model) }
                refreshHardwareState()
            }
        }

        // Observe settings
        viewModelScope.launch {
            settingsRepository.getGenerationSettingsFlow().collect { s ->
                val backend = settingsRepository.getSelectedBackend()
                _uiState.update { it.copy(activeSettings = s, activeBackend = backend) }
            }
        }

        // Observe benchmark runs
        viewModelScope.launch {
            benchmarkRepository.getAllBenchmarks().collect { list ->
                _uiState.update { it.copy(recentBenchmarks = list) }
            }
        }

        refreshHardwareState()
    }

    fun updateLatestInferenceStats(stats: InferenceStats) {
        _uiState.update { it.copy(latestStats = stats) }

        // Automatically record this real measurement to benchmarks database
        val model = _uiState.value.activeModel
        if (model != null && stats.generatedTokens > 0) {
            viewModelScope.launch {
                val benchmark = BenchmarkEntity(
                    modelId = model.id,
                    quantization = model.quantization ?: "Unknown",
                    contextSize = _uiState.value.activeSettings.contextSize,
                    threads = _uiState.value.activeSettings.threads,
                    backend = _uiState.value.activeBackend,
                    gpuLayers = _uiState.value.activeSettings.gpuLayers,
                    promptTokensPerSec = stats.promptTokensPerSec.takeIf { it > 0f },
                    generationTokensPerSec = stats.generationTokensPerSec.takeIf { it > 0f },
                    timeToFirstTokenMs = stats.timeToFirstTokenMs.takeIf { it > 0L },
                    peakMemoryBytes = stats.peakMemoryBytes.takeIf { it > 0L },
                    deviceInfo = "Motorola Edge 60 Stylus / Snapdragon 7s Gen 2 / Android 15",
                    thermalState = if (_uiState.value.isThermalThrottling) "Throttling" else "Normal",
                    notes = "Real user session generation"
                )
                benchmarkRepository.recordBenchmark(benchmark)
            }
        }
    }

    fun refreshHardwareState() {
        val runtime = Runtime.getRuntime()
        val jvmUsed = runtime.totalMemory() - runtime.freeMemory()
        val jvmMax = runtime.maxMemory()
        val nativeAlloc = Debug.getNativeHeapAllocatedSize()

        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isThermal = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            powerManager?.currentThermalStatus ?: 0 >= PowerManager.THERMAL_STATUS_MODERATE
        } else false

        val freeDisk = StorageUtils.getAvailableDiskSpace(context)
        val isStorageLow = freeDisk < 2L * 1024 * 1024 * 1024 // < 2GB

        val warnings = mutableListOf<String>()
        if (memInfo.lowMemory) {
            warnings.add("System is reporting low device memory. Unload model if backgrounding.")
        }
        if (isStorageLow) {
            warnings.add("Storage space is critically low (< 2 GB free).")
        }
        if (isThermal) {
            warnings.add("Thermal throttling observed. SoC is moderating clock speeds.")
        }
        val modelSize = _uiState.value.activeModel?.fileSize ?: 0L
        if (modelSize > 5L * 1024 * 1024 * 1024) {
            warnings.add("Active model is unusually large (> 5 GB) for an 8 GB device.")
        }

        _uiState.update {
            it.copy(
                jvmHeapUsedBytes = jvmUsed,
                jvmHeapMaxBytes = jvmMax,
                nativeHeapAllocatedBytes = nativeAlloc,
                deviceAvailMemBytes = memInfo.availMem,
                deviceTotalMemBytes = memInfo.totalMem,
                isLowMemoryDevice = memInfo.lowMemory,
                isThermalThrottling = isThermal,
                isStorageLow = isStorageLow,
                availableStorageBytes = freeDisk,
                warningMessages = warnings
            )
        }
    }
}
