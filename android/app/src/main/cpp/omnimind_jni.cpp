#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <memory>
#include <mutex>
#include "omnimind_core.h"
#include "omnimind_types.h"

static JavaVM* g_vm = nullptr;

struct JniCallbackState {
    jobject callback_global_ref;
    jclass callback_class;
    jmethodID on_token_mid;
    jmethodID on_stats_mid;
    jmethodID on_complete_mid;
    jmethodID on_error_mid;
    jmethodID stats_init_mid;
    jclass stats_class;
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_vm = vm;
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    omnimind_init();
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) {
    omnimind_shutdown();
    g_vm = nullptr;
}

static JNIEnv* get_env(bool* attached) {
    *attached = false;
    JNIEnv* env = nullptr;
    if (!g_vm) return nullptr;
    jint res = g_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (res == JNI_EDETACHED) {
        if (g_vm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            *attached = true;
        }
    }
    return env;
}

static void detach_env(bool attached) {
    if (attached && g_vm) {
        g_vm->DetachCurrentThread();
    }
}

static jobject create_stats_object(JNIEnv* env, const omnimind_stats_t* stats) {
    if (!stats) return nullptr;
    jclass cls = env->FindClass("com/omnimind/domain/model/InferenceStats");
    if (!cls) return nullptr;
    jmethodID mid = env->GetMethodID(cls, "<init>", "(IIFFJJJJ)V");
    if (!mid) return nullptr;
    return env->NewObject(
        cls, mid,
        stats->prompt_tokens,
        stats->generated_tokens,
        stats->prompt_tokens_per_sec,
        stats->generation_tokens_per_sec,
        (jlong)stats->time_to_first_token_ms,
        (jlong)stats->total_time_ms,
        (jlong)stats->load_time_ms,
        (jlong)stats->peak_memory_bytes
    );
}

extern "C" {

JNIEXPORT jint JNICALL
Java_com_omnimind_native_LlamaEngine_nativeInit(JNIEnv* env, jobject thiz) {
    return (jint)omnimind_init();
}

JNIEXPORT void JNICALL
Java_com_omnimind_native_LlamaEngine_nativeShutdown(JNIEnv* env, jobject thiz) {
    omnimind_shutdown();
}

JNIEXPORT jint JNICALL
Java_com_omnimind_native_LlamaEngine_nativeValidateGguf(JNIEnv* env, jobject thiz, jstring jpath) {
    if (!jpath) return (jint)OMNIMIND_ERROR_INVALID_ARGUMENT;
    const char* path = env->GetStringUTFChars(jpath, nullptr);
    omnimind_status_t status = omnimind_validate_gguf(path);
    env->ReleaseStringUTFChars(jpath, path);
    return (jint)status;
}

JNIEXPORT jobject JNICALL
Java_com_omnimind_native_LlamaEngine_nativeExtractMetadata(JNIEnv* env, jobject thiz, jstring jpath) {
    if (!jpath) return nullptr;
    const char* path = env->GetStringUTFChars(jpath, nullptr);
    omnimind_model_metadata_t meta{};
    omnimind_status_t status = omnimind_extract_metadata(path, &meta);
    env->ReleaseStringUTFChars(jpath, path);

    if (status != OMNIMIND_OK) return nullptr;

    jclass cls = env->FindClass("com/omnimind/domain/model/ModelMetadata");
    if (!cls) return nullptr;

    jmethodID mid = env->GetMethodID(
        cls, "<init>",
        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;JLjava/lang/String;IIJLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V"
    );
    if (!mid) return nullptr;

    return env->NewObject(
        cls, mid,
        env->NewStringUTF(meta.name),
        env->NewStringUTF(meta.file_name),
        env->NewStringUTF(meta.architecture),
        (jlong)meta.parameter_count,
        env->NewStringUTF(meta.quantization),
        (jint)meta.context_length,
        (jint)meta.vocab_size,
        (jlong)meta.file_size,
        env->NewStringUTF(meta.author),
        env->NewStringUTF(meta.license),
        env->NewStringUTF(meta.chat_template)
    );
}

JNIEXPORT jlong JNICALL
Java_com_omnimind_native_LlamaEngine_nativeLoadModel(
    JNIEnv* env, jobject thiz,
    jstring jpath,
    jint context_size,
    jint threads,
    jint batch_size,
    jint gpu_layers
) {
    if (!jpath) return 0;
    const char* path = env->GetStringUTFChars(jpath, nullptr);

    omnimind_model_params_t params{};
    params.context_size = context_size;
    params.threads = threads;
    params.batch_size = batch_size;
    params.gpu_layers = gpu_layers;
    params.use_mmap = true;
    params.use_mlock = false;

    omnimind_model_t* model = nullptr;
    omnimind_status_t status = omnimind_load_model(path, &params, &model);
    env->ReleaseStringUTFChars(jpath, path);

    if (status != OMNIMIND_OK) {
        return 0;
    }
    return reinterpret_cast<jlong>(model);
}

JNIEXPORT void JNICALL
Java_com_omnimind_native_LlamaEngine_nativeUnloadModel(JNIEnv* env, jobject thiz, jlong handle) {
    if (!handle) return;
    auto* model = reinterpret_cast<omnimind_model_t*>(handle);
    omnimind_unload_model(model);
}

JNIEXPORT jobject JNICALL
Java_com_omnimind_native_LlamaEngine_nativeGetModelMetadata(JNIEnv* env, jobject thiz, jlong handle) {
    if (!handle) return nullptr;
    auto* model = reinterpret_cast<omnimind_model_t*>(handle);
    omnimind_model_metadata_t meta{};
    omnimind_status_t status = omnimind_get_model_metadata(model, &meta);
    if (status != OMNIMIND_OK) return nullptr;

    jclass cls = env->FindClass("com/omnimind/domain/model/ModelMetadata");
    if (!cls) return nullptr;

    jmethodID mid = env->GetMethodID(
        cls, "<init>",
        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;JLjava/lang/String;IIJLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V"
    );
    if (!mid) return nullptr;

    return env->NewObject(
        cls, mid,
        env->NewStringUTF(meta.name),
        env->NewStringUTF(meta.file_name),
        env->NewStringUTF(meta.architecture),
        (jlong)meta.parameter_count,
        env->NewStringUTF(meta.quantization),
        (jint)meta.context_length,
        (jint)meta.vocab_size,
        (jlong)meta.file_size,
        env->NewStringUTF(meta.author),
        env->NewStringUTF(meta.license),
        env->NewStringUTF(meta.chat_template)
    );
}

JNIEXPORT jint JNICALL
Java_com_omnimind_native_LlamaEngine_nativeTokenize(JNIEnv* env, jobject thiz, jlong handle, jstring jtext) {
    if (!handle || !jtext) return 0;
    auto* model = reinterpret_cast<omnimind_model_t*>(handle);
    const char* text = env->GetStringUTFChars(jtext, nullptr);
    int32_t count = omnimind_tokenize(model, text);
    env->ReleaseStringUTFChars(jtext, text);
    return count;
}

JNIEXPORT jstring JNICALL
Java_com_omnimind_native_LlamaEngine_nativeApplyChatTemplate(
    JNIEnv* env, jobject thiz,
    jlong handle,
    jobjectArray jroles,
    jobjectArray jcontents,
    jboolean add_assistant_prompt
) {
    if (!handle || !jroles || !jcontents) return nullptr;
    auto* model = reinterpret_cast<omnimind_model_t*>(handle);

    jsize count = env->GetArrayLength(jroles);
    if (count == 0) return env->NewStringUTF("");

    std::vector<std::string> roles_str(count);
    std::vector<std::string> contents_str(count);
    std::vector<omnimind_chat_message_t> messages(count);

    for (jsize i = 0; i < count; ++i) {
        auto r = (jstring)env->GetObjectArrayElement(jroles, i);
        auto c = (jstring)env->GetObjectArrayElement(jcontents, i);
        const char* r_chars = env->GetStringUTFChars(r, nullptr);
        const char* c_chars = env->GetStringUTFChars(c, nullptr);
        roles_str[i] = r_chars;
        contents_str[i] = c_chars;
        env->ReleaseStringUTFChars(r, r_chars);
        env->ReleaseStringUTFChars(c, c_chars);

        messages[i].role = roles_str[i].c_str();
        messages[i].content = contents_str[i].c_str();
    }

    int32_t req_size = omnimind_apply_chat_template(model, messages.data(), count, add_assistant_prompt, nullptr, 0);
    if (req_size <= 0) {
        return env->NewStringUTF("");
    }

    std::vector<char> buf(req_size + 1);
    omnimind_apply_chat_template(model, messages.data(), count, add_assistant_prompt, buf.data(), buf.size());
    return env->NewStringUTF(buf.data());
}

static void jni_event_callback(const omnimind_inference_event_t* event, void* user_data) {
    auto* state = reinterpret_cast<JniCallbackState*>(user_data);
    if (!state) return;

    bool attached = false;
    JNIEnv* env = get_env(&attached);
    if (!env) return;

    switch (event->type) {
        case OMNIMIND_EVENT_TOKEN: {
            if (state->on_token_mid && event->token) {
                jstring jtok = env->NewStringUTF(event->token);
                env->CallVoidMethod(state->callback_global_ref, state->on_token_mid, jtok);
                env->DeleteLocalRef(jtok);
            }
            break;
        }
        case OMNIMIND_EVENT_STATS: {
            if (state->on_stats_mid && event->stats) {
                jobject jstats = create_stats_object(env, event->stats);
                if (jstats) {
                    env->CallVoidMethod(state->callback_global_ref, state->on_stats_mid, jstats);
                    env->DeleteLocalRef(jstats);
                }
            }
            break;
        }
        case OMNIMIND_EVENT_COMPLETE: {
            if (state->on_complete_mid) {
                jobject jstats = create_stats_object(env, event->stats);
                env->CallVoidMethod(state->callback_global_ref, state->on_complete_mid, jstats);
                if (jstats) env->DeleteLocalRef(jstats);
            }
            // Cleanup state
            env->DeleteGlobalRef(state->callback_global_ref);
            env->DeleteGlobalRef(state->callback_class);
            if (state->stats_class) env->DeleteGlobalRef(state->stats_class);
            delete state;
            break;
        }
        case OMNIMIND_EVENT_ERROR: {
            if (state->on_error_mid) {
                jstring jerr = env->NewStringUTF(event->error_message ? event->error_message : "Unknown error");
                env->CallVoidMethod(state->callback_global_ref, state->on_error_mid, jerr, (jint)event->status);
                env->DeleteLocalRef(jerr);
            }
            // Cleanup state
            env->DeleteGlobalRef(state->callback_global_ref);
            env->DeleteGlobalRef(state->callback_class);
            if (state->stats_class) env->DeleteGlobalRef(state->stats_class);
            delete state;
            break;
        }
    }

    detach_env(attached);
}

JNIEXPORT jlong JNICALL
Java_com_omnimind_native_LlamaEngine_nativeStartGeneration(
    JNIEnv* env, jobject thiz,
    jlong handle,
    jstring jprompt,
    jfloat temperature,
    jfloat top_p,
    jint top_k,
    jfloat min_p,
    jfloat repeat_penalty,
    jfloat presence_penalty,
    jfloat frequency_penalty,
    jint max_tokens,
    jint seed,
    jobject jcallback
) {
    if (!handle || !jprompt || !jcallback) return 0;
    auto* model = reinterpret_cast<omnimind_model_t*>(handle);

    omnimind_generation_params_t params{};
    params.temperature = temperature;
    params.top_p = top_p;
    params.top_k = top_k;
    params.min_p = min_p;
    params.repeat_penalty = repeat_penalty;
    params.presence_penalty = presence_penalty;
    params.frequency_penalty = frequency_penalty;
    params.max_tokens = max_tokens;
    params.seed = (uint32_t)seed;

    auto* state = new JniCallbackState();
    state->callback_global_ref = env->NewGlobalRef(jcallback);

    jclass local_cb_cls = env->GetObjectClass(jcallback);
    state->callback_class = reinterpret_cast<jclass>(env->NewGlobalRef(local_cb_cls));

    state->on_token_mid = env->GetMethodID(state->callback_class, "onToken", "(Ljava/lang/String;)V");
    state->on_stats_mid = env->GetMethodID(state->callback_class, "onStats", "(Lcom/omnimind/domain/model/InferenceStats;)V");
    state->on_complete_mid = env->GetMethodID(state->callback_class, "onComplete", "(Lcom/omnimind/domain/model/InferenceStats;)V");
    state->on_error_mid = env->GetMethodID(state->callback_class, "onError", "(Ljava/lang/String;I)V");

    jclass local_stats_cls = env->FindClass("com/omnimind/domain/model/InferenceStats");
    if (local_stats_cls) {
        state->stats_class = reinterpret_cast<jclass>(env->NewGlobalRef(local_stats_cls));
    } else {
        state->stats_class = nullptr;
    }

    const char* prompt = env->GetStringUTFChars(jprompt, nullptr);
    omnimind_generation_t* gen = nullptr;
    omnimind_status_t status = omnimind_start_generation(
        model, prompt, &params, jni_event_callback, state, &gen
    );
    env->ReleaseStringUTFChars(jprompt, prompt);

    if (status != OMNIMIND_OK) {
        env->DeleteGlobalRef(state->callback_global_ref);
        env->DeleteGlobalRef(state->callback_class);
        if (state->stats_class) env->DeleteGlobalRef(state->stats_class);
        delete state;
        return 0;
    }

    return reinterpret_cast<jlong>(gen);
}

JNIEXPORT void JNICALL
Java_com_omnimind_native_LlamaEngine_nativeCancelGeneration(JNIEnv* env, jobject thiz, jlong gen_handle) {
    if (!gen_handle) return;
    auto* gen = reinterpret_cast<omnimind_generation_t*>(gen_handle);
    omnimind_cancel_generation(gen);
}

JNIEXPORT void JNICALL
Java_com_omnimind_native_LlamaEngine_nativeFreeGeneration(JNIEnv* env, jobject thiz, jlong gen_handle) {
    if (!gen_handle) return;
    auto* gen = reinterpret_cast<omnimind_generation_t*>(gen_handle);
    omnimind_free_generation(gen);
}

JNIEXPORT jobjectArray JNICALL
Java_com_omnimind_native_LlamaEngine_nativeGetAvailableBackends(JNIEnv* env, jobject thiz) {
    omnimind_backend_info_t backends[16];
    size_t count = 0;
    omnimind_get_available_backends(backends, 16, &count);

    jclass cls = env->FindClass("com/omnimind/domain/model/BackendInfo");
    if (!cls) return nullptr;

    jmethodID mid = env->GetMethodID(cls, "<init>", "(Ljava/lang/String;Ljava/lang/String;ZZ)V");
    if (!mid) return nullptr;

    jobjectArray arr = env->NewObjectArray((jsize)count, cls, nullptr);
    for (size_t i = 0; i < count; ++i) {
        jobject obj = env->NewObject(
            cls, mid,
            env->NewStringUTF(backends[i].name),
            env->NewStringUTF(backends[i].description),
            (jboolean)backends[i].is_accelerator,
            (jboolean)backends[i].is_available
        );
        env->SetObjectArrayElement(arr, (jsize)i, obj);
        env->DeleteLocalRef(obj);
    }
    return arr;
}

} // extern "C"
