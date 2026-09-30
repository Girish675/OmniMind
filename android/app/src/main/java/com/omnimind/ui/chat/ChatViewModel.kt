package com.omnimind.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnimind.data.entity.ConversationEntity
import com.omnimind.data.entity.MessageEntity
import com.omnimind.data.entity.ModelEntity
import com.omnimind.data.repository.ConversationRepository
import com.omnimind.data.repository.ModelRepository
import com.omnimind.data.repository.SettingsRepository
import com.omnimind.domain.model.GenerationSettings
import com.omnimind.domain.model.InferenceEvent
import com.omnimind.domain.model.InferenceStats
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ChatUiState(
    val conversationId: String = "",
    val conversationTitle: String = "OmniMind Chat",
    val messages: List<MessageEntity> = emptyList(),
    val isGenerating: Boolean = false,
    val streamingText: String = "",
    val activeStats: InferenceStats? = null,
    val loadedModel: ModelEntity? = null,
    val availableModels: List<ModelEntity> = emptyList(),
    val systemPrompt: String = "You are a helpful, respectful, and honest assistant.",
    val temperature: Float = 0.7f,
    val contextLength: Int = 4096,
    val errorMessage: String? = null
)

class ChatViewModel(
    private val modelRepository: ModelRepository,
    private val conversationRepository: ConversationRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null
    private var activeAssistantMessageId: String? = null

    init {
        // Observe available models
        viewModelScope.launch {
            modelRepository.getAllModels().collect { models ->
                _uiState.update { it.copy(availableModels = models) }
                // If no model loaded, try auto-loading default
                if (!modelRepository.isModelLoaded()) {
                    val defaultModel = models.firstOrNull { it.isDefault } ?: models.firstOrNull { it.status == "ready" }
                    if (defaultModel != null) {
                        val settings = settingsRepository.getGenerationSettings()
                        modelRepository.loadModel(defaultModel.id, settings)
                    }
                }
            }
        }

        // Observe loaded model from repository
        viewModelScope.launch {
            modelRepository.inferenceState.collect { state ->
                val model = modelRepository.getLoadedModel()
                _uiState.update { it.copy(loadedModel = model) }
            }
        }

        // Initialize settings
        viewModelScope.launch {
            val settings = settingsRepository.getGenerationSettings()
            val sysPrompt = settingsRepository.getSystemPrompt()
            _uiState.update {
                it.copy(
                    systemPrompt = sysPrompt,
                    temperature = settings.temperature,
                    contextLength = settings.contextSize
                )
            }
        }

        // Load most recent conversation or create new
        viewModelScope.launch {
            conversationRepository.getAllConversations().firstOrNull()?.firstOrNull()?.let { conv ->
                selectConversation(conv.id, conv.title)
            } ?: run {
                newConversation()
            }
        }
    }

    fun selectConversation(id: String, title: String) {
        _uiState.update { it.copy(conversationId = id, conversationTitle = title) }
        viewModelScope.launch {
            conversationRepository.getMessagesForConversation(id).collect { msgs ->
                _uiState.update { it.copy(messages = msgs) }
            }
        }
    }

    fun newConversation() {
        viewModelScope.launch {
            val loaded = modelRepository.getLoadedModel()
            val conv = conversationRepository.createConversation(
                title = "New Chat",
                modelId = loaded?.id,
                systemPrompt = _uiState.value.systemPrompt
            )
            selectConversation(conv.id, conv.title)
        }
    }

    fun sendMessage(userText: String) {
        val text = userText.trim()
        if (text.isBlank()) return

        val state = _uiState.value
        val convId = state.conversationId
        if (convId.isBlank()) return

        val loaded = modelRepository.getLoadedModel()
        if (loaded == null || !modelRepository.isModelLoaded()) {
            _uiState.update { it.copy(errorMessage = "Please load a model first from the model picker.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }

            // 1. Save user message to database
            val userMsg = conversationRepository.addUserMessage(convId, text)

            // Update conversation title if first message
            if (state.messages.isEmpty()) {
                val shortTitle = if (text.length > 28) text.take(28) + "..." else text
                conversationRepository.updateTitle(convId, shortTitle)
                _uiState.update { it.copy(conversationTitle = shortTitle) }
            }

            // 2. Format chat prompt using model's template
            val history = conversationRepository.getMessagesList(convId)
            val roles = mutableListOf<String>()
            val contents = mutableListOf<String>()

            // Include system prompt if defined
            if (state.systemPrompt.isNotBlank()) {
                roles.add("system")
                contents.add(state.systemPrompt)
            }

            for (msg in history) {
                roles.add(msg.role)
                contents.add(msg.content)
            }

            val formattedPrompt = modelRepository.formatPrompt(
                roles.toTypedArray(),
                contents.toTypedArray(),
                addAssistantPrompt = true
            )

            // Fallback if template produces empty output
            val promptToUse = if (formattedPrompt.isNotBlank()) formattedPrompt else text

            // 3. Create assistant message placeholder
            val assistantMsg = conversationRepository.createAssistantMessage(convId, "", isPartial = true)
            activeAssistantMessageId = assistantMsg.id

            // 4. Begin streaming generation
            val settings = GenerationSettings(
                temperature = state.temperature,
                contextSize = state.contextLength
            )

            _uiState.update {
                it.copy(
                    isGenerating = true,
                    streamingText = "",
                    activeStats = null
                )
            }

            val fullResponseBuilder = StringBuilder()
            var lastFlushTime = System.currentTimeMillis()
            var lastStats: InferenceStats? = null

            generationJob = launch {
                try {
                    modelRepository.generateStream(promptToUse, settings).collect { event ->
                        when (event) {
                            is InferenceEvent.Token -> {
                                fullResponseBuilder.append(event.token)
                                val currentStr = fullResponseBuilder.toString()
                                _uiState.update { it.copy(streamingText = currentStr) }

                                // Flush partial output to database periodically (every 500ms)
                                val now = System.currentTimeMillis()
                                if (now - lastFlushTime >= 500) {
                                    conversationRepository.updatePartialMessage(
                                        assistantMsg.id,
                                        currentStr,
                                        isPartial = true,
                                        stats = lastStats
                                    )
                                    lastFlushTime = now
                                }
                            }
                            is InferenceEvent.Stats -> {
                                lastStats = event.stats
                                _uiState.update { it.copy(activeStats = event.stats) }
                            }
                            is InferenceEvent.Complete -> {
                                lastStats = event.stats
                                _uiState.update {
                                    it.copy(
                                        isGenerating = false,
                                        streamingText = "",
                                        activeStats = event.stats
                                    )
                                }
                                conversationRepository.finalizeAssistantMessage(
                                    assistantMsg.id,
                                    fullResponseBuilder.toString(),
                                    event.stats
                                )
                                activeAssistantMessageId = null
                            }
                            is InferenceEvent.Error -> {
                                _uiState.update {
                                    it.copy(
                                        isGenerating = false,
                                        streamingText = "",
                                        errorMessage = event.error
                                    )
                                }
                                conversationRepository.finalizeAssistantMessage(
                                    assistantMsg.id,
                                    fullResponseBuilder.toString(),
                                    lastStats
                                )
                                activeAssistantMessageId = null
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Save partial response on unexpected interruption
                    val partial = fullResponseBuilder.toString()
                    conversationRepository.finalizeAssistantMessage(
                        assistantMsg.id,
                        partial,
                        lastStats
                    )
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            streamingText = "",
                            errorMessage = e.message
                        )
                    }
                    activeAssistantMessageId = null
                }
            }
        }
    }

    fun stopGeneration() {
        modelRepository.cancelGeneration()
        generationJob?.cancel()

        val currentStream = _uiState.value.streamingText
        val msgId = activeAssistantMessageId
        if (msgId != null && currentStream.isNotBlank()) {
            viewModelScope.launch {
                conversationRepository.finalizeAssistantMessage(
                    msgId,
                    currentStream,
                    _uiState.value.activeStats
                )
            }
        }

        _uiState.update {
            it.copy(
                isGenerating = false,
                streamingText = ""
            )
        }
        activeAssistantMessageId = null
    }

    fun regenerate() {
        if (_uiState.value.isGenerating) return
        val convId = _uiState.value.conversationId
        if (convId.isBlank()) return

        viewModelScope.launch {
            val messages = conversationRepository.getMessagesList(convId)
            // Delete last assistant message
            val deleted = conversationRepository.deleteLastAssistantMessage(convId)
            if (deleted != null) {
                // Find the previous user prompt
                val lastUserMsg = messages.filter { it.role == "user" }.lastOrNull()
                if (lastUserMsg != null) {
                    sendMessage(lastUserMsg.content)
                }
            }
        }
    }

    fun clearConversation() {
        val convId = _uiState.value.conversationId
        if (convId.isBlank()) return
        stopGeneration()
        viewModelScope.launch {
            conversationRepository.clearMessages(convId)
        }
    }

    fun loadModel(modelId: String) {
        viewModelScope.launch {
            val settings = settingsRepository.getGenerationSettings().copy(
                temperature = _uiState.value.temperature,
                contextSize = _uiState.value.contextLength
            )
            modelRepository.loadModel(modelId, settings)
        }
    }

    fun unloadModel() {
        viewModelScope.launch {
            modelRepository.unloadModel()
        }
    }

    fun updateSystemPrompt(newPrompt: String) {
        _uiState.update { it.copy(systemPrompt = newPrompt) }
        viewModelScope.launch {
            settingsRepository.setSystemPrompt(newPrompt)
        }
    }

    fun updateTemperature(temp: Float) {
        _uiState.update { it.copy(temperature = temp) }
    }

    fun updateContextLength(ctx: Int) {
        _uiState.update { it.copy(contextLength = ctx) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
