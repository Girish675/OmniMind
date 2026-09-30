package com.omnimind.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.omnimind.data.db.ModelDao
import com.omnimind.data.entity.ModelEntity
import com.omnimind.domain.model.GenerationSettings
import com.omnimind.domain.model.InferenceEvent
import com.omnimind.domain.model.InferenceState
import com.omnimind.domain.util.StorageUtils
import com.omnimind.native.LlamaEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class ModelRepository(
    private val context: Context,
    private val modelDao: ModelDao
) {
    private val TAG = "ModelRepository"

    // Active loaded model tracking with lock
    private val modelLock = Mutex()
    private var loadedModelHandle: Long = 0L
    private var loadedModelEntity: ModelEntity? = null

    private val _inferenceState = MutableStateFlow<InferenceState>(InferenceState.Unloaded)
    val inferenceState: StateFlow<InferenceState> = _inferenceState.asStateFlow()

    fun getAllModels(): Flow<List<ModelEntity>> = modelDao.getAllModels()

    suspend fun getModelById(id: String): ModelEntity? = modelDao.getModelById(id)

    suspend fun getDefaultModel(): ModelEntity? = modelDao.getDefaultModel()

    suspend fun setDefaultModel(id: String) {
        modelDao.setDefaultModel(id)
    }

    suspend fun renameModel(id: String, newName: String) {
        modelDao.renameModel(id, newName)
        if (loadedModelEntity?.id == id) {
            loadedModelEntity = loadedModelEntity?.copy(name = newName)
        }
    }

    /**
     * Scan model directory on disk to discover any externally placed GGUF files.
     */
    suspend fun scanLocalModels(): List<ModelEntity> = withContext(Dispatchers.IO) {
        val dir = StorageUtils.getModelsDirectory(context)
        val files = dir.listFiles { _, name -> name.endsWith(".gguf", ignoreCase = true) } ?: emptyArray()
        val discovered = mutableListOf<ModelEntity>()

        for (file in files) {
            val existing = modelDao.getModelByPath(file.absolutePath)
            if (existing != null) {
                // Verify file still intact
                if (file.length() == 0L || !LlamaEngine.validateGguf(file.absolutePath)) {
                    modelDao.updateModel(existing.copy(status = "corrupted"))
                }
                continue
            }

            // New GGUF file discovered
            if (!LlamaEngine.validateGguf(file.absolutePath)) {
                val corrupted = ModelEntity(
                    name = file.nameWithoutExtension,
                    fileName = file.name,
                    filePath = file.absolutePath,
                    fileSize = file.length(),
                    status = "corrupted"
                )
                modelDao.insertModel(corrupted)
                discovered.add(corrupted)
                continue
            }

            val meta = LlamaEngine.extractMetadata(file.absolutePath)
            val entity = ModelEntity(
                name = meta?.name?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension,
                fileName = file.name,
                filePath = file.absolutePath,
                fileSize = file.length(),
                architecture = meta?.architecture,
                parameterCount = meta?.parameterCount,
                quantization = meta?.quantization,
                contextLength = meta?.contextLength,
                author = meta?.author,
                license = meta?.license,
                status = "ready"
            )
            modelDao.insertModel(entity)
            discovered.add(entity)
        }
        discovered
    }

    /**
     * Import model from Android content URI or local file.
     */
    suspend fun importModelFromUri(
        uri: Uri,
        cancelFlag: AtomicBoolean? = null,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null
    ): ModelEntity = withContext(Dispatchers.IO) {
        val originalName = StorageUtils.getFileNameFromUri(context, uri)
        val safeName = if (originalName.endsWith(".gguf", ignoreCase = true)) {
            originalName
        } else {
            "$originalName.gguf"
        }

        val copiedFile = StorageUtils.copyUriToModelsDir(context, uri, safeName, cancelFlag, onProgress)

        val meta = LlamaEngine.extractMetadata(copiedFile.absolutePath)
        val sha256 = try {
            StorageUtils.computeSha256(copiedFile, cancelFlag)
        } catch (e: Exception) {
            null
        }

        val model = ModelEntity(
            name = meta?.name?.takeIf { it.isNotBlank() } ?: safeName.removeSuffix(".gguf"),
            fileName = safeName,
            filePath = copiedFile.absolutePath,
            fileSize = copiedFile.length(),
            sha256 = sha256,
            architecture = meta?.architecture,
            parameterCount = meta?.parameterCount,
            quantization = meta?.quantization,
            contextLength = meta?.contextLength,
            author = meta?.author,
            license = meta?.license,
            status = "ready"
        )

        modelDao.insertModel(model)

        // If no default model exists, make this the default
        if (modelDao.getDefaultModel() == null) {
            modelDao.setDefaultModel(model.id)
        }

        model
    }

    /**
     * Delete model.
     * Throws IllegalStateException if the model is currently loaded in memory (Locked).
     */
    suspend fun deleteModel(id: String) = modelLock.withLock {
        withContext(Dispatchers.IO) {
            val model = modelDao.getModelById(id) ?: return@withContext
            if (loadedModelEntity?.id == id && loadedModelHandle != 0L) {
                throw IllegalStateException("Cannot delete model while it is currently loaded in memory. Please unload it first.")
            }

            val file = File(model.filePath)
            if (file.exists()) {
                file.delete()
            }
            modelDao.deleteModelById(id)
        }
    }

    /**
     * Load model into native inference memory.
     */
    suspend fun loadModel(
        modelId: String,
        settings: GenerationSettings
    ): Boolean = modelLock.withLock {
        withContext(Dispatchers.Default) {
            val model = modelDao.getModelById(modelId)
            if (model == null) {
                _inferenceState.value = InferenceState.Failed("Model not found in registry")
                return@withContext false
            }

            val file = File(model.filePath)
            if (!file.exists() || file.length() == 0L) {
                _inferenceState.value = InferenceState.Failed("Model file missing or empty on disk")
                return@withContext false
            }

            // Unload previously loaded model if any
            if (loadedModelHandle != 0L) {
                _inferenceState.value = InferenceState.Unloading
                LlamaEngine.unloadModel(loadedModelHandle)
                loadedModelHandle = 0L
                loadedModelEntity = null
            }

            _inferenceState.value = InferenceState.Loading(model.name)

            val handle = LlamaEngine.loadModel(
                path = model.filePath,
                contextSize = settings.contextSize,
                threads = settings.threads,
                batchSize = settings.batchSize,
                gpuLayers = settings.gpuLayers
            )

            if (handle == 0L) {
                _inferenceState.value = InferenceState.Failed("Native model loading failed")
                return@withContext false
            }

            loadedModelHandle = handle
            loadedModelEntity = model
            modelDao.updateLastUsed(model.id, System.currentTimeMillis())

            _inferenceState.value = InferenceState.Ready(model.id, model.name)
            Log.i(TAG, "Model '${model.name}' loaded successfully (handle: $handle)")
            true
        }
    }

    /**
     * Unload active model from native memory.
     */
    suspend fun unloadModel() = modelLock.withLock {
        withContext(Dispatchers.Default) {
            if (loadedModelHandle != 0L) {
                _inferenceState.value = InferenceState.Unloading
                LlamaEngine.unloadModel(loadedModelHandle)
                loadedModelHandle = 0L
                loadedModelEntity = null
                _inferenceState.value = InferenceState.Unloaded
                Log.i(TAG, "Model unloaded")
            }
        }
    }

    fun isModelLoaded(): Boolean = loadedModelHandle != 0L

    fun getLoadedModel(): ModelEntity? = loadedModelEntity

    fun getLoadedHandle(): Long = loadedModelHandle

    /**
     * Format conversation prompt with active model template.
     */
    fun formatPrompt(
        roles: Array<String>,
        contents: Array<String>,
        addAssistantPrompt: Boolean = true
    ): String {
        val handle = loadedModelHandle
        if (handle == 0L) return ""
        return LlamaEngine.applyChatTemplate(handle, roles, contents, addAssistantPrompt)
    }

    /**
     * Stream generation tokens.
     */
    fun generateStream(
        prompt: String,
        settings: GenerationSettings
    ): Flow<InferenceEvent> {
        val handle = loadedModelHandle
        if (handle == 0L) {
            return flow {
                emit(InferenceEvent.Error("No model loaded", -1))
            }
        }
        return LlamaEngine.startGenerationStream(handle, prompt, settings)
    }

    fun cancelGeneration() {
        LlamaEngine.cancelGeneration()
    }
}
