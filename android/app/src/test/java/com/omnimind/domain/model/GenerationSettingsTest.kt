package com.omnimind.domain.model

import org.junit.Assert.*
import org.junit.Test

class GenerationSettingsTest {

    @Test
    fun testDefaultSettings_referenceModelCompatibility() {
        val settings = GenerationSettings()

        // Conservative defaults for Snapdragon 7s Gen 2 (8-core: 4 performance + 4 efficiency)
        assertEquals(4, settings.threads)
        assertEquals(4096, settings.contextSize)
        assertEquals(512, settings.batchSize)
        assertEquals(2048, settings.maxTokens)
        assertEquals(0.7f, settings.temperature, 0.001f)
        assertEquals(0.9f, settings.topP, 0.001f)
        assertEquals(40, settings.topK)
        assertEquals(0.05f, settings.minP, 0.001f)
        assertEquals(1.1f, settings.repeatPenalty, 0.001f)
        assertEquals(0, settings.gpuLayers) // CPU baseline by default
    }

    @Test
    fun testCustomSettingsMutation() {
        val original = GenerationSettings()
        val custom = original.copy(
            threads = 6,
            contextSize = 8192,
            temperature = 0.2f,
            gpuLayers = 33
        )

        assertEquals(6, custom.threads)
        assertEquals(8192, custom.contextSize)
        assertEquals(0.2f, custom.temperature, 0.001f)
        assertEquals(33, custom.gpuLayers)
        // Verify unchanged values
        assertEquals(40, custom.topK)
        assertEquals(0.9f, custom.topP, 0.001f)
    }
}
