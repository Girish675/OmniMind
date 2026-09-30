# OmniMind — Technical Design Document

## Table of Contents

- [1. Overview](#1-overview)
- [2. Technical Decisions](#2-technical-decisions)
- [3. Inference Core (Component A)](#3-inference-core-component-a)
- [4. Model Manager (Component B)](#4-model-manager-component-b)
- [5. Chat Engine (Component C)](#5-chat-engine-component-c)
- [6. Storage (Component D)](#6-storage-component-d)
- [7. Android Architecture (Component E)](#7-android-architecture-component-e)
- [8. Desktop Architecture (Component F)](#8-desktop-architecture-component-f)
- [9. Web Interface (Component G)](#9-web-interface-component-g)
- [10. API Design (Component H)](#10-api-design-component-h)
- [11. Security (Component I)](#11-security-component-i)
- [12. Performance (Component J)](#12-performance-component-j)
- [13. Model Selection Strategy (Component K)](#13-model-selection-strategy-component-k)
- [14. Observability (Component L)](#14-observability-component-l)
- [15. Failure Recovery (Component M)](#15-failure-recovery-component-m)
- [16. Testing Strategy (Component N)](#16-testing-strategy-component-n)
- [17. Repository Layout (Component O)](#17-repository-layout-component-o)
- [18. Dependency Policy (Component P)](#18-dependency-policy-component-p)
- [19. Assumptions](#19-assumptions)

---

## 1. Overview

OmniMind is a local-first LLM chat application with three user-facing targets:

1. **Android** — native Kotlin/Compose app with in-process llama.cpp via JNI.
2. **Desktop** — Tauri 2 app (React/TypeScript frontend) managing a local llama-server process.
3. **Web** — React SPA communicating with a local llama-server over HTTP/SSE.

All inference runs locally. Model weights remain on the device/machine. No cloud services are used for inference, storage, or telemetry.

---

## 2. Technical Decisions

### Decision 1: Why llama.cpp

llama.cpp is used instead of implementing transformer inference from scratch because:

- It is the most mature, actively maintained open-source GGUF inference engine.
- It supports ARM64 NEON, x86-64 AVX2/AVX-512, and optional GPU backends (Vulkan, OpenCL, CUDA, Metal).
- It provides a ready-made local HTTP server (`llama-server`) with OpenAI-compatible API endpoints.
- It handles quantized model loading, tokenization, KV caching, sampling strategies, and chat template application.
- Building a transformer inference engine from scratch would take months and produce an inferior result for no architectural benefit.
- MIT license is compatible with Apache-2.0.

### Decision 2: Why GGUF

GGUF is the initial model format because:

- It is a single-file format containing weights, tokenizer, and metadata — no auxiliary files needed.
- llama.cpp natively loads GGUF with no conversion step.
- GGUF files are memory-mappable, reducing load time and peak memory.
- Metadata (architecture, context length, chat template) is embedded and machine-readable.
- It supports all major quantization schemes (Q4_K_M, Q5_K_M, Q6_K, Q8_0, etc.).
- It is the de facto standard for local inference.

### Decision 3: Why Qwen3-4B Q4_K_M as Initial Reference

- **Size**: The Q4_K_M quantization is approximately 2.5 GB — fits comfortably in 8 GB device RAM alongside Android OS overhead (2–4 GB) and app overhead.
- **Quality**: Q4_K_M provides a good balance between model quality and memory/speed. It is a "medium" quantization that retains most of the model's capabilities.
- **Architecture**: Qwen3-4B uses 36 layers, 32 query heads, 8 KV heads (GQA). Grouped-Query Attention is efficient for inference.
- **Context**: 32K native context. Practical on mobile at 2K–4K; desktop can handle more.
- **Features**: Dual-mode reasoning (thinking/non-thinking), multilingual, strong coding performance.
- **License**: Apache-2.0 — fully permissive, no restrictions on use.
- **Availability**: Official GGUF files published on Hugging Face by Qwen team.

### Decision 4: Why Android CPU Inference Must Be the Baseline

- The Snapdragon 7s Gen 2 (Adreno 710) is a mid-range SoC. GPU acceleration via OpenCL or Vulkan on this specific chip is **not verified** to provide a speedup over optimized ARM64 NEON CPU inference.
- llama.cpp's OpenCL backend documentation primarily lists high-end Adreno GPUs (8xx series, Adreno 810+). The Adreno 710 may work but is not explicitly validated.
- CPU inference on ARM64 with NEON and dot-product instructions is well-tested and reliable.
- GPU offloading introduces complexity (driver availability, shared memory overhead, potential regressions).
- The design must guarantee that the app works on the target device *today* with CPU-only inference. GPU acceleration is a bonus, not a requirement.

### Decision 5: Optional GPU/Accelerator Detection and Benchmarking

The architecture supports runtime backend detection without requiring GPU backends to be present:

1. At app startup (or on-demand), query available GGML backends via `llama_backend_init()` / device enumeration.
2. If OpenCL or Vulkan libraries are available on the device, list them as options in the benchmark/settings UI.
3. The benchmark subsystem can run the same prompt on CPU-only and on GPU-offloaded configurations.
4. The user compares results and chooses their preferred backend.
5. If a GPU backend fails to initialize, the app falls back to CPU silently.

This is achieved at build time by compiling llama.cpp with optional OpenCL/Vulkan support and detecting library availability at runtime. The native bridge reports available backends to the UI layer.

### Decision 6: Desktop App Owns a Local llama-server Process

The desktop application (Tauri 2) manages a local `llama-server` process because:

- Tauri's webview cannot load multi-GB models directly into browser memory.
- Tauri's Rust backend can spawn, monitor, and terminate child processes reliably.
- `llama-server` provides a stable, OpenAI-compatible HTTP API that the React frontend consumes.
- This pattern separates inference (native C++) from UI (React/TypeScript) cleanly.
- The server binds to `127.0.0.1` by default — no network exposure.
- A dynamic port is allocated to avoid conflicts.
- The Rust backend manages the server lifecycle: start on model load, restart on crash, stop on app exit.

### Decision 7: Browser Communicates with Local Inference Host

The browser cannot perform client-side model execution because:

- Multi-GB GGUF files cannot be practically loaded into browser memory.
- WebAssembly has memory limits and lacks efficient SIMD comparable to native ARM64 NEON or x86 AVX2.
- Browser tabs can be throttled, backgrounded, or killed by the OS.
- The HTTP/SSE pattern (browser → localhost server) is simple, well-understood, and fast over localhost.
- The same React frontend can be shared between the desktop (embedded in Tauri WebView) and standalone web use.

### Decision 8: Streaming End-to-End

**Android path:**
1. User sends a message → Kotlin ViewModel calls JNI `startGeneration()`.
2. JNI bridge calls `llama_decode()` in a loop on a background thread.
3. Each token is passed back to Kotlin via a callback registered through JNI.
4. The callback emits to a `Flow<String>`, which the Compose UI collects.
5. The UI appends each token chunk to the displayed message in real-time.

**Desktop/Web path:**
1. User sends a message → React sends `POST /v1/chat/completions` with `"stream": true`.
2. `llama-server` begins inference and returns an SSE stream (`text/event-stream`).
3. Each SSE event is a JSON chunk following OpenAI's format: `data: {"choices":[{"delta":{"content":"token"}}]}`.
4. The React client reads the `EventSource` or `fetch` with `ReadableStream`, appending each token to the UI.
5. The stream ends with `data: [DONE]`.

### Decision 9: Cancellation End-to-End

**Android:**
1. User taps "Stop" → ViewModel sets a `cancelled` flag (atomic boolean).
2. The JNI generation loop checks this flag before each `llama_decode()` call.
3. If cancelled, the loop exits, the partial response is saved, and the UI shows the message as incomplete.

**Desktop/Web:**
1. User clicks "Stop" → React sends a request to cancel (closes the SSE connection / sends abort signal).
2. For the HTTP approach: the client aborts the `fetch` request. `llama-server` detects the closed connection and stops generation.
3. The partial response is saved to the conversation.

### Decision 10: Model Switching

1. The user selects a different model in the Model Manager UI.
2. The current model is unloaded: `llama_free()` / server restart with new model.
3. Memory from the previous model is fully released before loading the new model.
4. The new model is validated (GGUF magic bytes, architecture support check).
5. The new model is loaded. Inference parameters are reset to the new model's defaults (context length, chat template).
6. The user can resume chatting. Existing conversations are preserved but the context window starts fresh.

**Desktop**: Model switching requires restarting `llama-server` with the new `--model` argument. The Rust backend handles this transparently.

**Android**: Model switching calls `llama_free()` and `llama_load_model()` via JNI on a background thread.

### Decision 11: Conversation Persistence

- Each conversation has a unique ID, title, creation timestamp, and last-modified timestamp.
- Messages are stored individually with role, content, timestamp, and sequence number.
- During streaming, partial content is flushed to the database periodically (e.g., every 500ms or every N tokens).
- On app crash, the last flushed state is preserved.
- Deleted messages are soft-deleted initially, then hard-deleted on next cleanup.
- The database uses SQLite (Room on Android, raw SQLite on desktop via rusqlite or better-sqlite3).

### Decision 12: Local-Only Privacy by Architecture

Privacy is enforced structurally, not by policy:

- There is no HTTP client that calls any external API during inference.
- The inference engine operates entirely in-process (Android) or over localhost (desktop/web).
- The database is local SQLite with no sync mechanism.
- There is no analytics SDK, no crash reporting SDK, no tracking code.
- Network permissions on Android are not required for core functionality (only for model downloads).
- The inference server binds to `127.0.0.1` — the OS kernel prevents external connections.

### Decision 13: LAN Access Isolation

- By default, `llama-server` binds to `127.0.0.1:<port>`. No external access is possible.
- LAN mode is an opt-in setting that changes the bind address to `0.0.0.0`.
- When LAN mode is enabled:
  - A warning dialog is shown explaining the implications.
  - Optional bearer-token authentication is available.
  - CORS headers are configured to allow requests from the local network.
  - The server's IP address and port are displayed for easy sharing.
- Disabling LAN mode immediately rebinds to `127.0.0.1`.
- The setting is stored in the local database and defaults to OFF on fresh installs.

### Decision 14: Future Multimodal Support

The architecture can accommodate multimodal models without redesign:

- The GGUF format supports multimodal model metadata.
- llama.cpp has experimental multimodal support (vision models).
- The chat message schema includes a `content` field that could be extended to support `[{"type": "text", "text": "..."}, {"type": "image_url", "image_url": {"url": "..."}}]` structured content.
- The JNI bridge and API endpoints can add new parameters for image input.
- The UI components can add image selection/capture capabilities.
- None of these require changing the core inference architecture — they are additive.

### Decision 15: Supporting Models Beyond Qwen3

The application is model-agnostic by design:

- No model-specific code exists in the UI, chat engine, or storage layers.
- Model-specific behavior (chat template, special tokens, parameter defaults) is read from GGUF metadata.
- The `llama-server` automatically applies the correct chat template.
- The JNI bridge reads the chat template from the model and formats prompts accordingly.
- Adding a new model family requires only downloading a GGUF file — no code changes.
- The model manager displays architecture information read from GGUF metadata.

---

## 3. Inference Core (Component A)

### 3.1 Engine

The inference core wraps llama.cpp, providing a clean interface for model loading, inference, and lifecycle management.

### 3.2 Capabilities

| Capability | Supported | Notes |
|-----------|-----------|-------|
| GGUF model loading | ✅ | Via `llama_load_model_from_file()` |
| Quantized models | ✅ | Q4_K_M, Q5_K_M, Q6_K, Q8_0, and others |
| Model unloading | ✅ | `llama_free()` releases all memory |
| Streaming generation | ✅ | Token-by-token callback |
| Cancellation | ✅ | Atomic flag checked per decode step |
| Temperature | ✅ | Sampling parameter |
| Top-p | ✅ | Nucleus sampling |
| Top-k | ✅ | Top-k sampling |
| Min-p | ✅ | Supported in llama.cpp sampling API |
| Repetition penalty | ✅ | `repeat_penalty` parameter |
| Presence penalty | ✅ | `presence_penalty` parameter |
| Frequency penalty | ✅ | `frequency_penalty` parameter |
| Context size | ✅ | Configurable `n_ctx` at model load |
| Thread count | ✅ | `n_threads` parameter |
| Batch size | ✅ | `n_batch` parameter |
| GPU layer offloading | ⚠️ | Where backend is available (`n_gpu_layers`) |
| CPU fallback | ✅ | Always available |
| Model metadata inspection | ✅ | Read from GGUF metadata fields |
| Chat template | ✅ | Read from `tokenizer.chat_template` in GGUF |
| Token counting | ✅ | `llama_tokenize()` for counting |
| Model health check | ✅ | Verify model loads and can process a small prompt |
| Inference statistics | ✅ | Timing data from `llama_perf_context()` |

### 3.2.1 Upstream llama.cpp API Alignment (Pinned Commit 931351ea5)

During implementation of the native core (`omnimind_core.cpp`), the upstream `llama.cpp` API was inspected directly from headers (`llama.h`, `gguf.h`, `ggml.h`). The following API adaptations were made to align with the current llama.cpp architecture:

1. **Model Loading Parameters (`llama_model_params`)**:
   - `use_mmap` and `use_mlock` booleans have evolved into `enum llama_load_mode load_mode` (`LLAMA_LOAD_MODE_MMAP`, `LLAMA_LOAD_MODE_MLOCK`, `LLAMA_LOAD_MODE_MMAP_MLOCK`).
   - Configured in `omnimind_core.cpp` via `mparams.load_mode = LLAMA_LOAD_MODE_MMAP;`.
2. **KV Cache Management**:
   - Context clearing is handled via `llama_memory_clear(llama_get_memory(ctx), true)` rather than legacy helpers.
3. **Sampling Chain Penalties**:
   - `llama_sampler_init_penalties` requires 5 arguments: `(n_vocab, penalty_last_n, penalty_repeat, penalty_freq, penalty_present)`.
4. **GGUF Tensor Dimensions**:
   - `gguf_get_tensor_ne(ctx, tensor_id)` returns `const int64_t* ne`, queried with 2 arguments.
5. **Chat Templates**:
   - Model chat templates are evaluated dynamically using `llama_chat_apply_template()` directly from the model's embedded `tokenizer.chat_template` GGUF key, ensuring zero hardcoded model formatting.

### 3.3 Backend Detection and Fallback

```
┌─────────────────────────────────┐
│      Application Startup        │
└──────────────┬──────────────────┘
               ▼
┌─────────────────────────────────┐
│  Initialize GGML backend        │
│  (llama_backend_init)           │
└──────────────┬──────────────────┘
               ▼
┌─────────────────────────────────┐
│  Enumerate available devices    │
│  - CPU (always available)       │
│  - OpenCL (check for driver)    │
│  - Vulkan (check for driver)    │
└──────────────┬──────────────────┘
               ▼
┌─────────────────────────────────┐
│  Report available backends      │
│  to UI / settings layer         │
└──────────────┬──────────────────┘
               ▼
┌─────────────────────────────────┐
│  User selects backend           │
│  (default: CPU)                 │
└──────────────┬──────────────────┘
               ▼
┌─────────────────────────────────┐
│  Load model with selected       │
│  backend config                 │
│  - n_gpu_layers = 0 for CPU     │
│  - n_gpu_layers > 0 for GPU     │
└──────────────┬──────────────────┘
               ▼
┌─────────────────────────────────┐
│  If GPU load fails →            │
│  Fallback to CPU (n_gpu=0)      │
│  Log warning                    │
└─────────────────────────────────┘
```

### 3.4 Android Native Build

The llama.cpp library is cross-compiled for ARM64 using the Android NDK:

```bash
cmake -B build-android \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-29 \
  -DANDROID_STL=c++_shared \
  -DBUILD_SHARED_LIBS=ON \
  -DGGML_NATIVE=OFF \
  -DLLAMA_BUILD_EXAMPLES=OFF \
  -DLLAMA_BUILD_TESTS=OFF \
  -DCMAKE_BUILD_TYPE=Release
```

Critical flags:
- `GGML_NATIVE=OFF` — required for cross-compilation (do not auto-detect host CPU features).
- `ANDROID_ABI=arm64-v8a` — target ARM64 only.
- `BUILD_SHARED_LIBS=ON` — produces `libllama.so` and `libggml.so` for JNI loading.
- `CMAKE_BUILD_TYPE=Release` — enable optimizations; debug builds are ~100x slower.

Optional acceleration (compile-time, runtime-detected):
- `-DGGML_OPENCL=ON` — requires OpenCL headers and ICD loader.
- `-DGGML_VULKAN=ON` — requires Vulkan SDK headers.

### 3.5 Desktop/Server Build

For desktop, llama-server is built for the host platform:

```bash
cmake -B build \
  -DCMAKE_BUILD_TYPE=Release \
  -DLLAMA_BUILD_SERVER=ON
cmake --build build --config Release --target llama-server
```

Optional backends:
- `-DGGML_VULKAN=ON` for Vulkan GPU support.
- `-DGGML_CUDA=ON` for NVIDIA GPU support.

### 3.6 Inference Interface (Abstract)

```
InferenceEngine
├── loadModel(path, params) → ModelHandle | Error
├── unloadModel(handle) → void
├── getModelInfo(handle) → ModelMetadata
├── startGeneration(handle, messages, settings, callback) → GenerationHandle
├── cancelGeneration(genHandle) → void
├── tokenize(handle, text) → TokenCount
├── getAvailableBackends() → List<BackendInfo>
├── getPerformanceStats(genHandle) → InferenceStats
└── healthCheck(handle) → HealthStatus

ModelMetadata
├── name: String
├── architecture: String
├── parameterCount: Long
├── contextLength: Int
├── quantization: String
├── chatTemplate: String?
├── vocabSize: Int
└── fileSize: Long

GenerationSettings
├── temperature: Float (default: 0.7)
├── topP: Float (default: 0.9)
├── topK: Int (default: 40)
├── minP: Float (default: 0.05)
├── repeatPenalty: Float (default: 1.1)
├── presencePenalty: Float (default: 0.0)
├── frequencyPenalty: Float (default: 0.0)
├── maxTokens: Int (default: 2048)
├── contextSize: Int (default: 4096)
├── threads: Int (default: 4)
├── batchSize: Int (default: 512)
├── gpuLayers: Int (default: 0)
└── seed: Int (default: -1, random)

InferenceStats
├── promptTokens: Int
├── generatedTokens: Int
├── promptTokensPerSec: Float
├── generationTokensPerSec: Float
├── timeToFirstTokenMs: Long
├── totalTimeMs: Long
└── peakMemoryBytes: Long?
```

---

## 4. Model Manager (Component B)

### 4.1 Responsibilities

The model manager handles all model lifecycle operations outside of inference.

### 4.2 Model Registry

Each model entry in the registry stores:

```
ModelEntry
├── id: UUID
├── name: String (user-editable)
├── fileName: String
├── filePath: String (platform-specific)
├── fileSize: Long
├── sha256: String? (computed or from source)
├── architecture: String (from GGUF)
├── parameterCount: Long (from GGUF)
├── quantization: String (from GGUF)
├── contextLength: Int (from GGUF)
├── author: String? (from GGUF metadata or user input)
├── license: String? (from GGUF metadata or user input)
├── downloadSource: String? (URL if downloaded)
├── importedAt: Timestamp
├── lastUsedAt: Timestamp?
├── isDefault: Boolean
└── status: ModelStatus (ready | validating | corrupted | downloading)
```

### 4.3 Operations

| Operation | Description | Constraints |
|-----------|-------------|-------------|
| **Import** | Copy or reference a local GGUF file | Validate GGUF magic bytes, check file size > 0 |
| **Download** | Fetch GGUF from Hugging Face URL | Free-space check, atomic download (temp file + rename), resume support |
| **Validate** | Verify file integrity and loadability | GGUF header check, optional SHA-256 verification |
| **Delete** | Remove model file and registry entry | Block if model is currently loaded |
| **Rename** | Change display name | Does not rename the file on disk |
| **Set default** | Mark one model as the default | Only one default at a time |
| **Load** | Load model into inference engine | Memory check, unload current model first |
| **Unload** | Release model from memory | Free native memory, update status |
| **Inspect** | Read and display GGUF metadata | Non-destructive |

### 4.4 Storage Separation

Model files (multi-GB) are stored separately from the application database:

- **Android**: Models are stored in the app's external files directory (`getExternalFilesDir(null)/models/`) or a user-chosen location via scoped storage. The SQLite database containing metadata is in the app's internal storage.
- **Desktop**: Models are stored in a configurable directory (default: `<app-data>/models/`). The SQLite database is in `<app-data>/data/`.

### 4.5 Download Flow

```
User requests download
         │
         ▼
┌─────────────────────────┐
│  Check free space        │
│  (file size + 10% buffer)│
└──────────┬──────────────┘
           ▼
┌─────────────────────────┐
│  Create temp file        │
│  (.gguf.part)            │
└──────────┬──────────────┘
           ▼
┌─────────────────────────┐
│  HTTP GET with Range     │
│  header (for resume)     │
│  Stream to temp file     │
│  Report progress to UI   │
└──────────┬──────────────┘
           ▼
┌─────────────────────────┐
│  Verify SHA-256          │
│  (if checksum known)     │
└──────────┬──────────────┘
           ▼
┌─────────────────────────┐
│  Rename .gguf.part →    │
│  .gguf (atomic)          │
└──────────┬──────────────┘
           ▼
┌─────────────────────────┐
│  Register in model DB    │
│  Extract GGUF metadata   │
└─────────────────────────┘
```

### 4.6 Free Space and Storage Warnings

| Condition | Action |
|-----------|--------|
| Free space < model file size | Block download/import with error |
| Free space < model size + 1 GB | Show warning: "Low storage" |
| Free space < 2 GB after import | Show warning: "Device storage critically low" |
| Model file is 0 bytes or truncated | Mark as corrupted, offer delete |

---

## 5. Chat Engine (Component C)

### 5.1 Conversation Model

```
Conversation
├── id: UUID
├── title: String (auto-generated from first message, user-editable)
├── createdAt: Timestamp
├── updatedAt: Timestamp
├── modelId: UUID? (model used, nullable if model deleted)
├── systemPrompt: String?
└── messages: List<Message>

Message
├── id: UUID
├── conversationId: UUID
├── role: MessageRole (system | user | assistant)
├── content: String
├── createdAt: Timestamp
├── sequenceNumber: Int
├── isPartial: Boolean (true during streaming)
├── tokenCount: Int? (if computed)
└── generationStats: InferenceStats? (for assistant messages)
```

### 5.2 Operations

| Operation | Description |
|-----------|-------------|
| **Create conversation** | New conversation with optional system prompt |
| **List conversations** | Sorted by `updatedAt` descending |
| **Rename conversation** | Update title |
| **Delete conversation** | Soft delete → hard delete on cleanup |
| **Send message** | Add user message, trigger generation |
| **Stream response** | Receive tokens, append to assistant message |
| **Stop generation** | Cancel mid-stream, save partial response |
| **Regenerate** | Delete last assistant message, re-generate |
| **Edit and resend** | Edit a user message, delete all subsequent messages, regenerate |
| **Clear context** | Start new conversation (does not delete old one) |

### 5.3 Streaming and Persistence

During streaming:

1. A new assistant `Message` is created with `isPartial = true`.
2. Tokens are appended to the in-memory message content.
3. Every ~500ms (or every 50 tokens, whichever comes first), the message content is flushed to the database.
4. When generation completes, `isPartial` is set to `false`, final stats are saved, and the content is flushed.
5. If the app crashes between flushes, the last flushed state is preserved.

### 5.4 Context Handling

- Messages are assembled into a prompt using the model's chat template.
- If the total token count exceeds the context window, older messages are truncated from the beginning (preserving the system prompt and recent messages).
- The user is notified when context truncation occurs.
- "New conversation" starts with a fresh context and no history.

### 5.5 Error Handling

| Error | Response |
|-------|----------|
| Model not loaded | Prompt user to select and load a model |
| Generation fails mid-stream | Save partial response, show error, allow retry |
| Out of memory during generation | Save partial, unload model, show OOM error |
| Context overflow | Truncate old messages, notify user |
| Corrupted conversation data | Skip corrupted messages, log error |

---

## 6. Storage (Component D)

### 6.1 Storage Categories

| Category | Storage Type | Location |
|----------|-------------|----------|
| **Model files** | Filesystem | Separate directory, multi-GB files |
| **Model metadata** | SQLite | App database |
| **Conversations** | SQLite | App database |
| **App settings** | SQLite / SharedPreferences | App database or Android preferences |
| **Inference settings** | SQLite | App database |
| **Logs** | Filesystem | App-private log directory |
| **Benchmarks** | SQLite | App database |

### 6.2 Database Schema (SQLite)

```sql
-- Model registry
CREATE TABLE models (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    file_name TEXT NOT NULL,
    file_path TEXT NOT NULL,
    file_size INTEGER NOT NULL,
    sha256 TEXT,
    architecture TEXT,
    parameter_count INTEGER,
    quantization TEXT,
    context_length INTEGER,
    author TEXT,
    license TEXT,
    download_source TEXT,
    imported_at INTEGER NOT NULL,
    last_used_at INTEGER,
    is_default INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'ready'
);

-- Conversations
CREATE TABLE conversations (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    model_id TEXT,
    system_prompt TEXT,
    is_deleted INTEGER NOT NULL DEFAULT 0
);

-- Messages
CREATE TABLE messages (
    id TEXT PRIMARY KEY,
    conversation_id TEXT NOT NULL,
    role TEXT NOT NULL,
    content TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    sequence_number INTEGER NOT NULL,
    is_partial INTEGER NOT NULL DEFAULT 0,
    token_count INTEGER,
    generation_stats_json TEXT,
    FOREIGN KEY (conversation_id) REFERENCES conversations(id)
);
CREATE INDEX idx_messages_conversation ON messages(conversation_id, sequence_number);

-- App settings
CREATE TABLE settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

-- Inference settings (per model or global)
CREATE TABLE inference_settings (
    id TEXT PRIMARY KEY,
    model_id TEXT,  -- NULL for global defaults
    temperature REAL NOT NULL DEFAULT 0.7,
    top_p REAL NOT NULL DEFAULT 0.9,
    top_k INTEGER NOT NULL DEFAULT 40,
    min_p REAL NOT NULL DEFAULT 0.05,
    repeat_penalty REAL NOT NULL DEFAULT 1.1,
    presence_penalty REAL NOT NULL DEFAULT 0.0,
    frequency_penalty REAL NOT NULL DEFAULT 0.0,
    max_tokens INTEGER NOT NULL DEFAULT 2048,
    context_size INTEGER NOT NULL DEFAULT 4096,
    threads INTEGER NOT NULL DEFAULT 4,
    batch_size INTEGER NOT NULL DEFAULT 512,
    gpu_layers INTEGER NOT NULL DEFAULT 0
);

-- Benchmark results
CREATE TABLE benchmarks (
    id TEXT PRIMARY KEY,
    model_id TEXT NOT NULL,
    quantization TEXT NOT NULL,
    context_size INTEGER NOT NULL,
    threads INTEGER NOT NULL,
    backend TEXT NOT NULL,
    gpu_layers INTEGER NOT NULL DEFAULT 0,
    prompt_tokens_per_sec REAL,
    generation_tokens_per_sec REAL,
    time_to_first_token_ms INTEGER,
    peak_memory_bytes INTEGER,
    device_info TEXT,
    thermal_state TEXT,
    battery_level INTEGER,
    timestamp INTEGER NOT NULL,
    notes TEXT
);

-- Logs
CREATE TABLE logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp INTEGER NOT NULL,
    level TEXT NOT NULL,
    tag TEXT NOT NULL,
    message TEXT NOT NULL
);
CREATE INDEX idx_logs_timestamp ON logs(timestamp);
```

### 6.3 Platform-Specific Database Choices

**Android:**
- **Room** (Jetpack persistence library) wrapping SQLite.
- Room provides compile-time SQL verification, type-safe DAOs, and Flow/coroutine integration.
- Database file: `<internal-storage>/databases/omnimind.db`.

**Desktop (Tauri/Rust):**
- **rusqlite** or Tauri's SQLite plugin for the Rust backend.
- The Rust backend owns the database and exposes data via Tauri commands.
- Database file: `<app-data-dir>/data/omnimind.db`.

**Web:**
- The web frontend does not have its own database.
- All persistent data is managed by the local inference host's SQLite database.
- Browser `localStorage` stores only UI preferences (theme, last-used settings) — non-critical data.
- If the inference host is unavailable, the web UI shows a connection error; it does not try to cache conversations in the browser.

### 6.4 Model File Storage

Model files are stored outside the database directory to avoid bloating backups and to allow independent management:

- **Android**: `getExternalFilesDir(null)/models/` — no special permissions needed, survives app updates, deleted on uninstall (with warning).
- **Desktop**: `<app-data-dir>/models/` — configurable in settings.

---

## 7. Android Architecture (Component E)

### 7.1 Architecture Layers

```
┌──────────────────────────────────────────┐
│                UI Layer                   │
│  (Jetpack Compose screens + ViewModels)   │
├──────────────────────────────────────────┤
│              Domain Layer                 │
│  (Use cases, business logic)              │
├──────────────────────────────────────────┤
│               Data Layer                  │
│  (Repositories, Room DAOs, Preferences)   │
├──────────────────────────────────────────┤
│             Native Layer                  │
│  (JNI bridge → llama.cpp)                │
└──────────────────────────────────────────┘
```

### 7.2 Screens

| Screen | Purpose |
|--------|---------|
| **Chat** | Main conversation interface with streaming output |
| **Conversation list** | Browse, search, delete conversations |
| **Model picker** | Select, load, unload models |
| **Model manager** | Import, download, delete, inspect models |
| **Settings** | Inference parameters, app preferences, storage management |
| **Performance/Benchmark** | Run benchmarks, view results, compare backends |
| **About/Licenses** | App version, open-source licenses, third-party credits |

### 7.3 Key Components

**ViewModels** (one per major screen):
- `ChatViewModel` — manages current conversation, streaming, generation control.
- `ModelManagerViewModel` — manages model list, import, download, validation.
- `SettingsViewModel` — manages inference and app settings.
- `BenchmarkViewModel` — manages benchmark runs and results.

**Repositories** (data layer):
- `ConversationRepository` — CRUD for conversations and messages (Room).
- `ModelRepository` — CRUD for model registry, file operations.
- `SettingsRepository` — Read/write app and inference settings.
- `BenchmarkRepository` — Store and query benchmark results.

**Use Cases** (domain layer):
- `SendMessageUseCase` — orchestrates sending a message and starting generation.
- `LoadModelUseCase` — validates and loads a model, manages memory.
- `ImportModelUseCase` — copies/references a file, extracts metadata.
- `DownloadModelUseCase` — manages download lifecycle.
- `RunBenchmarkUseCase` — executes a benchmark scenario.

### 7.4 JNI Bridge

The JNI bridge is a Kotlin class backed by native C/C++ code:

```kotlin
// Kotlin-side interface
object LlamaEngine {
    init {
        System.loadLibrary("omnimind_jni")  // loads libonnimind_jni.so
    }

    // Model lifecycle
    external fun loadModel(path: String, params: ModelParams): Long  // returns handle
    external fun unloadModel(handle: Long)
    external fun getModelInfo(handle: Long): ModelInfo

    // Generation
    external fun startGeneration(
        handle: Long,
        prompt: String,
        settings: GenerationSettings,
        callback: GenerationCallback
    ): Long  // returns generation handle

    external fun cancelGeneration(genHandle: Long)

    // Utilities
    external fun getAvailableBackends(): Array<BackendInfo>
    external fun tokenize(handle: Long, text: String): Int
    external fun getPerformanceStats(genHandle: Long): InferenceStats
}

interface GenerationCallback {
    fun onToken(token: String)
    fun onComplete(stats: InferenceStats)
    fun onError(error: String)
}
```

The native C++ side:
- Links against `libllama.so` and `libggml.so`.
- Implements JNI functions that call llama.cpp API.
- Manages model pointers in a thread-safe map.
- Runs inference on a dedicated thread (not the JNI caller's thread).
- Calls back into Java/Kotlin via the `GenerationCallback` interface using `JNIEnv`.

### 7.5 Threading Model

```
Main Thread (UI)
    │
    ├── Compose UI rendering
    ├── User input handling
    └── StateFlow collection
    
ViewModel Scope (Dispatchers.Default)
    │
    ├── Business logic
    ├── Database operations (via Room)
    └── Flow transformations

Inference Thread (native, single-threaded for model ops)
    │
    ├── llama_load_model()
    ├── llama_decode() loop
    └── Token callbacks → Flow emission

IO Dispatcher (Dispatchers.IO)
    │
    ├── File operations
    ├── Downloads
    └── Model imports
```

### 7.6 Memory Lifecycle

- The native model is loaded via JNI and lives in native (C++) heap memory, NOT in the Java/Kotlin heap.
- `llama_load_model_from_file()` memory-maps the GGUF file, keeping Java heap overhead minimal.
- When the model is unloaded (`llama_free()`), all native memory is released.
- The Kotlin layer holds only a `Long` handle (pointer) — no large byte arrays.
- If the Android OS kills the process while the model is loaded, native memory is reclaimed by the OS.
- On Activity recreation (config change), the model handle is preserved in the ViewModel (which survives config changes).

### 7.7 Scoped Storage

- Model import uses `ActivityResultContracts.OpenDocument` with MIME type filter.
- The returned URI is opened via `ContentResolver.openInputStream()`.
- The file is copied to the app's external files directory (which does not require `MANAGE_EXTERNAL_STORAGE`).
- The original URI is not persisted (scoped storage URIs can expire).

---

## 8. Desktop Architecture (Component F)

### 8.1 Technology Stack

| Component | Technology |
|-----------|-----------|
| **App shell** | Tauri 2 (Rust backend + WebView frontend) |
| **Frontend** | React + TypeScript + Vite |
| **Inference** | llama-server (bundled as sidecar binary) |
| **Database** | SQLite via rusqlite (Rust backend) |
| **IPC** | Tauri commands (frontend ↔ Rust) |
| **Inference API** | HTTP/SSE (Rust ↔ llama-server over localhost) |

### 8.2 Architecture

```
┌────────────────────────────────────────────┐
│              Tauri Application              │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │         React + TypeScript          │    │
│  │         (WebView frontend)          │    │
│  │                                     │    │
│  │  ┌───────────┐  ┌──────────────┐    │    │
│  │  │ Chat UI   │  │ Model Mgr UI│    │    │
│  │  └─────┬─────┘  └──────┬──────┘    │    │
│  │        │               │            │    │
│  │        └───────┬───────┘            │    │
│  │                │                    │    │
│  │     Tauri invoke() commands         │    │
│  └────────────────┬────────────────────┘    │
│                   │                          │
│  ┌────────────────┴────────────────────┐    │
│  │         Tauri Rust Backend           │    │
│  │                                      │    │
│  │  ┌──────────────┐ ┌──────────────┐   │    │
│  │  │ Server Mgr   │ │ SQLite DB    │   │    │
│  │  │ (process     │ │ (rusqlite)   │   │    │
│  │  │  lifecycle)  │ │              │   │    │
│  │  └──────┬───────┘ └──────────────┘   │    │
│  │         │                             │    │
│  └─────────┼─────────────────────────────┘    │
│            │                                   │
│  ┌─────────┴─────────────────────────────┐    │
│  │         llama-server (sidecar)         │    │
│  │  - Bound to 127.0.0.1:<dynamic-port>  │    │
│  │  - Managed lifecycle                   │    │
│  │  - OpenAI-compatible API               │    │
│  └────────────────────────────────────────┘    │
└────────────────────────────────────────────────┘
```

### 8.3 llama-server Management

The Tauri Rust backend manages the llama-server process:

1. **Port allocation**: Find an available port by binding a `TcpListener` to port 0, reading the assigned port, then closing the listener before spawning the server.
2. **Spawn**: Use Tauri's shell plugin to spawn llama-server as a sidecar with arguments:
   ```
   llama-server --host 127.0.0.1 --port <dynamic-port> --model <path> --ctx-size <n> --threads <n>
   ```
3. **Health check**: Poll `GET /health` until the server reports ready.
4. **Monitor**: Watch the process for unexpected exit. If it crashes, show an error to the user with retry option.
5. **Model switch**: To load a different model, kill the current server process and spawn a new one with the new model path.
6. **Shutdown**: On app close, send SIGTERM (or TerminateProcess on Windows) to the server process. Clean up on Tauri's `on_window_event` close handler.

### 8.4 Sidecar Binary Bundling

The llama-server binary is bundled with the Tauri application using `externalBin`:

```json
// tauri.conf.json
{
  "bundle": {
    "externalBin": [
      "binaries/llama-server"
    ]
  }
}
```

Platform-specific binaries must be named with the target triple suffix:
- `llama-server-x86_64-pc-windows-msvc.exe`
- `llama-server-x86_64-unknown-linux-gnu`
- `llama-server-aarch64-unknown-linux-gnu` (for ARM64 Linux)

### 8.5 Tauri Permissions

```json
// capabilities/default.json
{
  "permissions": [
    "core:default",
    {
      "identifier": "shell:allow-spawn",
      "allow": [{ "name": "binaries/llama-server", "sidecar": true }]
    },
    "dialog:default",
    "fs:default"
  ]
}
```

### 8.6 Supported Platforms

| Platform | First Release | Notes |
|----------|:---:|-------|
| **Windows** (x86-64) | ✅ | Primary target |
| **Linux** (x86-64) | ✅ | Primary target |
| **Linux** (ARM64) | ⚠️ | Best-effort |
| **macOS** (ARM64) | 🔮 | Architecture supports it; not tested for v1 |

---

## 9. Web Interface (Component G)

### 9.1 Architecture

The web interface is a standalone React SPA that communicates with a local inference host (llama-server) over HTTP and SSE.

```
┌──────────────────────────┐
│    Browser (React SPA)    │
│                           │
│  ┌─────────┐ ┌────────┐  │
│  │Chat View│ │Settings│  │
│  └────┬────┘ └───┬────┘  │
│       │          │        │
│  HTTP/SSE to localhost    │
└───────┬──────────┘────────┘
        │
        ▼
┌──────────────────────────┐
│  Local Inference Host     │
│  (llama-server)           │
│  127.0.0.1:<port>         │
└──────────────────────────┘
```

### 9.2 Connection

- The user configures the server URL (default: `http://localhost:8080`).
- The SPA verifies connectivity via `GET /health`.
- If the connection fails, the UI shows a connection error with instructions.

### 9.3 Code Sharing

The web interface and the desktop frontend share the same React/TypeScript codebase:

```
shared/
├── api/           # HTTP client, API types
├── components/    # Reusable UI components
├── hooks/         # Custom React hooks
├── types/         # TypeScript type definitions
└── utils/         # Shared utilities

web/               # Web-specific entry point, routing, config
desktop/src/       # Desktop-specific entry point, Tauri integration
```

The key difference:
- **Desktop**: API calls go through Tauri commands (which proxy to llama-server). Tauri commands also provide access to the local database and file system.
- **Web**: API calls go directly to the llama-server HTTP endpoints. Conversation persistence relies on the server.

### 9.4 CORS

When the web SPA and llama-server are on the same host (`localhost`), CORS is not an issue.

When the web SPA is served from a different origin (e.g., accessing a LAN server):
- llama-server supports the `--cors` flag to allow cross-origin requests.
- OmniMind's server startup scripts enable CORS when LAN mode is active.
- CORS is restricted to the local network's IP range when possible.

### 9.5 Responsive Design

The web UI is responsive and works on:
- Desktop browsers (Chrome, Firefox, Edge, Safari)
- Tablet browsers
- Mobile browsers (as a fallback; the native Android app is preferred)

---

## 10. API Design (Component H)

### 10.1 API Layers

```
┌─────────────────────────────────────────┐
│         OmniMind Internal API            │
│  (Application-level abstractions)        │
│                                          │
│  Used by: Android UI, Desktop UI, Web UI │
├─────────────────────────────────────────┤
│         llama.cpp Server API             │
│  (OpenAI-compatible endpoints)           │
│                                          │
│  Provided by: llama-server               │
│  Used by: OmniMind backend/proxy         │
├─────────────────────────────────────────┤
│      Optional Compatibility API          │
│  (Subset of OpenAI API format)           │
│                                          │
│  Allows third-party tools to connect     │
└─────────────────────────────────────────┘
```

### 10.2 Internal API Endpoints

These are the endpoints that the OmniMind UI consumes. On Android, these are Kotlin function calls. On Desktop, these are Tauri commands. On Web, these are HTTP endpoints on the local server.

#### Health

```
GET /api/health
→ 200 { "status": "ok", "model_loaded": true, "model_name": "Qwen3-4B-Q4_K_M" }
→ 503 { "status": "error", "message": "Model not loaded" }
```

#### Models

```
GET /api/models
→ 200 {
    "models": [
      {
        "id": "uuid",
        "name": "Qwen3-4B Q4_K_M",
        "file_name": "qwen3-4b-q4_k_m.gguf",
        "file_size": 2684354560,
        "architecture": "qwen3",
        "parameter_count": 4000000000,
        "quantization": "Q4_K_M",
        "context_length": 32768,
        "status": "ready",
        "is_loaded": false,
        "is_default": true
      }
    ]
  }

GET /api/models/:id
→ 200 { ...full model details... }

POST /api/models/load
← { "model_id": "uuid" }
→ 200 { "status": "loaded", "model_id": "uuid" }
→ 400 { "error": "model_not_found" }
→ 500 { "error": "load_failed", "message": "Insufficient memory" }

POST /api/models/unload
→ 200 { "status": "unloaded" }
```

#### Chat Completion

```
POST /api/chat/completions
← {
    "conversation_id": "uuid",        // optional, creates new if omitted
    "messages": [
      { "role": "system", "content": "You are helpful." },
      { "role": "user", "content": "Hello!" }
    ],
    "stream": true,
    "settings": {                       // optional, uses defaults if omitted
      "temperature": 0.7,
      "top_p": 0.9,
      "top_k": 40,
      "min_p": 0.05,
      "max_tokens": 2048,
      "repeat_penalty": 1.1,
      "presence_penalty": 0.0,
      "frequency_penalty": 0.0
    }
  }

# Non-streaming response
→ 200 {
    "id": "gen-uuid",
    "conversation_id": "uuid",
    "choices": [{
      "message": { "role": "assistant", "content": "Hello! How can I help?" },
      "finish_reason": "stop"
    }],
    "usage": {
      "prompt_tokens": 15,
      "completion_tokens": 8,
      "total_tokens": 23
    },
    "stats": {
      "prompt_tokens_per_sec": 125.3,
      "generation_tokens_per_sec": 18.7,
      "time_to_first_token_ms": 245
    }
  }

# Streaming response (SSE)
→ 200 Content-Type: text/event-stream

data: {"choices":[{"delta":{"role":"assistant","content":""},"index":0}]}

data: {"choices":[{"delta":{"content":"Hello"},"index":0}]}

data: {"choices":[{"delta":{"content":"!"},"index":0}]}

data: {"choices":[{"delta":{"content":" How"},"index":0}]}

data: [DONE]
```

#### Cancel Generation

```
POST /api/chat/cancel
← { "generation_id": "gen-uuid" }
→ 200 { "status": "cancelled" }
```

#### Conversations

```
GET /api/conversations
→ 200 {
    "conversations": [
      { "id": "uuid", "title": "...", "updated_at": 1696000000, "message_count": 12 }
    ]
  }

GET /api/conversations/:id
→ 200 { ...conversation with all messages... }

DELETE /api/conversations/:id
→ 200 { "status": "deleted" }

PATCH /api/conversations/:id
← { "title": "New Title" }
→ 200 { "status": "updated" }
```

#### Settings

```
GET /api/settings
→ 200 { "inference": { ... }, "app": { ... } }

PATCH /api/settings
← { "inference": { "temperature": 0.8 } }
→ 200 { "status": "updated" }
```

### 10.3 Error Format

All errors follow a consistent format:

```json
{
  "error": "error_code",
  "message": "Human-readable description",
  "details": {}
}
```

Error codes:

| Code | HTTP Status | Description |
|------|:-----------:|-------------|
| `model_not_found` | 404 | Requested model ID not in registry |
| `model_not_loaded` | 400 | No model loaded for inference |
| `load_failed` | 500 | Model failed to load |
| `generation_failed` | 500 | Inference error during generation |
| `out_of_memory` | 500 | Insufficient memory |
| `invalid_request` | 400 | Malformed request body |
| `server_unavailable` | 503 | Inference server not running |
| `cancelled` | 499 | Generation was cancelled |

### 10.4 Streaming Protocol

OmniMind uses Server-Sent Events (SSE) for streaming, following the OpenAI streaming format:

1. The client sends `POST /api/chat/completions` with `"stream": true`.
2. The server responds with `Content-Type: text/event-stream`.
3. Each token is sent as an SSE event: `data: {JSON}\n\n`.
4. The stream ends with `data: [DONE]\n\n`.
5. If the client disconnects (closes the connection), the server detects this and stops generation.

### 10.5 Relationship to llama-server API

OmniMind's internal API is a superset of what llama-server provides:

| OmniMind API | Backed by |
|-------------|-----------|
| `/api/health` | `GET /health` on llama-server |
| `/api/models` | Local database + `GET /v1/models` |
| `/api/models/load` | Restart llama-server with new model |
| `/api/chat/completions` | `POST /v1/chat/completions` on llama-server |
| `/api/conversations` | Local SQLite database |
| `/api/settings` | Local SQLite database |

The OmniMind backend (Tauri Rust or a thin wrapper for web) acts as a proxy/orchestrator between the UI and llama-server.

---

## 11. Security (Component I)

### 11.1 Principles

1. **No telemetry** — no analytics, no crash reporting, no usage tracking.
2. **No cloud** — no remote API calls during inference or normal operation.
3. **Localhost by default** — inference server binds to `127.0.0.1`.
4. **Defense in depth** — validate inputs at every layer.

### 11.2 Threat Model

| Threat | Mitigation |
|--------|-----------|
| Remote access to inference server | Bind to 127.0.0.1; LAN mode requires explicit opt-in |
| Path traversal via model paths | Validate all file paths against allowed directories |
| Command injection via model names | Sanitize model names; never pass to shell |
| Malicious GGUF file | Validate GGUF header; llama.cpp validates format on load |
| Unauthorized LAN access | Optional bearer-token authentication in LAN mode |
| XSS via model output | React auto-escapes rendered content; no `dangerouslySetInnerHTML` without sanitization |
| Denial of service (local) | Resource limits on context size, max tokens |
| Process escape from llama-server | llama-server runs with minimal permissions; no shell access exposed |

### 11.3 LAN Mode Security

When LAN mode is enabled:

1. A warning dialog explains the risks: "Other devices on your network will be able to send prompts to your model."
2. The user must explicitly confirm.
3. A random bearer token is generated and displayed.
4. Requests from non-localhost must include `Authorization: Bearer <token>`.
5. The token is stored locally and can be regenerated.
6. CORS is configured to allow requests from the LAN.

### 11.4 Input Validation

| Input | Validation |
|-------|-----------|
| Model file path | Must exist, must end in `.gguf`, must be within allowed directories |
| Model name (user-entered) | Strip control characters, limit length to 255 |
| System prompt | Limit length to 10,000 characters |
| User message | Limit length to 100,000 characters |
| Inference parameters | Range-checked (e.g., temperature 0.0–2.0, top_k 1–500) |
| Server port | Must be 1024–65535 |
| Download URL | Must be HTTPS, must match allowed domains (Hugging Face by default) |

---

## 12. Performance (Component J)

### 12.1 Target Device Profile

**Motorola Edge 60 Stylus:**

| Property | Value |
|----------|-------|
| SoC | Qualcomm Snapdragon 7s Gen 2 |
| CPU | 4× Cortex-A78 @ 2.4 GHz + 4× Cortex-A55 @ 1.8 GHz |
| GPU | Adreno 710 |
| RAM | 8 GB |
| Storage | 256 GB (UFS) |
| OS | Android 15 |

### 12.2 Memory Budget

```
Total RAM:                         8,192 MB
Android OS + system services:     ~2,500 MB
Background apps (minimal):         ~500 MB
OmniMind app (Kotlin/JVM):         ~150 MB
──────────────────────────────────────────
Available for model + KV cache:   ~5,000 MB

Qwen3-4B Q4_K_M model:            ~2,500 MB
KV cache (4096 ctx):               ~300 MB
Scratch buffers:                   ~200 MB
──────────────────────────────────────────
Remaining headroom:               ~2,000 MB
```

This budget shows Qwen3-4B Q4_K_M at 4096 context fits comfortably. Increasing context to 8192+ or using Q8_0 (~4.5 GB) significantly reduces headroom.

### 12.3 Benchmark Subsystem

The benchmark subsystem records:

| Metric | How Measured |
|--------|-------------|
| Model | From model registry |
| Quantization | From GGUF metadata |
| Context size | Configuration parameter |
| Thread count | Configuration parameter |
| Backend | CPU / OpenCL / Vulkan |
| GPU layers | Configuration parameter |
| Prompt tokens/sec | From `llama_perf_context()` timings |
| Generation tokens/sec | From `llama_perf_context()` timings |
| Time to first token (ms) | Wall clock: request start → first token callback |
| Peak memory (bytes) | Platform-specific: `Debug.getNativeHeapAllocatedSize()` on Android |
| Thermal state | Android: `PowerManager.getThermalHeadroom()` if available |
| Battery level | Android: `BatteryManager` |
| Device info | Model, SoC, OS version |
| Timestamp | When benchmark was run |

### 12.4 Benchmark Protocol

A benchmark run:

1. Loads the selected model with the specified configuration.
2. Runs a standardized prompt (e.g., "Write a short story about a robot learning to paint.").
3. Generates a fixed number of tokens (e.g., 256).
4. Records all metrics.
5. Unloads the model.
6. Allows comparing runs with different backends, thread counts, or context sizes.

### 12.5 Performance Considerations

- **Thread count**: The Cortex-A78 big cores (4 cores) should be used for inference. Recommended default: 4 threads.
- **Batch size**: Default 512. Larger batches improve prompt processing speed but use more memory.
- **Memory mapping**: GGUF files are memory-mapped by llama.cpp, reducing load time and avoiding double-buffering.
- **Thermal throttling**: Sustained inference will cause the SoC to throttle. The benchmark should note thermal state.
- **Battery**: Full CPU inference is power-intensive. Warn users about battery consumption.

---

## 13. Model Selection Strategy (Component K)

### 13.1 Why Qwen3-4B Q4_K_M

For the target device (8 GB RAM, Snapdragon 7s Gen 2):

1. **Fits in memory**: ~2.5 GB model + ~300 MB KV cache (4K ctx) + ~150 MB app ≈ 3 GB total, leaving 5 GB for Android.
2. **Quality/size ratio**: 4B parameters provide useful conversational and reasoning capability. Q4_K_M retains most quality compared to the full-precision model.
3. **Speed**: 4B models generate tokens meaningfully fast on ARM64 mid-range SoCs.
4. **License**: Apache-2.0 — no restrictions.
5. **GQA architecture**: 8 KV heads vs 32 Q heads means the KV cache is smaller than a model with full multi-head attention.

### 13.2 Compatibility Matrix

| Category | Example Models | Q4_K_M Size | 8 GB Device | 16 GB Desktop |
|----------|---------------|-------------|:-----------:|:--------------:|
| **Very small** (≤1B) | Qwen3-0.6B, SmolLM-1B | <1 GB | ✅ | ✅ |
| **Small** (1–3B) | Qwen3-1.7B, Phi-3-mini | 1–2 GB | ✅ | ✅ |
| **Medium** (3–4B) | Qwen3-4B, Phi-3-small | 2–3 GB | ✅ (reference) | ✅ |
| **Large** (7–8B) | Qwen3-8B, Llama-3-8B | 4–5 GB | ⚠️ Small ctx only | ✅ |
| **Very large** (13B+) | Llama-3-13B | 7+ GB | ❌ | ⚠️ |

Notes:
- Actual usability depends on quantization, context size, KV cache memory, Android memory pressure, and backend.
- The app does not hard-code maximum model size. It checks available memory and warns the user.

### 13.3 Quantization Trade-offs

| Quantization | Relative Size | Quality | Speed |
|-------------|:------------:|:-------:|:-----:|
| Q4_K_M | 1.0× (baseline) | Good | Fast |
| Q5_K_M | ~1.2× | Better | Slightly slower |
| Q6_K | ~1.4× | Very good | Slower |
| Q8_0 | ~1.8× | Near-original | Slowest |

---

## 14. Observability (Component L)

### 14.1 Logging

- **Structured logging** with levels: DEBUG, INFO, WARN, ERROR.
- **Tags** for subsystems: `inference`, `model`, `chat`, `storage`, `network`, `ui`.
- **No conversation content** logged by default. A debug toggle can enable content logging for troubleshooting.
- **Log storage**: SQLite `logs` table or rotating log files.
- **Log rotation**: Keep last 7 days or 10 MB, whichever comes first.

### 14.2 User-Facing Features

| Feature | Description |
|---------|-------------|
| **View logs** | Scrollable log viewer in the app (Settings → Diagnostics) |
| **Clear logs** | Delete all stored logs |
| **Export diagnostics** | Export logs + device info + benchmark results as a zip file |
| **Benchmark reports** | View and export benchmark comparison tables |

### 14.3 What Is Logged

| Event | Level | Content |
|-------|-------|---------|
| Model loaded/unloaded | INFO | Model name, load time, memory |
| Generation started/completed | INFO | Token count, speed, duration (no content) |
| Generation cancelled | INFO | Partial token count |
| Error | ERROR | Error type, message, stack trace |
| Backend detection | INFO | Available backends, selected backend |
| Download progress | DEBUG | URL, bytes downloaded, progress % |
| Settings changed | INFO | Changed setting key (not value for sensitive settings) |

Logs are **never uploaded**. They are stored locally and can only be exported by explicit user action.

---

## 15. Failure Recovery (Component M)

### 15.1 Failure Scenarios

| Failure | Detection | Recovery |
|---------|-----------|----------|
| **Model load failure** | `llama_load_model()` returns null | Show error with details. Suggest checking file integrity, available memory, or trying a smaller model. |
| **Out of memory** | Native OOM signal or `llama_decode()` failure | Save partial conversation. Unload model. Show OOM error. Suggest reducing context size or using a smaller model. |
| **Unsupported architecture** | GGUF metadata indicates unknown architecture | Show error: "This model architecture is not supported by the current version of OmniMind." |
| **Corrupted GGUF** | Invalid magic bytes or `llama_load_model()` failure | Mark model as corrupted in registry. Offer re-download or delete. |
| **Insufficient storage** | Free-space check before download/import | Block operation. Show available vs. required space. |
| **Inference crash** | JNI exception or process exit | Save partial conversation. Show error. Allow retry or model change. |
| **llama-server crash** | Process monitoring (Tauri Rust backend) | Show error. Offer restart. If repeated, suggest changing settings. |
| **Network disconnect** (web UI ↔ server) | `fetch` error or SSE disconnection | Show connection lost banner. Auto-retry with exponential backoff. Save any partial response. |
| **App backgrounding** (Android) | `onStop()` / `onTrimMemory()` | Pause generation. Save partial conversation. Resume on return if model is still loaded. |
| **Android process recreation** | `ViewModel` + `SavedStateHandle` | Conversation state is in the database. Model must be reloaded (handle is invalid after process death). Show "Model was unloaded" message. |

### 15.2 Crash Resilience

- Conversations are persisted incrementally (not only on generation complete).
- The database uses WAL (Write-Ahead Logging) mode for crash resistance.
- Model registry state is always consistent — download uses temp file + atomic rename.
- Settings are written transactionally.

---

## 16. Testing Strategy (Component N)

### 16.1 Test Categories

| Category | Scope | Tools |
|----------|-------|-------|
| **Unit tests** | Individual functions, utilities, data transformations | JUnit (Android), Vitest (React), Rust `#[test]` |
| **Integration tests** | Repository + database, API + server, JNI bridge | AndroidX Test, Vitest + MSW, Rust integration tests |
| **Android instrumentation** | UI interactions, end-to-end flows | Compose Test, Espresso |
| **Desktop integration** | Server lifecycle, Tauri commands | Tauri test utilities, Rust tests |
| **API tests** | HTTP endpoint contract tests | Vitest + fetch, curl scripts |

### 16.2 Test Plan

#### Model Metadata Tests
- Parse GGUF metadata from a known model file.
- Handle missing optional metadata fields.
- Handle corrupted GGUF header.

#### Model Validation Tests
- Valid GGUF file is accepted.
- Non-GGUF file is rejected (wrong magic bytes).
- Truncated file is detected.
- Zero-byte file is rejected.

#### API Tests
- Health endpoint returns correct status.
- Model list returns registered models.
- Chat completion returns valid response.
- Streaming returns correctly formatted SSE events.
- Invalid requests return appropriate error codes.
- Missing model returns 400/404.

#### Streaming Tests
- Tokens are delivered in order.
- Partial responses are accumulated correctly.
- Stream ends with `[DONE]`.
- Empty responses are handled.

#### Cancellation Tests
- Cancel mid-stream stops generation.
- Partial response is preserved.
- UI updates to show incomplete message.
- Subsequent generation works after cancellation.

#### Chat Persistence Tests
- Conversation is created and retrievable.
- Messages are stored in correct order.
- Partial messages are saved during streaming.
- Conversation survives app restart.
- Deleted conversation is not returned in list.

#### Malformed Request Tests
- Missing required fields return 400.
- Invalid parameter ranges return 400.
- Extremely long messages are handled.
- Unicode and special characters work correctly.

#### Process Restart Tests (Desktop)
- llama-server can be started and stopped.
- App recovers from server crash.
- Model switch restarts server correctly.
- Port conflict is handled.

#### Android JNI Tests
- Model loads and unloads without memory leak.
- Generation produces tokens.
- Cancel stops generation.
- Multiple load/unload cycles work.
- Invalid model path returns error (no crash).

#### UI State Tests
- Chat screen updates during streaming.
- Model picker shows correct loaded state.
- Settings changes persist.
- Error dialogs display on failure.

#### Storage Failure Tests
- Database handles full-disk gracefully.
- Model import with insufficient space fails with clear error.
- Download with network loss preserves partial file.

#### Low-Memory Tests
- Loading a too-large model produces error, not crash.
- Android `onTrimMemory()` does not corrupt state.
- Model unload releases native memory.

---

## 17. Repository Layout (Component O)

```
OmniMind/
├── README.md                    # Project README
├── DESIGN.md                    # This document
├── IMPLEMENTATION.md            # Phased implementation plan
├── LICENSE                      # Apache-2.0 (application code)
├── .gitignore                   # Global gitignore
├── .gitmodules                  # Submodule references (llama.cpp)
│
├── android/                     # Android application
│   ├── app/
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   ├── java/com/omnimind/
│   │   │   │   │   ├── ui/             # Compose screens + ViewModels
│   │   │   │   │   ├── domain/          # Use cases
│   │   │   │   │   ├── data/            # Repositories, DAOs
│   │   │   │   │   ├── native/          # JNI bridge (Kotlin side)
│   │   │   │   │   └── di/              # Dependency injection
│   │   │   │   ├── jniLibs/
│   │   │   │   │   └── arm64-v8a/       # libllama.so, libggml.so, libomnimind_jni.so
│   │   │   │   ├── cpp/                 # JNI C++ source
│   │   │   │   │   ├── CMakeLists.txt
│   │   │   │   │   └── omnimind_jni.cpp
│   │   │   │   └── AndroidManifest.xml
│   │   │   ├── test/                    # Unit tests
│   │   │   └── androidTest/             # Instrumentation tests
│   │   └── build.gradle.kts
│   ├── build.gradle.kts                 # Root Android build
│   ├── settings.gradle.kts
│   └── gradle.properties
│
├── desktop/                     # Tauri desktop application
│   ├── src/                     # React frontend source
│   │   ├── App.tsx
│   │   ├── main.tsx
│   │   ├── components/          # Desktop-specific components
│   │   ├── hooks/               # Desktop-specific hooks (Tauri commands)
│   │   └── pages/               # Desktop-specific pages
│   ├── src-tauri/               # Tauri Rust backend
│   │   ├── src/
│   │   │   ├── main.rs
│   │   │   ├── commands/        # Tauri command handlers
│   │   │   ├── server/          # llama-server process management
│   │   │   └── db/              # SQLite database access
│   │   ├── binaries/            # llama-server sidecar binaries
│   │   ├── capabilities/        # Tauri permission configs
│   │   ├── Cargo.toml
│   │   └── tauri.conf.json
│   ├── package.json
│   ├── tsconfig.json
│   └── vite.config.ts
│
├── web/                         # Standalone web interface
│   ├── src/
│   │   ├── App.tsx
│   │   ├── main.tsx
│   │   ├── components/          # Web-specific components
│   │   └── pages/               # Web-specific pages
│   ├── package.json
│   ├── tsconfig.json
│   └── vite.config.ts
│
├── shared/                      # Shared React/TypeScript code
│   ├── api/                     # API client, types, HTTP utilities
│   ├── components/              # Shared UI components
│   ├── hooks/                   # Shared custom hooks
│   ├── types/                   # TypeScript type definitions
│   ├── utils/                   # Shared utilities
│   ├── package.json
│   └── tsconfig.json
│
├── native/                      # llama.cpp and native build
│   ├── llama.cpp/               # Git submodule → ggml-org/llama.cpp
│   ├── CMakeLists.txt           # Top-level native build
│   └── scripts/
│       ├── build-android.sh     # Cross-compile for Android
│       ├── build-desktop.sh     # Build for host platform
│       └── build-server.sh      # Build llama-server
│
├── scripts/                     # Project-level scripts
│   ├── start-server.sh          # Start local inference server
│   ├── download-model.sh        # CLI model download helper
│   └── setup-dev.sh             # Development environment setup
│
├── tests/                       # Cross-platform / integration tests
│   ├── api/                     # API contract tests
│   ├── e2e/                     # End-to-end tests
│   └── fixtures/                # Test data (tiny GGUF for testing)
│
└── docs/                        # Additional docs if needed (future)
    └── api-reference.md
```

### 17.1 Rationale

- **`android/`** is a standard Android Studio project structure (Gradle, app module). This can be opened directly in Android Studio.
- **`desktop/`** is a standard Tauri 2 project (React frontend + Rust backend). This can be developed with `npm run tauri dev`.
- **`web/`** is a standalone Vite + React project. Kept separate from `desktop/src/` because the entry points, routing, and platform-specific hooks differ.
- **`shared/`** contains React/TypeScript code shared between `desktop/` and `web/`. Both import from `shared/` as a local dependency.
- **`native/`** contains the llama.cpp submodule and build scripts. Both Android and Desktop consume artifacts from here.
- **`tests/`** contains cross-platform tests (API, E2E) that are not specific to Android or Desktop.

---

## 18. Dependency Policy (Component P)

### 18.1 Principles

1. **Open source only** — no proprietary dependencies.
2. **Permissive licenses** — prefer MIT, Apache-2.0, BSD, public domain.
3. **Actively maintained** — no abandoned projects.
4. **Justify each dependency** — do not add libraries for trivial tasks.

### 18.2 License Inventory

| Dependency | License | Category |
|-----------|---------|----------|
| llama.cpp | MIT | Inference engine |
| Qwen3-4B model weights | Apache-2.0 | Model weights (not bundled) |
| Kotlin | Apache-2.0 | Language |
| Jetpack Compose | Apache-2.0 | Android UI |
| Room | Apache-2.0 | Android database |
| Kotlin Coroutines | Apache-2.0 | Async |
| AndroidX libraries | Apache-2.0 | Android framework |
| Tauri 2 | Apache-2.0 / MIT | Desktop framework |
| React | MIT | UI framework |
| TypeScript | Apache-2.0 | Language |
| Vite | MIT | Build tool |
| rusqlite | MIT | Rust SQLite bindings |
| serde | Apache-2.0 / MIT | Rust serialization |
| tokio | MIT | Rust async runtime |
| SQLite | Public domain | Database |
| Android NDK | Android Software Development Kit License | Build tool |
| CMake | BSD 3-Clause | Build system |

### 18.3 Guidelines

- Do not add a state management library (Redux, Zustand) unless complexity demands it. React `useState` + `useReducer` + context is sufficient initially.
- Do not add an ORM for the React/web side. Direct SQLite access via the Tauri backend or API is sufficient.
- Do not add a CSS framework. Vanilla CSS or CSS modules.
- Do not add a testing framework beyond Vitest (React), JUnit (Android), and built-in Rust tests.
- Networking: Use `fetch` (browser built-in) or `reqwest` (Rust). No Axios or similar wrappers.

---

## 19. Assumptions

These assumptions are recorded explicitly. If any prove incorrect, the affected design decisions should be revisited.

1. **llama.cpp stability**: We assume llama.cpp's C API (`llama.h`) is stable enough for integration. The API does change between versions; we pin to a specific commit/tag via the git submodule.

2. **GGUF backward compatibility**: We assume GGUF v3 files produced by current converters will continue to work with future llama.cpp versions. llama.cpp maintains GGUF backward compatibility.

3. **Android NDK ARM64 build**: We assume the Android NDK CMake toolchain correctly cross-compiles llama.cpp for `arm64-v8a` with NEON optimizations enabled by default. This is well-established.

4. **Adreno 710 GPU acceleration**: We do NOT assume GPU acceleration works on the Adreno 710. The OpenCL backend explicitly lists higher-end Adreno GPUs. We treat GPU acceleration as experimental and benchmark-dependent on this device.

5. **Memory mapping on Android**: We assume `mmap()` works for GGUF files on Android's filesystem (ext4/f2fs). This is standard Linux/Android behavior.

6. **Tauri 2 sidecar on Windows and Linux**: We assume Tauri 2's sidecar mechanism works reliably on Windows and Linux for managing the llama-server process. Tauri 2 is stable (released October 2024) and sidecar is a documented feature.

7. **llama-server API stability**: We assume `llama-server`'s `/v1/chat/completions`, `/v1/models`, and `/health` endpoints maintain their current API contract. We pin to a specific llama.cpp version to mitigate API drift.

8. **Qwen3-4B GGUF availability**: We assume the Qwen3-4B GGUF files remain available on Hugging Face under Apache-2.0.

9. **Room/SQLite performance**: We assume Room/SQLite can handle the write volume of incremental message persistence during streaming (a few writes per second) without significant performance impact. This is well within SQLite's capabilities.

10. **WebView on Windows and Linux**: We assume the Tauri WebView (WebView2 on Windows, WebKitGTK on Linux) supports the JavaScript features used by the React frontend (ES2020+, EventSource, fetch with ReadableStream). This is supported by modern WebView engines.
