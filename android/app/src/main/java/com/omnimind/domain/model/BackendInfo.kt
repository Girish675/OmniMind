package com.omnimind.domain.model

data class BackendInfo(
    val name: String,
    val description: String,
    val isAccelerator: Boolean,
    val isAvailable: Boolean
)
