package com.omnimind.domain.model

data class InferenceStats(
    val promptTokens: Int = 0,
    val generatedTokens: Int = 0,
    val promptTokensPerSec: Float = 0.0f,
    val generationTokensPerSec: Float = 0.0f,
    val timeToFirstTokenMs: Long = 0,
    val totalTimeMs: Long = 0,
    val loadTimeMs: Long = 0,
    val peakMemoryBytes: Long = 0
)
