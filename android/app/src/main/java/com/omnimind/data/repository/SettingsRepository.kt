package com.omnimind.data.repository

import com.omnimind.data.db.SettingsDao
import com.omnimind.data.entity.SettingsEntity
import com.omnimind.domain.model.GenerationSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SettingsRepository(
    private val settingsDao: SettingsDao
) {

    fun getGenerationSettingsFlow(): Flow<GenerationSettings> {
        return settingsDao.getAllSettings().map { list ->
            val map = list.associate { it.key to it.value }
            GenerationSettings(
                temperature = map["temperature"]?.toFloatOrNull() ?: 0.7f,
                topP = map["top_p"]?.toFloatOrNull() ?: 0.9f,
                topK = map["top_k"]?.toIntOrNull() ?: 40,
                minP = map["min_p"]?.toFloatOrNull() ?: 0.05f,
                repeatPenalty = map["repeat_penalty"]?.toFloatOrNull() ?: 1.1f,
                presencePenalty = map["presence_penalty"]?.toFloatOrNull() ?: 0.0f,
                frequencyPenalty = map["frequency_penalty"]?.toFloatOrNull() ?: 0.0f,
                maxTokens = map["max_tokens"]?.toIntOrNull() ?: 2048,
                seed = map["seed"]?.toIntOrNull() ?: -1,
                contextSize = map["context_size"]?.toIntOrNull() ?: 4096,
                threads = map["threads"]?.toIntOrNull() ?: 4,
                batchSize = map["batch_size"]?.toIntOrNull() ?: 512,
                gpuLayers = map["gpu_layers"]?.toIntOrNull() ?: 0
            )
        }
    }

    suspend fun getGenerationSettings(): GenerationSettings = withContext(Dispatchers.IO) {
        GenerationSettings(
            temperature = settingsDao.getSetting("temperature")?.toFloatOrNull() ?: 0.7f,
            topP = settingsDao.getSetting("top_p")?.toFloatOrNull() ?: 0.9f,
            topK = settingsDao.getSetting("top_k")?.toIntOrNull() ?: 40,
            minP = settingsDao.getSetting("min_p")?.toFloatOrNull() ?: 0.05f,
            repeatPenalty = settingsDao.getSetting("repeat_penalty")?.toFloatOrNull() ?: 1.1f,
            presencePenalty = settingsDao.getSetting("presence_penalty")?.toFloatOrNull() ?: 0.0f,
            frequencyPenalty = settingsDao.getSetting("frequency_penalty")?.toFloatOrNull() ?: 0.0f,
            maxTokens = settingsDao.getSetting("max_tokens")?.toIntOrNull() ?: 2048,
            seed = settingsDao.getSetting("seed")?.toIntOrNull() ?: -1,
            contextSize = settingsDao.getSetting("context_size")?.toIntOrNull() ?: 4096,
            threads = settingsDao.getSetting("threads")?.toIntOrNull() ?: 4,
            batchSize = settingsDao.getSetting("batch_size")?.toIntOrNull() ?: 512,
            gpuLayers = settingsDao.getSetting("gpu_layers")?.toIntOrNull() ?: 0
        )
    }

    suspend fun updateGenerationSettings(settings: GenerationSettings) = withContext(Dispatchers.IO) {
        settingsDao.setSetting(SettingsEntity("temperature", settings.temperature.toString()))
        settingsDao.setSetting(SettingsEntity("top_p", settings.topP.toString()))
        settingsDao.setSetting(SettingsEntity("top_k", settings.topK.toString()))
        settingsDao.setSetting(SettingsEntity("min_p", settings.minP.toString()))
        settingsDao.setSetting(SettingsEntity("repeat_penalty", settings.repeatPenalty.toString()))
        settingsDao.setSetting(SettingsEntity("presence_penalty", settings.presencePenalty.toString()))
        settingsDao.setSetting(SettingsEntity("frequency_penalty", settings.frequencyPenalty.toString()))
        settingsDao.setSetting(SettingsEntity("max_tokens", settings.maxTokens.toString()))
        settingsDao.setSetting(SettingsEntity("seed", settings.seed.toString()))
        settingsDao.setSetting(SettingsEntity("context_size", settings.contextSize.toString()))
        settingsDao.setSetting(SettingsEntity("threads", settings.threads.toString()))
        settingsDao.setSetting(SettingsEntity("batch_size", settings.batchSize.toString()))
        settingsDao.setSetting(SettingsEntity("gpu_layers", settings.gpuLayers.toString()))
    }

    suspend fun getSystemPrompt(): String = withContext(Dispatchers.IO) {
        settingsDao.getSetting("system_prompt") ?: "You are a helpful, respectful, and honest assistant."
    }

    suspend fun setSystemPrompt(prompt: String) = withContext(Dispatchers.IO) {
        settingsDao.setSetting(SettingsEntity("system_prompt", prompt))
    }

    suspend fun getSelectedBackend(): String = withContext(Dispatchers.IO) {
        settingsDao.getSetting("selected_backend") ?: "CPU"
    }

    suspend fun setSelectedBackend(backend: String) = withContext(Dispatchers.IO) {
        settingsDao.setSetting(SettingsEntity("selected_backend", backend))
    }
}
