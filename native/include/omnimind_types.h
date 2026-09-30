#ifndef OMNIMIND_TYPES_H
#define OMNIMIND_TYPES_H

#include <stdint.h>
#include <stdbool.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

// Error codes
typedef enum {
    OMNIMIND_OK = 0,
    OMNIMIND_ERROR_INVALID_ARGUMENT = 1,
    OMNIMIND_ERROR_FILE_NOT_FOUND = 2,
    OMNIMIND_ERROR_INVALID_GGUF = 3,
    OMNIMIND_ERROR_UNSUPPORTED_ARCH = 4,
    OMNIMIND_ERROR_UNSUPPORTED_BACKEND = 5,
    OMNIMIND_ERROR_LOAD_FAILED = 6,
    OMNIMIND_ERROR_OUT_OF_MEMORY = 7,
    OMNIMIND_ERROR_NOT_LOADED = 8,
    OMNIMIND_ERROR_ALREADY_LOADED = 9,
    OMNIMIND_ERROR_GENERATION_FAILED = 10,
    OMNIMIND_ERROR_CANCELLED = 11,
    OMNIMIND_ERROR_BUFFER_TOO_SMALL = 12
} omnimind_status_t;

// Model loading parameters
typedef struct {
    int32_t context_size;    // Context length in tokens (default: 4096)
    int32_t threads;         // Thread count (default: 4)
    int32_t batch_size;      // Logical batch size (default: 512)
    int32_t gpu_layers;      // Layers to offload to GPU/accelerator (0 = CPU only)
    bool use_mmap;           // Memory map file (default: true)
    bool use_mlock;          // Lock memory from swapping (default: false)
} omnimind_model_params_t;

// Generation sampling parameters
typedef struct {
    float temperature;         // Randomness: 0.0 = deterministic (default: 0.7)
    float top_p;               // Nucleus sampling probability (default: 0.9)
    int32_t top_k;             // Top-K sampling (default: 40)
    float min_p;               // Minimum probability threshold (default: 0.05)
    float repeat_penalty;      // Repetition penalty (default: 1.1)
    float presence_penalty;    // Presence penalty (default: 0.0)
    float frequency_penalty;   // Frequency penalty (default: 0.0)
    int32_t max_tokens;        // Maximum new tokens to generate (default: 2048)
    uint32_t seed;             // RNG seed (0xFFFFFFFF = random)
} omnimind_generation_params_t;

// Extracted model metadata
typedef struct {
    char name[256];
    char file_name[256];
    char architecture[64];
    int64_t parameter_count;
    char quantization[32];
    int32_t context_length;
    int32_t vocab_size;
    int64_t file_size;
    char author[256];
    char license[128];
    char chat_template[8192];
} omnimind_model_metadata_t;

// Performance and timing statistics
typedef struct {
    int32_t prompt_tokens;
    int32_t generated_tokens;
    float prompt_tokens_per_sec;
    float generation_tokens_per_sec;
    int64_t time_to_first_token_ms;
    int64_t total_time_ms;
    int64_t load_time_ms;
    int64_t peak_memory_bytes;
} omnimind_stats_t;

// Available compute backends
typedef struct {
    char name[64];
    char description[256];
    bool is_accelerator;
    bool is_available;
} omnimind_backend_info_t;

// Chat message structure for templating
typedef struct {
    const char* role;     // "system", "user", or "assistant"
    const char* content;  // message content
} omnimind_chat_message_t;

// Stream event types
typedef enum {
    OMNIMIND_EVENT_TOKEN = 1,
    OMNIMIND_EVENT_STATS = 2,
    OMNIMIND_EVENT_COMPLETE = 3,
    OMNIMIND_EVENT_ERROR = 4
} omnimind_event_type_t;

// Event payload
typedef struct {
    omnimind_event_type_t type;
    const char* token;              // valid for OMNIMIND_EVENT_TOKEN
    const omnimind_stats_t* stats;  // valid for OMNIMIND_EVENT_STATS and OMNIMIND_EVENT_COMPLETE
    const char* error_message;      // valid for OMNIMIND_EVENT_ERROR
    omnimind_status_t status;
} omnimind_inference_event_t;

// Event callback function
typedef void (*omnimind_event_callback_t)(const omnimind_inference_event_t* event, void* user_data);

// Token-only callback function (simplified)
typedef void (*omnimind_token_callback_t)(const char* token, void* user_data);

// Opaque handles
typedef struct omnimind_model omnimind_model_t;
typedef struct omnimind_generation omnimind_generation_t;

#ifdef __cplusplus
}
#endif

#endif // OMNIMIND_TYPES_H
