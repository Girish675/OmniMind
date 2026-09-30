package com.omnimind.data.repository

import com.omnimind.data.db.ConversationDao
import com.omnimind.data.db.MessageDao
import com.omnimind.data.entity.ConversationEntity
import com.omnimind.data.entity.MessageEntity
import com.omnimind.domain.model.InferenceStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class ConversationRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) {

    fun getAllConversations(): Flow<List<ConversationEntity>> =
        conversationDao.getAllConversations()

    suspend fun getConversationById(id: String): ConversationEntity? =
        conversationDao.getConversationById(id)

    suspend fun createConversation(
        title: String = "New Chat",
        modelId: String? = null,
        systemPrompt: String? = null
    ): ConversationEntity = withContext(Dispatchers.IO) {
        val conv = ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            modelId = modelId,
            systemPrompt = systemPrompt
        )
        conversationDao.insertConversation(conv)
        conv
    }

    suspend fun updateTitle(id: String, title: String) = withContext(Dispatchers.IO) {
        conversationDao.updateTitle(id, title)
    }

    suspend fun deleteConversation(id: String) = withContext(Dispatchers.IO) {
        conversationDao.softDeleteConversation(id)
    }

    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>> =
        messageDao.getMessagesForConversation(conversationId)

    suspend fun getMessagesList(conversationId: String): List<MessageEntity> =
        messageDao.getMessagesList(conversationId)

    suspend fun addUserMessage(
        conversationId: String,
        content: String
    ): MessageEntity = withContext(Dispatchers.IO) {
        val maxSeq = messageDao.getMaxSequenceNumber(conversationId) ?: 0
        val message = MessageEntity(
            conversationId = conversationId,
            role = "user",
            content = content,
            sequenceNumber = maxSeq + 1,
            isPartial = false
        )
        messageDao.insertMessage(message)
        conversationDao.touchConversation(conversationId)
        message
    }

    suspend fun createAssistantMessage(
        conversationId: String,
        initialContent: String = "",
        isPartial: Boolean = true
    ): MessageEntity = withContext(Dispatchers.IO) {
        val maxSeq = messageDao.getMaxSequenceNumber(conversationId) ?: 0
        val message = MessageEntity(
            conversationId = conversationId,
            role = "assistant",
            content = initialContent,
            sequenceNumber = maxSeq + 1,
            isPartial = isPartial
        )
        messageDao.insertMessage(message)
        conversationDao.touchConversation(conversationId)
        message
    }

    /**
     * Flush partial generation output to database periodically or on interruption.
     */
    suspend fun updatePartialMessage(
        messageId: String,
        content: String,
        isPartial: Boolean = true,
        stats: InferenceStats? = null
    ) = withContext(Dispatchers.IO) {
        val statsJson = stats?.let {
            """{"ttft":${it.timeToFirstTokenMs},"gen_tps":${it.generationTokensPerSec},"prompt_tps":${it.promptTokensPerSec},"tokens":${it.generatedTokens}}"""
        }
        messageDao.updateMessageContent(messageId, content, isPartial, statsJson)
    }

    /**
     * Finalize assistant message with final stats.
     */
    suspend fun finalizeAssistantMessage(
        messageId: String,
        finalContent: String,
        stats: InferenceStats? = null
    ) = withContext(Dispatchers.IO) {
        updatePartialMessage(messageId, finalContent, isPartial = false, stats = stats)
    }

    /**
     * Delete last assistant message (for regeneration).
     */
    suspend fun deleteLastAssistantMessage(conversationId: String): MessageEntity? = withContext(Dispatchers.IO) {
        val messages = messageDao.getMessagesList(conversationId)
        val lastMsg = messages.lastOrNull()
        if (lastMsg != null && lastMsg.role == "assistant") {
            messageDao.deleteMessageById(lastMsg.id)
            lastMsg
        } else {
            null
        }
    }

    suspend fun clearMessages(conversationId: String) = withContext(Dispatchers.IO) {
        messageDao.deleteMessagesForConversation(conversationId)
    }
}
