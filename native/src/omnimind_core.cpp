#include "omnimind_core.h"
#include "omnimind_engine.hpp"

#include "llama.h"
#include "ggml.h"
#include "ggml-backend.h"
#include "gguf.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>
#include <atomic>
#include <thread>
#include <mutex>
#include <chrono>
#include <algorithm>
#include <sys/stat.h>

struct omnimind_model {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    const llama_vocab* vocab = nullptr;
    std::string path;
    omnimind_model_params_t params;
    omnimind_model_metadata_t metadata;
    int64_t load_time_ms = 0;
    std::mutex mutex;
    std::atomic<bool> is_generating{false};
};

struct omnimind_generation {
    omnimind_model* model = nullptr;
    std::atomic<bool> cancelled{false};
    std::atomic<bool> completed{false};
    std::thread worker;
    omnimind_stats_t stats{};
};

static std::atomic<bool> g_initialized{false};
static std::mutex g_init_mutex;

static const char* ftype_to_quant_str(enum llama_ftype ftype) {
    switch (ftype) {
        case LLAMA_FTYPE_ALL_F32: return "F32";
        case LLAMA_FTYPE_MOSTLY_F16: return "F16";
        case LLAMA_FTYPE_MOSTLY_Q4_0: return "Q4_0";
        case LLAMA_FTYPE_MOSTLY_Q4_1: return "Q4_1";
        case LLAMA_FTYPE_MOSTLY_Q8_0: return "Q8_0";
        case LLAMA_FTYPE_MOSTLY_Q5_0: return "Q5_0";
        case LLAMA_FTYPE_MOSTLY_Q5_1: return "Q5_1";
        case LLAMA_FTYPE_MOSTLY_Q2_K: return "Q2_K";
        case LLAMA_FTYPE_MOSTLY_Q3_K_S: return "Q3_K_S";
        case LLAMA_FTYPE_MOSTLY_Q3_K_M: return "Q3_K_M";
        case LLAMA_FTYPE_MOSTLY_Q3_K_L: return "Q3_K_L";
        case LLAMA_FTYPE_MOSTLY_Q4_K_S: return "Q4_K_S";
        case LLAMA_FTYPE_MOSTLY_Q4_K_M: return "Q4_K_M";
        case LLAMA_FTYPE_MOSTLY_Q5_K_S: return "Q5_K_S";
        case LLAMA_FTYPE_MOSTLY_Q5_K_M: return "Q5_K_M";
        case LLAMA_FTYPE_MOSTLY_Q6_K: return "Q6_K";
        case LLAMA_FTYPE_MOSTLY_IQ2_XXS: return "IQ2_XXS";
        case LLAMA_FTYPE_MOSTLY_IQ2_XS: return "IQ2_XS";
        case LLAMA_FTYPE_MOSTLY_Q2_K_S: return "Q2_K_S";
        case LLAMA_FTYPE_MOSTLY_IQ3_XS: return "IQ3_XS";
        case LLAMA_FTYPE_MOSTLY_IQ3_XXS: return "IQ3_XXS";
        case LLAMA_FTYPE_MOSTLY_IQ1_S: return "IQ1_S";
        case LLAMA_FTYPE_MOSTLY_IQ4_NL: return "IQ4_NL";
        case LLAMA_FTYPE_MOSTLY_IQ3_S: return "IQ3_S";
        case LLAMA_FTYPE_MOSTLY_IQ3_M: return "IQ3_M";
        case LLAMA_FTYPE_MOSTLY_IQ2_S: return "IQ2_S";
        case LLAMA_FTYPE_MOSTLY_IQ2_M: return "IQ2_M";
        case LLAMA_FTYPE_MOSTLY_IQ4_XS: return "IQ4_XS";
        case LLAMA_FTYPE_MOSTLY_IQ1_M: return "IQ1_M";
        case LLAMA_FTYPE_MOSTLY_BF16: return "BF16";
        default: return "UNKNOWN";
    }
}

static int64_t get_file_size(const char* path) {
    struct stat st;
    if (stat(path, &st) == 0) {
        return (int64_t)st.st_size;
    }
    return 0;
}

static void safe_strncpy(char* dest, const char* src, size_t max_len) {
    if (!dest || max_len == 0) return;
    if (!src) {
        dest[0] = '\0';
        return;
    }
    strncpy(dest, src, max_len - 1);
    dest[max_len - 1] = '\0';
}

extern "C" {

OMNIMIND_API omnimind_status_t omnimind_init(void) {
    std::lock_guard<std::mutex> lock(g_init_mutex);
    if (g_initialized.load()) {
        return OMNIMIND_OK;
    }
    llama_backend_init();
    ggml_backend_load_all();
    g_initialized.store(true);
    return OMNIMIND_OK;
}

OMNIMIND_API void omnimind_shutdown(void) {
    std::lock_guard<std::mutex> lock(g_init_mutex);
    if (!g_initialized.load()) {
        return;
    }
    llama_backend_free();
    g_initialized.store(false);
}

OMNIMIND_API omnimind_status_t omnimind_validate_gguf(const char* path) {
    if (!path || strlen(path) == 0) {
        return OMNIMIND_ERROR_INVALID_ARGUMENT;
    }

    FILE* f = fopen(path, "rb");
    if (!f) {
        return OMNIMIND_ERROR_FILE_NOT_FOUND;
    }

    char magic[4];
    size_t read_bytes = fread(magic, 1, 4, f);
    if (read_bytes < 4) {
        fclose(f);
        return OMNIMIND_ERROR_INVALID_GGUF;
    }

    // GGUF magic is 'G', 'G', 'U', 'F'
    if (magic[0] != 'G' || magic[1] != 'G' || magic[2] != 'U' || magic[3] != 'F') {
        fclose(f);
        return OMNIMIND_ERROR_INVALID_GGUF;
    }

    uint32_t version = 0;
    if (fread(&version, sizeof(version), 1, f) != 1) {
        fclose(f);
        return OMNIMIND_ERROR_INVALID_GGUF;
    }
    fclose(f);

    if (version < 2 || version > 3) {
        return OMNIMIND_ERROR_INVALID_GGUF;
    }

    // Try initializing GGUF context (without allocating tensors)
    struct gguf_init_params params = { true, nullptr };
    struct gguf_context* ctx = gguf_init_from_file(path, params);
    if (!ctx) {
        return OMNIMIND_ERROR_INVALID_GGUF;
    }
    gguf_free(ctx);

    return OMNIMIND_OK;
}

OMNIMIND_API omnimind_status_t omnimind_extract_metadata(
    const char* path,
    omnimind_model_metadata_t* out_meta
) {
    if (!path || !out_meta) {
        return OMNIMIND_ERROR_INVALID_ARGUMENT;
    }

    memset(out_meta, 0, sizeof(omnimind_model_metadata_t));
    out_meta->file_size = get_file_size(path);

    // Extract filename from path
    const char* last_slash = strrchr(path, '/');
    const char* last_backslash = strrchr(path, '\\');
    const char* fname = path;
    if (last_slash && last_slash >= fname) fname = last_slash + 1;
    if (last_backslash && last_backslash >= fname) fname = last_backslash + 1;
    safe_strncpy(out_meta->file_name, fname, sizeof(out_meta->file_name));

    struct gguf_init_params params = { true, nullptr };
    struct gguf_context* ctx = gguf_init_from_file(path, params);
    if (!ctx) {
        return OMNIMIND_ERROR_INVALID_GGUF;
    }

    // Read general metadata
    int64_t key_arch = gguf_find_key(ctx, "general.architecture");
    if (key_arch >= 0) {
        const char* arch = gguf_get_val_str(ctx, key_arch);
        safe_strncpy(out_meta->architecture, arch, sizeof(out_meta->architecture));
    }

    int64_t key_name = gguf_find_key(ctx, "general.name");
    if (key_name >= 0) {
        safe_strncpy(out_meta->name, gguf_get_val_str(ctx, key_name), sizeof(out_meta->name));
    } else {
        safe_strncpy(out_meta->name, fname, sizeof(out_meta->name));
    }

    int64_t key_author = gguf_find_key(ctx, "general.author");
    if (key_author >= 0) {
        safe_strncpy(out_meta->author, gguf_get_val_str(ctx, key_author), sizeof(out_meta->author));
    }

    int64_t key_license = gguf_find_key(ctx, "general.license");
    if (key_license >= 0) {
        safe_strncpy(out_meta->license, gguf_get_val_str(ctx, key_license), sizeof(out_meta->license));
    }

    int64_t key_ftype = gguf_find_key(ctx, "general.file_type");
    if (key_ftype >= 0) {
        uint32_t ft = gguf_get_val_u32(ctx, key_ftype);
        safe_strncpy(out_meta->quantization, ftype_to_quant_str((enum llama_ftype)ft), sizeof(out_meta->quantization));
    } else {
        safe_strncpy(out_meta->quantization, "UNKNOWN", sizeof(out_meta->quantization));
    }

    // Context length for architecture: "<arch>.context_length"
    if (out_meta->architecture[0] != '\0') {
        std::string ctx_key = std::string(out_meta->architecture) + ".context_length";
        int64_t key_ctx = gguf_find_key(ctx, ctx_key.c_str());
        if (key_ctx >= 0) {
            out_meta->context_length = (int32_t)gguf_get_val_u32(ctx, key_ctx);
        }
    }

    // Chat template
    int64_t key_tmpl = gguf_find_key(ctx, "tokenizer.chat_template");
    if (key_tmpl >= 0) {
        safe_strncpy(out_meta->chat_template, gguf_get_val_str(ctx, key_tmpl), sizeof(out_meta->chat_template));
    }

    // Vocabulary size
    int64_t key_vocab = gguf_find_key(ctx, "tokenizer.ggml.tokens");
    if (key_vocab >= 0 && gguf_get_kv_type(ctx, key_vocab) == GGUF_TYPE_ARRAY) {
        out_meta->vocab_size = (int32_t)gguf_get_arr_n(ctx, key_vocab);
    }

    // Estimate parameter count from tensors
    int64_t n_tensors = gguf_get_n_tensors(ctx);
    int64_t total_params = 0;
    for (int64_t i = 0; i < n_tensors; ++i) {
        const int64_t* ne = gguf_get_tensor_ne(ctx, i);
        if (ne) {
            int64_t p = ne[0] * ne[1] * ne[2] * ne[3];
            if (p > 0) total_params += p;
        }
    }
    out_meta->parameter_count = total_params;

    gguf_free(ctx);
    return OMNIMIND_OK;
}

OMNIMIND_API omnimind_status_t omnimind_load_model(
    const char* path,
    const omnimind_model_params_t* params,
    omnimind_model_t** out_model
) {
    if (!path || !out_model) {
        return OMNIMIND_ERROR_INVALID_ARGUMENT;
    }
    *out_model = nullptr;

    omnimind_init();

    omnimind_status_t val_status = omnimind_validate_gguf(path);
    if (val_status != OMNIMIND_OK) {
        return val_status;
    }

    auto start_time = std::chrono::steady_clock::now();

    struct llama_model_params mparams = llama_model_default_params();
    if (params) {
        mparams.n_gpu_layers = params->gpu_layers;
        if (params->use_mmap && params->use_mlock) {
            mparams.load_mode = LLAMA_LOAD_MODE_MMAP_MLOCK;
        } else if (params->use_mmap) {
            mparams.load_mode = LLAMA_LOAD_MODE_MMAP;
        } else if (params->use_mlock) {
            mparams.load_mode = LLAMA_LOAD_MODE_MLOCK;
        } else {
            mparams.load_mode = LLAMA_LOAD_MODE_NONE;
        }
    }

    llama_model* model = llama_model_load_from_file(path, mparams);
    if (!model) {
        // Fallback to CPU only if GPU loading failed
        if (params && params->gpu_layers > 0) {
            mparams.n_gpu_layers = 0;
            model = llama_model_load_from_file(path, mparams);
        }
        if (!model) {
            return OMNIMIND_ERROR_LOAD_FAILED;
        }
    }

    struct llama_context_params cparams = llama_context_default_params();
    int32_t ctx_size = (params && params->context_size > 0) ? params->context_size : 4096;
    int32_t threads = (params && params->threads > 0) ? params->threads : 4;
    int32_t batch_size = (params && params->batch_size > 0) ? params->batch_size : 512;

    cparams.n_ctx = ctx_size;
    cparams.n_threads = threads;
    cparams.n_threads_batch = threads;
    cparams.n_batch = batch_size;

    llama_context* ctx = llama_init_from_model(model, cparams);
    if (!ctx) {
        llama_model_free(model);
        return OMNIMIND_ERROR_LOAD_FAILED;
    }

    auto end_time = std::chrono::steady_clock::now();
    int64_t load_time = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();

    auto* wrapper = new omnimind_model();
    wrapper->model = model;
    wrapper->ctx = ctx;
    wrapper->vocab = llama_model_get_vocab(model);
    wrapper->path = path;
    if (params) {
        wrapper->params = *params;
    } else {
        wrapper->params.context_size = ctx_size;
        wrapper->params.threads = threads;
        wrapper->params.batch_size = batch_size;
        wrapper->params.gpu_layers = 0;
        wrapper->params.use_mmap = true;
        wrapper->params.use_mlock = false;
    }
    wrapper->load_time_ms = load_time;

    omnimind_extract_metadata(path, &wrapper->metadata);

    *out_model = wrapper;
    return OMNIMIND_OK;
}

OMNIMIND_API void omnimind_unload_model(omnimind_model_t* model) {
    if (!model) return;

    std::lock_guard<std::mutex> lock(model->mutex);
    if (model->ctx) {
        llama_free(model->ctx);
        model->ctx = nullptr;
    }
    if (model->model) {
        llama_model_free(model->model);
        model->model = nullptr;
    }
    delete model;
}

OMNIMIND_API omnimind_status_t omnimind_get_model_metadata(
    const omnimind_model_t* model,
    omnimind_model_metadata_t* out_meta
) {
    if (!model || !out_meta) {
        return OMNIMIND_ERROR_INVALID_ARGUMENT;
    }
    *out_meta = model->metadata;
    return OMNIMIND_OK;
}

OMNIMIND_API int32_t omnimind_tokenize(
    const omnimind_model_t* model,
    const char* text
) {
    if (!model || !model->vocab || !text) {
        return 0;
    }
    int len = (int)strlen(text);
    int32_t n_tokens = llama_tokenize(model->vocab, text, len, nullptr, 0, true, true);
    return n_tokens < 0 ? -n_tokens : n_tokens;
}

OMNIMIND_API int32_t omnimind_apply_chat_template(
    const omnimind_model_t* model,
    const omnimind_chat_message_t* messages,
    size_t n_messages,
    bool add_assistant_prompt,
    char* out_buf,
    size_t buf_size
) {
    if (!model || !messages || n_messages == 0) {
        return 0;
    }

    std::vector<llama_chat_message> chat;
    chat.reserve(n_messages);
    for (size_t i = 0; i < n_messages; ++i) {
        llama_chat_message msg;
        msg.role = messages[i].role;
        msg.content = messages[i].content;
        chat.push_back(msg);
    }

    const char* tmpl = nullptr;
    if (model->metadata.chat_template[0] != '\0') {
        tmpl = model->metadata.chat_template;
    }

    return llama_chat_apply_template(
        tmpl,
        chat.data(),
        chat.size(),
        add_assistant_prompt,
        out_buf,
        (int32_t)buf_size
    );
}

OMNIMIND_API omnimind_status_t omnimind_start_generation(
    omnimind_model_t* model,
    const char* prompt,
    const omnimind_generation_params_t* params,
    omnimind_event_callback_t callback,
    void* user_data,
    omnimind_generation_t** out_gen
) {
    if (!model || !model->ctx || !model->model || !prompt || !out_gen) {
        return OMNIMIND_ERROR_INVALID_ARGUMENT;
    }
    *out_gen = nullptr;

    bool expected = false;
    if (!model->is_generating.compare_exchange_strong(expected, true)) {
        return OMNIMIND_ERROR_ALREADY_LOADED; // Currently generating
    }

    auto* gen = new omnimind_generation();
    gen->model = model;
    gen->cancelled.store(false);
    gen->completed.store(false);

    omnimind_generation_params_t gen_params;
    if (params) {
        gen_params = *params;
    } else {
        gen_params.temperature = 0.7f;
        gen_params.top_p = 0.9f;
        gen_params.top_k = 40;
        gen_params.min_p = 0.05f;
        gen_params.repeat_penalty = 1.1f;
        gen_params.presence_penalty = 0.0f;
        gen_params.frequency_penalty = 0.0f;
        gen_params.max_tokens = 2048;
        gen_params.seed = 0xFFFFFFFF;
    }

    std::string prompt_str(prompt);

    gen->worker = std::thread([gen, model, prompt_str, gen_params, callback, user_data]() {
        auto t_start = std::chrono::steady_clock::now();
        gen->stats.load_time_ms = model->load_time_ms;

        // Tokenize prompt
        int32_t n_prompt_tokens = omnimind_tokenize(model, prompt_str.c_str());
        if (n_prompt_tokens <= 0) {
            if (callback) {
                omnimind_inference_event_t ev{};
                ev.type = OMNIMIND_EVENT_ERROR;
                ev.error_message = "Failed to tokenize prompt";
                ev.status = OMNIMIND_ERROR_GENERATION_FAILED;
                callback(&ev, user_data);
            }
            model->is_generating.store(false);
            gen->completed.store(true);
            return;
        }

        std::vector<llama_token> prompt_tokens(n_prompt_tokens);
        llama_tokenize(
            model->vocab,
            prompt_str.c_str(),
            (int32_t)prompt_str.length(),
            prompt_tokens.data(),
            n_prompt_tokens,
            true,
            true
        );

        gen->stats.prompt_tokens = n_prompt_tokens;

        // Clear existing KV cache
        llama_memory_t mem = llama_get_memory(model->ctx);
        if (mem) {
            llama_memory_clear(mem, true);
        }

        // Prepare sampling chain
        llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
        llama_sampler* smpl = llama_sampler_chain_init(sparams);
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(gen_params.top_k));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(gen_params.top_p, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_min_p(gen_params.min_p, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(gen_params.temperature));

        int32_t n_vocab = llama_vocab_n_tokens(model->vocab);
        llama_sampler_chain_add(smpl, llama_sampler_init_penalties(
            n_vocab,
            64,
            gen_params.repeat_penalty,
            gen_params.frequency_penalty,
            gen_params.presence_penalty
        ));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(gen_params.seed));

        // Evaluate prompt in batches
        auto t_prompt_start = std::chrono::steady_clock::now();
        int32_t batch_size = model->params.batch_size > 0 ? model->params.batch_size : 512;
        int32_t n_past = 0;

        for (int32_t i = 0; i < n_prompt_tokens; i += batch_size) {
            if (gen->cancelled.load()) {
                break;
            }
            int32_t n_eval = std::min(batch_size, n_prompt_tokens - i);
            llama_batch batch = llama_batch_get_one(&prompt_tokens[i], n_eval);
            if (llama_decode(model->ctx, batch) != 0) {
                if (callback) {
                    omnimind_inference_event_t ev{};
                    ev.type = OMNIMIND_EVENT_ERROR;
                    ev.error_message = "Prompt decode failed";
                    ev.status = OMNIMIND_ERROR_GENERATION_FAILED;
                    callback(&ev, user_data);
                }
                llama_sampler_free(smpl);
                model->is_generating.store(false);
                gen->completed.store(true);
                return;
            }
            n_past += n_eval;
        }

        auto t_prompt_end = std::chrono::steady_clock::now();
        float prompt_sec = std::chrono::duration<float>(t_prompt_end - t_prompt_start).count();
        gen->stats.prompt_tokens_per_sec = prompt_sec > 0.0f ? (float)n_prompt_tokens / prompt_sec : 0.0f;

        // Generation loop
        int32_t n_gen = 0;
        int32_t max_tokens = gen_params.max_tokens > 0 ? gen_params.max_tokens : 2048;
        bool is_first = true;
        auto t_gen_start = std::chrono::steady_clock::now();

        while (n_gen < max_tokens && !gen->cancelled.load()) {
            llama_token new_token = llama_sampler_sample(smpl, model->ctx, -1);
            llama_sampler_accept(smpl, new_token);

            if (is_first) {
                auto t_first = std::chrono::steady_clock::now();
                gen->stats.time_to_first_token_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_first - t_start).count();
                is_first = false;
            }

            if (llama_vocab_is_eog(model->vocab, new_token)) {
                break;
            }

            // Convert token to text piece
            char piece_buf[256];
            int32_t n_piece = llama_token_to_piece(model->vocab, new_token, piece_buf, sizeof(piece_buf), 0, false);
            if (n_piece > 0) {
                piece_buf[std::min((size_t)n_piece, sizeof(piece_buf) - 1)] = '\0';
                if (callback) {
                    omnimind_inference_event_t ev{};
                    ev.type = OMNIMIND_EVENT_TOKEN;
                    ev.token = piece_buf;
                    ev.status = OMNIMIND_OK;
                    callback(&ev, user_data);
                }
            }

            n_gen++;
            gen->stats.generated_tokens = n_gen;

            // Decode next single token
            llama_batch next_batch = llama_batch_get_one(&new_token, 1);
            if (llama_decode(model->ctx, next_batch) != 0) {
                break;
            }
        }

        auto t_end = std::chrono::steady_clock::now();
        float gen_sec = std::chrono::duration<float>(t_end - t_gen_start).count();
        gen->stats.generation_tokens_per_sec = gen_sec > 0.0f ? (float)n_gen / gen_sec : 0.0f;
        gen->stats.total_time_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_end - t_start).count();

        llama_sampler_free(smpl);

        if (callback) {
            omnimind_inference_event_t ev{};
            ev.type = OMNIMIND_EVENT_COMPLETE;
            ev.stats = &gen->stats;
            ev.status = gen->cancelled.load() ? OMNIMIND_ERROR_CANCELLED : OMNIMIND_OK;
            callback(&ev, user_data);
        }

        model->is_generating.store(false);
        gen->completed.store(true);
    });

    *out_gen = gen;
    return OMNIMIND_OK;
}

OMNIMIND_API omnimind_status_t omnimind_cancel_generation(omnimind_generation_t* gen) {
    if (!gen) return OMNIMIND_ERROR_INVALID_ARGUMENT;
    gen->cancelled.store(true);
    return OMNIMIND_OK;
}

OMNIMIND_API void omnimind_free_generation(omnimind_generation_t* gen) {
    if (!gen) return;
    gen->cancelled.store(true);
    if (gen->worker.joinable()) {
        gen->worker.join();
    }
    delete gen;
}

OMNIMIND_API omnimind_status_t omnimind_get_available_backends(
    omnimind_backend_info_t* out_backends,
    size_t max_count,
    size_t* out_count
) {
    if (!out_backends || max_count == 0 || !out_count) {
        return OMNIMIND_ERROR_INVALID_ARGUMENT;
    }

    omnimind_init();

    size_t dev_count = ggml_backend_dev_count();
    size_t count = 0;

    for (size_t i = 0; i < dev_count && count < max_count; ++i) {
        ggml_backend_dev_t dev = ggml_backend_dev_get(i);
        if (!dev) continue;

        const char* name = ggml_backend_dev_name(dev);
        const char* desc = ggml_backend_dev_description(dev);

        safe_strncpy(out_backends[count].name, name ? name : "Unknown", sizeof(out_backends[count].name));
        safe_strncpy(out_backends[count].description, desc ? desc : "", sizeof(out_backends[count].description));

        enum ggml_backend_dev_type type = ggml_backend_dev_type(dev);
        out_backends[count].is_accelerator = (type == GGML_BACKEND_DEVICE_TYPE_GPU ||
                                              type == GGML_BACKEND_DEVICE_TYPE_IGPU ||
                                              type == GGML_BACKEND_DEVICE_TYPE_ACCEL);
        out_backends[count].is_available = true;
        count++;
    }

    // Always ensure at least CPU is listed if no devices found
    if (count == 0 && max_count > 0) {
        safe_strncpy(out_backends[0].name, "CPU", sizeof(out_backends[0].name));
        safe_strncpy(out_backends[0].description, "Standard Host CPU (ARM NEON / AVX)", sizeof(out_backends[0].description));
        out_backends[0].is_accelerator = false;
        out_backends[0].is_available = true;
        count = 1;
    }

    *out_count = count;
    return OMNIMIND_OK;
}

OMNIMIND_API const char* omnimind_status_str(omnimind_status_t status) {
    switch (status) {
        case OMNIMIND_OK: return "OK";
        case OMNIMIND_ERROR_INVALID_ARGUMENT: return "Invalid argument";
        case OMNIMIND_ERROR_FILE_NOT_FOUND: return "File not found";
        case OMNIMIND_ERROR_INVALID_GGUF: return "Invalid GGUF file";
        case OMNIMIND_ERROR_UNSUPPORTED_ARCH: return "Unsupported model architecture";
        case OMNIMIND_ERROR_UNSUPPORTED_BACKEND: return "Unsupported backend";
        case OMNIMIND_ERROR_LOAD_FAILED: return "Model load failed";
        case OMNIMIND_ERROR_OUT_OF_MEMORY: return "Out of memory";
        case OMNIMIND_ERROR_NOT_LOADED: return "Model not loaded";
        case OMNIMIND_ERROR_ALREADY_LOADED: return "Operation already in progress";
        case OMNIMIND_ERROR_GENERATION_FAILED: return "Generation failed";
        case OMNIMIND_ERROR_CANCELLED: return "Operation cancelled";
        case OMNIMIND_ERROR_BUFFER_TOO_SMALL: return "Buffer too small";
        default: return "Unknown error";
    }
}

} // extern "C"

namespace omnimind {

omnimind_status_t ModelManager::validate_gguf(const std::string& path) {
    return omnimind_validate_gguf(path.c_str());
}

omnimind_status_t ModelManager::extract_metadata(const std::string& path, ModelMetadata& out_meta) {
    omnimind_model_metadata_t raw{};
    omnimind_status_t status = omnimind_extract_metadata(path.c_str(), &raw);
    if (status != OMNIMIND_OK) return status;

    out_meta.name = raw.name;
    out_meta.file_name = raw.file_name;
    out_meta.architecture = raw.architecture;
    out_meta.parameter_count = raw.parameter_count;
    out_meta.quantization = raw.quantization;
    out_meta.context_length = raw.context_length;
    out_meta.vocab_size = raw.vocab_size;
    out_meta.file_size = raw.file_size;
    out_meta.author = raw.author;
    out_meta.license = raw.license;
    out_meta.chat_template = raw.chat_template;
    return OMNIMIND_OK;
}

std::vector<omnimind_backend_info_t> ModelManager::get_available_backends() {
    omnimind_backend_info_t backends[16];
    size_t count = 0;
    omnimind_get_available_backends(backends, 16, &count);
    return std::vector<omnimind_backend_info_t>(backends, backends + count);
}

} // namespace omnimind
