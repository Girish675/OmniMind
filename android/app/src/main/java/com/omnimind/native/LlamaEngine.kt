package com.omnimind.native

import android.util.Log
import com.omnimind.domain.model.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object LlamaEngine {
    private const val TAG = "LlamaEngine"

    init {
        try {
            System.loadLibrary("omnimind_jni")
            Log.i(TAG, "libomnimind_jni loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load libomnimind_jni", e)
        }
    }

    private val currentGenHandle = AtomicLong(0)
    private val isGenerating = AtomicBoolean(false)

    // Native C++ prototypes
    private external fun nativeInit(): Int
    private external fun nativeShutdown()
    private external fun nativeValidateGguf(path: String): Int
    private external fun nativeExtractMetadata(path: String): ModelMetadata?
    private external fun nativeLoadModel(
        path: String,
        contextSize: Int,
        threads: Int,
        batchSize: Int,
        gpuLayers: Int
    ): Long
    private external fun nativeUnloadModel(handle: Long)
    private external fun nativeGetModelMetadata(handle: Long): ModelMetadata?
    private external fun nativeTokenize(handle: Long, text: String): Int
    private external fun nativeApplyChatTemplate(
        handle: Long,
        roles: Array<String>,
        contents: Array<String>,
        addAssistantPrompt: Boolean
    ): String?
    private external fun nativeStartGeneration(
        handle: Long,
        prompt: String,
        temperature: Float,
        topP: Float,
        topK: Int,
        minP: Float,
        repeatPenalty: Float,
        presencePenalty: Float,
        frequencyPenalty: Float,
        maxTokens: Int,
        seed: Int,
        callback: NativeGenerationCallback
    ): Long
    private external fun nativeCancelGeneration(genHandle: Long)
    private external fun nativeFreeGeneration(genHandle: Long)
    private external fun nativeGetAvailableBackends(): Array<BackendInfo>?

    // Public Kotlin API

    fun init(): Boolean {
        return nativeInit() == 0
    }

    fun shutdown() {
        nativeShutdown()
    }

    fun validateGguf(path: String): Boolean {
        return nativeValidateGguf(path) == 0
    }

    fun extractMetadata(path: String): ModelMetadata? {
        return nativeExtractMetadata(path)
    }

    fun loadModel(
        path: String,
        contextSize: Int = 4096,
        threads: Int = 4,
        batchSize: Int = 512,
        gpuLayers: Int = 0
    ): Long {
        return nativeLoadModel(path, contextSize, threads, batchSize, gpuLayers)
    }

    fun unloadModel(handle: Long) {
        if (handle == 0L) return
        cancelGeneration()
        nativeUnloadModel(handle)
    }

    fun getModelMetadata(handle: Long): ModelMetadata? {
        if (handle == 0L) return null
        return nativeGetModelMetadata(handle)
    }

    fun tokenize(handle: Long, text: String): Int {
        if (handle == 0L) return 0
        return nativeTokenize(handle, text)
    }

    fun applyChatTemplate(
        handle: Long,
        roles: Array<String>,
        contents: Array<String>,
        addAssistantPrompt: Boolean = true
    ): String {
        if (handle == 0L) return ""
        return nativeApplyChatTemplate(handle, roles, contents, addAssistantPrompt) ?: ""
    }

    fun getAvailableBackends(): List<BackendInfo> {
        return nativeGetAvailableBackends()?.toList() ?: listOf(
            BackendInfo("CPU", "Standard ARM64 NEON CPU (Default)", false, true)
        )
    }

    fun cancelGeneration() {
        val genHandle = currentGenHandle.getAndSet(0)
        if (genHandle != 0L) {
            nativeCancelGeneration(genHandle)
            nativeFreeGeneration(genHandle)
        }
        isGenerating.set(false)
    }

    fun startGenerationStream(
        handle: Long,
        prompt: String,
        settings: GenerationSettings
    ): Flow<InferenceEvent> = callbackFlow {
        if (handle == 0L) {
            trySend(InferenceEvent.Error("No model loaded", -1))
            close()
            return@callbackFlow
        }

        cancelGeneration()
        isGenerating.set(true)

        val callback = object : NativeGenerationCallback {
            override fun onToken(token: String) {
                trySend(InferenceEvent.Token(token))
            }

            override fun onStats(stats: InferenceStats) {
                trySend(InferenceEvent.Stats(stats))
            }

            override fun onComplete(stats: InferenceStats) {
                trySend(InferenceEvent.Complete(stats))
                isGenerating.set(false)
                currentGenHandle.set(0)
                channel.close()
            }

            override fun onError(error: String, code: Int) {
                trySend(InferenceEvent.Error(error, code))
                isGenerating.set(false)
                currentGenHandle.set(0)
                channel.close()
            }
        }

        val gen = nativeStartGeneration(
            handle,
            prompt,
            settings.temperature,
            settings.topP,
            settings.topK,
            settings.minP,
            settings.repeatPenalty,
            settings.presencePenalty,
            settings.frequencyPenalty,
            settings.maxTokens,
            settings.seed,
            callback
        )

        if (gen == 0L) {
            trySend(InferenceEvent.Error("Failed to initiate generation", -2))
            isGenerating.set(false)
            close()
            return@callbackFlow
        }

        currentGenHandle.set(gen)

        awaitClose {
            val active = currentGenHandle.getAndSet(0)
            if (active != 0L) {
                nativeCancelGeneration(active)
                nativeFreeGeneration(active)
            }
            isGenerating.set(false)
        }
    }

    interface NativeGenerationCallback {
        fun onToken(token: String)
        fun onStats(stats: InferenceStats)
        fun onComplete(stats: InferenceStats)
        fun onError(error: String, code: Int)
    }
}
