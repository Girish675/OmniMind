package com.omnimind.domain.model

data class GenerationSettings(
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val minP: Float = 0.05f,
    val repeatPenalty: Float = 1.1f,
    val presencePenalty: Float = 0.0f,
    val frequencyPenalty: Float = 0.0f,
    val maxTokens: Int = 2048,
    val seed: Int = -1,
    val contextSize: Int = 4096,
    val threads: Int = 4,
    val batchSize: Int = 512,
    val gpuLayers: Int = 0
)
