package com.omnimind.domain.model

sealed interface InferenceState {
    object Unloaded : InferenceState
    data class Loading(val modelName: String) : InferenceState
    data class Ready(val modelId: String, val modelName: String) : InferenceState
    data class Generating(val partialText: String, val stats: InferenceStats? = null) : InferenceState
    data class Stopped(val partialText: String, val stats: InferenceStats? = null) : InferenceState
    data class Failed(val error: String) : InferenceState
    object Unloading : InferenceState
}

sealed interface InferenceEvent {
    data class Token(val token: String) : InferenceEvent
    data class Stats(val stats: InferenceStats) : InferenceEvent
    data class Complete(val stats: InferenceStats) : InferenceEvent
    data class Error(val error: String, val code: Int) : InferenceEvent
}
