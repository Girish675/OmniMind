package com.omnimind.data.entity

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class EntityTest {

    @Test
    fun testModelEntityCreation() {
        val model = ModelEntity(
            name = "Qwen3-4B Q4_K_M",
            fileName = "qwen3-4b-q4_k_m.gguf",
            filePath = "/data/user/0/com.omnimind/files/models/qwen3-4b-q4_k_m.gguf",
            fileSize = 2500000000L,
            sha256 = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
            architecture = "qwen2",
            parameterCount = 4000000000L,
            quantization = "Q4_K_M",
            contextLength = 32768,
            author = "Qwen Team",
            license = "Apache-2.0",
            isDefault = true
        )

        assertNotNull(model.id)
        assertEquals("Qwen3-4B Q4_K_M", model.name)
        assertEquals("qwen3-4b-q4_k_m.gguf", model.fileName)
        assertEquals(2500000000L, model.fileSize)
        assertEquals("Q4_K_M", model.quantization)
        assertTrue(model.isDefault)
        assertEquals("ready", model.status)
    }

    @Test
    fun testConversationAndMessageEntities() {
        val convId = UUID.randomUUID().toString()
        val conv = ConversationEntity(
            id = convId,
            title = "Test Conversation",
            modelId = "mod_1",
            systemPrompt = "You are a helpful assistant."
        )

        assertEquals(convId, conv.id)
        assertEquals("Test Conversation", conv.title)

        val userMsg = MessageEntity(
            conversationId = convId,
            role = "user",
            content = "What is Snapdragon 7s Gen 2?",
            sequenceNumber = 1
        )
        assertFalse(userMsg.isPartial)
        assertEquals(1, userMsg.sequenceNumber)

        val assistantMsg = MessageEntity(
            conversationId = convId,
            role = "assistant",
            content = "Snapdragon 7s Gen 2 is an octa-core SoC",
            sequenceNumber = 2,
            isPartial = true,
            tokenCount = 8
        )
        assertTrue(assistantMsg.isPartial)
        assertEquals(2, assistantMsg.sequenceNumber)
        assertEquals(8, assistantMsg.tokenCount)
    }
}
