#ifndef OMNIMIND_CORE_H
#define OMNIMIND_CORE_H

#include "omnimind_types.h"

#ifdef __cplusplus
extern "C" {
#endif

#if defined(_WIN32) && defined(OMNIMIND_BUILD_SHARED)
    #define OMNIMIND_API __declspec(dllexport)
#elif defined(_WIN32) && !defined(OMNIMIND_BUILD_STATIC)
    #define OMNIMIND_API __declspec(dllimport)
#elif defined(__GNUC__) && __GNUC__ >= 4
    #define OMNIMIND_API __attribute__((visibility("default")))
#else
    #define OMNIMIND_API
#endif

// ============================================================================
// Lifecycle Management
// ============================================================================

/**
 * Initialize OmniMind core and GGML backends.
 * Must be called before any model operations.
 */
OMNIMIND_API omnimind_status_t omnimind_init(void);

/**
 * Shutdown OmniMind core and release backend resources.
 */
OMNIMIND_API void omnimind_shutdown(void);

// ============================================================================
// Model Inspection & Validation (Non-destructive, No Heavy Weights Allocation)
// ============================================================================

/**
 * Validate a GGUF model file (checks magic number "GGUF", header version, format).
 * Fast check without allocating tensors.
 */
OMNIMIND_API omnimind_status_t omnimind_validate_gguf(const char* path);

/**
 * Extract complete metadata from a GGUF file without loading tensor weights into memory.
 */
OMNIMIND_API omnimind_status_t omnimind_extract_metadata(
    const char* path,
    omnimind_model_metadata_t* out_meta
);

// ============================================================================
// Model Lifecycle
// ============================================================================

/**
 * Load a GGUF model for inference.
 */
OMNIMIND_API omnimind_status_t omnimind_load_model(
    const char* path,
    const omnimind_model_params_t* params,
    omnimind_model_t** out_model
);

/**
 * Unload a model and release all native memory.
 */
OMNIMIND_API void omnimind_unload_model(omnimind_model_t* model);

/**
 * Retrieve metadata from an actively loaded model.
 */
OMNIMIND_API omnimind_status_t omnimind_get_model_metadata(
    const omnimind_model_t* model,
    omnimind_model_metadata_t* out_meta
);

/**
 * Calculate token count for input text with the loaded model's vocabulary.
 */
OMNIMIND_API int32_t omnimind_tokenize(
    const omnimind_model_t* model,
    const char* text
);

/**
 * Format conversation messages into a prompt using the model's native chat template.
 * If out_buf is NULL or buf_size is 0, returns the required buffer size in bytes.
 */
OMNIMIND_API int32_t omnimind_apply_chat_template(
    const omnimind_model_t* model,
    const omnimind_chat_message_t* messages,
    size_t n_messages,
    bool add_assistant_prompt,
    char* out_buf,
    size_t buf_size
);

// ============================================================================
// Inference Execution & Streaming
// ============================================================================

/**
 * Start streaming generation for a given prompt.
 * Spawns execution on a background thread and streams tokens/events via callback.
 */
OMNIMIND_API omnimind_status_t omnimind_start_generation(
    omnimind_model_t* model,
    const char* prompt,
    const omnimind_generation_params_t* params,
    omnimind_event_callback_t callback,
    void* user_data,
    omnimind_generation_t** out_gen
);

/**
 * Signal active generation to cancel immediately.
 */
OMNIMIND_API omnimind_status_t omnimind_cancel_generation(omnimind_generation_t* gen);

/**
 * Wait for generation to finish or join, and release generation resources.
 */
OMNIMIND_API void omnimind_free_generation(omnimind_generation_t* gen);

// ============================================================================
// Device & Backend Diagnostics
// ============================================================================

/**
 * Enumerate available compute backends on this system (CPU, OpenCL, Vulkan, etc.).
 */
OMNIMIND_API omnimind_status_t omnimind_get_available_backends(
    omnimind_backend_info_t* out_backends,
    size_t max_count,
    size_t* out_count
);

/**
 * Convert status code to human-readable string.
 */
OMNIMIND_API const char* omnimind_status_str(omnimind_status_t status);

#ifdef __cplusplus
}
#endif

#endif // OMNIMIND_CORE_H
