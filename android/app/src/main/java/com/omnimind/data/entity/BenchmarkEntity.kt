package com.omnimind.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "benchmarks")
data class BenchmarkEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "model_id")
    val modelId: String,
    val quantization: String,
    @ColumnInfo(name = "context_size")
    val contextSize: Int,
    val threads: Int,
    val backend: String,
    @ColumnInfo(name = "gpu_layers")
    val gpuLayers: Int = 0,
    @ColumnInfo(name = "prompt_tokens_per_sec")
    val promptTokensPerSec: Float? = null,
    @ColumnInfo(name = "generation_tokens_per_sec")
    val generationTokensPerSec: Float? = null,
    @ColumnInfo(name = "time_to_first_token_ms")
    val timeToFirstTokenMs: Long? = null,
    @ColumnInfo(name = "peak_memory_bytes")
    val peakMemoryBytes: Long? = null,
    @ColumnInfo(name = "device_info")
    val deviceInfo: String? = null,
    @ColumnInfo(name = "thermal_state")
    val thermalState: String? = null,
    @ColumnInfo(name = "battery_level")
    val batteryLevel: Int? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String? = null
)
