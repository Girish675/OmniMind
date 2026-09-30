package com.omnimind.domain.model

data class ModelMetadata(
    val name: String,
    val fileName: String,
    val architecture: String,
    val parameterCount: Long,
    val quantization: String,
    val contextLength: Int,
    val vocabSize: Int,
    val fileSize: Long,
    val author: String,
    val license: String,
    val chatTemplate: String
)
