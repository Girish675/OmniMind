#ifndef OMNIMIND_ENGINE_HPP
#define OMNIMIND_ENGINE_HPP

#include "omnimind_types.h"
#include <string>
#include <vector>
#include <memory>
#include <atomic>
#include <thread>
#include <mutex>
#include <functional>
#include <chrono>

namespace omnimind {

struct ModelMetadata {
    std::string name;
    std::string file_name;
    std::string architecture;
    int64_t parameter_count = 0;
    std::string quantization;
    int32_t context_length = 0;
    int32_t vocab_size = 0;
    int64_t file_size = 0;
    std::string author;
    std::string license;
    std::string chat_template;
};

struct GenerationConfig {
    float temperature = 0.7f;
    float top_p = 0.9f;
    int32_t top_k = 40;
    float min_p = 0.05f;
    float repeat_penalty = 1.1f;
    float presence_penalty = 0.0f;
    float frequency_penalty = 0.0f;
    int32_t max_tokens = 2048;
    uint32_t seed = 0xFFFFFFFF;
};

struct ModelParams {
    int32_t context_size = 4096;
    int32_t threads = 4;
    int32_t batch_size = 512;
    int32_t gpu_layers = 0;
    bool use_mmap = true;
    bool use_mlock = false;
};

struct GenerationResult {
    omnimind_status_t status = OMNIMIND_OK;
    int32_t prompt_tokens = 0;
    int32_t generated_tokens = 0;
    float prompt_tokens_per_sec = 0.0f;
    float generation_tokens_per_sec = 0.0f;
    int64_t time_to_first_token_ms = 0;
    int64_t total_time_ms = 0;
    std::string generated_text;
};

class InferenceSession {
public:
    virtual ~InferenceSession() = default;
    virtual void cancel() = 0;
    virtual bool is_cancelled() const = 0;
    virtual void wait() = 0;
};

class InferenceEngine {
public:
    virtual ~InferenceEngine() = default;
    virtual omnimind_status_t load_model(const std::string& path, const ModelParams& params) = 0;
    virtual void unload_model() = 0;
    virtual bool is_model_loaded() const = 0;
    virtual ModelMetadata get_model_metadata() const = 0;
    virtual int32_t tokenize(const std::string& text) const = 0;
    virtual std::string apply_chat_template(
        const std::vector<std::pair<std::string, std::string>>& messages,
        bool add_assistant_prompt
    ) const = 0;
    virtual std::shared_ptr<InferenceSession> start_generation(
        const std::string& prompt,
        const GenerationConfig& config,
        std::function<void(const std::string& token)> token_callback,
        std::function<void(const GenerationResult& result)> complete_callback,
        std::function<void(const std::string& error)> error_callback
    ) = 0;
};

class ModelManager {
public:
    static omnimind_status_t validate_gguf(const std::string& path);
    static omnimind_status_t extract_metadata(const std::string& path, ModelMetadata& out_meta);
    static std::vector<omnimind_backend_info_t> get_available_backends();
};

} // namespace omnimind

#endif // OMNIMIND_ENGINE_HPP
