package com.omnimind.domain.model

import org.junit.Assert.*
import org.junit.Test

class InferenceStateTest {

    @Test
    fun testInferenceStateTransitions() {
        var state: InferenceState = InferenceState.Unloaded
        assertTrue(state is InferenceState.Unloaded)

        // Unloaded -> Loading
        state = InferenceState.Loading(modelName = "Qwen3-4B-Q4_K_M.gguf")
        assertTrue(state is InferenceState.Loading)
        assertEquals("Qwen3-4B-Q4_K_M.gguf", (state as InferenceState.Loading).modelName)

        // Loading -> Ready
        state = InferenceState.Ready(modelId = "mod_123", modelName = "Qwen3-4B-Q4_K_M.gguf")
        assertTrue(state is InferenceState.Ready)
        assertEquals("mod_123", (state as InferenceState.Ready).modelId)

        // Ready -> Generating
        state = InferenceState.Generating(partialText = "Hello", stats = null)
        assertTrue(state is InferenceState.Generating)
        assertEquals("Hello", (state as InferenceState.Generating).partialText)

        // Generating -> Stopped (cancellation)
        state = InferenceState.Stopped(
            partialText = "Hello, world",
            stats = InferenceStats(generatedTokens = 2, generationTokensPerSec = 18.5f)
        )
        assertTrue(state is InferenceState.Stopped)
        assertEquals("Hello, world", (state as InferenceState.Stopped).partialText)
        assertEquals(2, (state as InferenceState.Stopped).stats?.generatedTokens)

        // Unloading -> Unloaded
        state = InferenceState.Unloading
        assertTrue(state is InferenceState.Unloading)

        state = InferenceState.Unloaded
        assertTrue(state is InferenceState.Unloaded)
    }

    @Test
    fun testInferenceEvents() {
        val tokenEvent: InferenceEvent = InferenceEvent.Token(" test")
        assertTrue(tokenEvent is InferenceEvent.Token)
        assertEquals(" test", (tokenEvent as InferenceEvent.Token).token)

        val stats = InferenceStats(
            promptTokens = 12,
            generatedTokens = 45,
            promptTokensPerSec = 82.4f,
            generationTokensPerSec = 21.3f,
            timeToFirstTokenMs = 145,
            totalTimeMs = 2250
        )
        val completeEvent: InferenceEvent = InferenceEvent.Complete(stats)
        assertTrue(completeEvent is InferenceEvent.Complete)
        assertEquals(45, (completeEvent as InferenceEvent.Complete).stats.generatedTokens)
        assertEquals(21.3f, (completeEvent as InferenceEvent.Complete).stats.generationTokensPerSec, 0.01f)

        val errorEvent: InferenceEvent = InferenceEvent.Error("Context window exceeded", 101)
        assertTrue(errorEvent is InferenceEvent.Error)
        assertEquals(101, (errorEvent as InferenceEvent.Error).code)
    }
}
