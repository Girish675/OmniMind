# OmniMind — Implementation Plan

This document provides a phased implementation plan. Each phase is designed to be executable by a coding agent without requiring major architectural decisions. All architectural decisions are documented in [DESIGN.md](DESIGN.md).

---

## Dependency Graph

```
PHASE 0: Repository Bootstrap
    │
    ▼
PHASE 1: Native Inference Core
    │
    ├──────────────────────┐
    ▼                      ▼
PHASE 2: Model Manager   PHASE 3: Android Native Integration
    │                      │
    └──────────┬───────────┘
               ▼
         PHASE 4: Android Chat Application
               │
               ▼
         PHASE 5: Local API / Inference Host
               │
               ├──────────────────────┐
               ▼                      ▼
         PHASE 6: Desktop App    PHASE 7: Web Application
               │                      │
               └──────────┬───────────┘
                          ▼
                    PHASE 8: Performance / Acceleration
                          │
                          ▼
                    PHASE 9: Security / Hardening
                          │
                          ▼
                    PHASE 10: Testing
                          │
                          ▼
                    PHASE 11: Packaging / Release
```

**Key dependencies:**
- Phase 1 must complete before Phases 2, 3.
- Phase 2 and 3 can proceed in parallel.
- Phase 4 requires both Phase 2 and Phase 3.
- Phase 5 requires Phase 2 (model manager logic).
- Phases 6 and 7 require Phase 5 (local API server).
- Phases 8–11 require Phases 4, 6, 7 (all targets functional).

---

## PHASE 0 — Repository Bootstrap

### Objective

Set up the monorepo structure, build tooling, git submodules, and project scaffolding so that all subsequent phases can work within an established structure.

### Files/Modules to Create

```
OmniMind/
├── README.md                          # Already exists
├── DESIGN.md                          # Already exists
├── IMPLEMENTATION.md                  # Already exists (this file)
├── LICENSE                            # Already exists (Apache-2.0)
├── .gitignore                         # Comprehensive gitignore
├── .gitmodules                        # llama.cpp submodule reference
│
├── android/
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/omnimind/     # Package structure (empty)
│   │   │   │   ├── ui/
│   │   │   │   ├── domain/
│   │   │   │   ├── data/
│   │   │   │   ├── native/
│   │   │   │   └── di/
│   │   │   ├── cpp/
│   │   │   │   └── CMakeLists.txt     # Placeholder
│   │   │   ├── res/                   # Android resources
│   │   │   └── AndroidManifest.xml
│   │   ├── src/test/                  # Unit test directory
│   │   ├── src/androidTest/           # Instrumentation test directory
│   │   └── build.gradle.kts
│   ├── build.gradle.kts               # Root build file
│   ├── settings.gradle.kts
│   └── gradle.properties
│
├── desktop/
│   ├── src/                           # React frontend (scaffolded by create-tauri-app)
│   ├── src-tauri/
│   │   ├── src/main.rs                # Tauri entry point (minimal)
│   │   ├── Cargo.toml
│   │   ├── tauri.conf.json
│   │   ├── capabilities/default.json
│   │   └── binaries/                  # Empty, for llama-server later
│   ├── package.json
│   ├── tsconfig.json
│   └── vite.config.ts
│
├── web/
│   ├── src/                           # React SPA (scaffolded by Vite)
│   ├── package.json
│   ├── tsconfig.json
│   └── vite.config.ts
│
├── shared/
│   ├── api/                           # Empty
│   ├── components/                    # Empty
│   ├── hooks/                         # Empty
│   ├── types/
│   │   └── index.ts                   # Core type definitions
│   ├── utils/                         # Empty
│   ├── package.json
│   └── tsconfig.json
│
├── native/
│   ├── llama.cpp/                     # Git submodule
│   ├── CMakeLists.txt                 # Top-level native build config
│   └── scripts/
│       ├── build-android.sh
│       ├── build-android.bat
│       ├── build-desktop.sh
│       ├── build-desktop.bat
│       └── build-server.sh
│
├── scripts/
│   ├── setup-dev.sh
│   └── setup-dev.bat
│
└── tests/
    ├── api/
    ├── fixtures/
    └── package.json
```

### Dependencies

- Git
- Node.js 20+
- Rust (stable)
- Android Studio + NDK r26+
- CMake 3.22+
- JDK 17+

### Implementation Sequence

1. Create `.gitignore` with rules for:
   - Android (build/, .gradle/, *.apk, local.properties)
   - Node.js (node_modules/, dist/)
   - Rust (target/)
   - Native builds (build*/, *.so, *.dll, *.dylib)
   - IDE files (.idea/, .vscode/)
   - Model files (*.gguf)
   - OS files (.DS_Store, Thumbs.db)

2. Add llama.cpp as a git submodule:
   ```bash
   git submodule add https://github.com/ggml-org/llama.cpp.git native/llama.cpp
   # Pin to a specific stable tag/commit
   cd native/llama.cpp
   git checkout <latest-stable-tag>
   cd ../..
   git add .gitmodules native/llama.cpp
   ```

3. Create the Android project structure:
   - Create `android/` with Gradle wrapper, build scripts, and minimal application module.
   - Set `minSdk=29`, `targetSdk=35`, `compileSdk=35`.
   - Add Kotlin, Compose, Room, Coroutines dependencies.
   - Add NDK/CMake configuration in `build.gradle.kts` for native builds.
   - Create a minimal `MainActivity` with an empty Compose screen.

4. Create the Tauri desktop project:
   ```bash
   cd desktop/
   npm create tauri-app@latest ./ -- --template react-ts
   ```
   - Verify the project builds with `npm run tauri dev`.
   - Add shell plugin: `npm install @tauri-apps/plugin-shell`.

5. Create the web project:
   ```bash
   cd web/
   npm create vite@latest ./ -- --template react-ts
   ```
   - Verify it builds with `npm run dev`.

6. Create the `shared/` package:
   - Initialize with `npm init`.
   - Create `tsconfig.json` with shared compiler options.
   - Create `shared/types/index.ts` with core type definitions (see DESIGN.md Section 10.2).

7. Create native build scripts:
   - `native/CMakeLists.txt` — references `llama.cpp` subdirectory.
   - `native/scripts/build-android.sh` — cross-compile for arm64-v8a.
   - `native/scripts/build-desktop.sh` — build for host platform.
   - `native/scripts/build-server.sh` — build llama-server.

8. Create `tests/` scaffolding with a basic package.json and Vitest configuration.

9. Create `scripts/setup-dev.sh` that checks for required tools and prints setup instructions.

### Interfaces

None (bootstrap only).

### Commands to Run

```bash
# Verify git submodule
git submodule update --init --recursive

# Verify Android project
cd android && ./gradlew assembleDebug

# Verify desktop project
cd desktop && npm install && npm run tauri dev

# Verify web project
cd web && npm install && npm run dev

# Verify native build script
cd native && bash scripts/build-desktop.sh
```

### Tests

- Android project compiles without errors.
- Desktop project launches (shows default Tauri window).
- Web project serves on localhost.
- llama.cpp submodule is checked out at the pinned commit.
- Native build scripts produce expected artifacts.

### Expected Result

A fully scaffolded monorepo where each subproject compiles/runs independently. No application logic yet.

### Exit Criteria

- [ ] All directories exist per the layout.
- [ ] `.gitignore` covers all subprojects.
- [ ] llama.cpp submodule is pinned to a specific version.
- [ ] Android project compiles (empty Compose activity).
- [ ] Desktop project compiles and shows a window.
- [ ] Web project compiles and serves.
- [ ] Shared types package exists.
- [ ] Native build scripts exist and are documented.
- [ ] `setup-dev.sh` validates prerequisites.

### Known Risks

- llama.cpp tag/commit may need updating if a critical bug is found.
- Android NDK version must match the CMake toolchain expectations.
- Tauri 2 scaffolding may change between `create-tauri-app` versions; pin the version.

### NOT Implemented in This Phase

- No inference logic.
- No UI beyond placeholder screens.
- No database schema.
- No model management.
- No llama.cpp compilation yet (scripts exist but may not be runnable until NDK is configured).

---

## PHASE 1 — Native Inference Core

### Objective

Build and verify llama.cpp for the host platform (desktop) and create a minimal C API wrapper that the rest of the application will use.

### Files/Modules to Create

```
native/
├── CMakeLists.txt                      # Updated: build omnimind_core library
├── src/
│   ├── omnimind_core.h                 # C API header for inference operations
│   ├── omnimind_core.cpp               # Implementation wrapping llama.cpp
│   └── omnimind_types.h                # Shared type definitions (structs)
└── tests/
    ├── test_core.cpp                   # Native unit tests
    └── CMakeLists.txt
```

### Dependencies

- Phase 0 (repository structure, llama.cpp submodule)
- CMake 3.22+
- C++17 compiler

### Implementation Sequence

1. **Build llama.cpp for the host platform:**
   ```bash
   cd native/llama.cpp
   cmake -B build -DCMAKE_BUILD_TYPE=Release -DLLAMA_BUILD_SERVER=ON
   cmake --build build --config Release
   ```
   Verify: `build/bin/llama-server --help` runs without error.

2. **Define `omnimind_core.h`** — the C API wrapper:

   Functions to implement:
   ```c
   // Lifecycle
   int omnimind_init();                          // Initialize backends
   void omnimind_shutdown();                     // Cleanup

   // Model
   omnimind_model_t* omnimind_load_model(const char* path, omnimind_model_params_t params);
   void omnimind_free_model(omnimind_model_t* model);
   omnimind_model_info_t omnimind_get_model_info(omnimind_model_t* model);
   int omnimind_validate_gguf(const char* path); // Quick validation without full load

   // Generation
   omnimind_generation_t* omnimind_start_generation(
       omnimind_model_t* model,
       const char* prompt,
       omnimind_generation_params_t params,
       omnimind_token_callback_t callback,
       void* user_data
   );
   void omnimind_cancel_generation(omnimind_generation_t* gen);
   omnimind_stats_t omnimind_get_stats(omnimind_generation_t* gen);

   // Utilities
   int omnimind_tokenize(omnimind_model_t* model, const char* text);
   omnimind_backend_info_t* omnimind_get_backends(int* count);
   const char* omnimind_get_chat_template(omnimind_model_t* model);
   ```

3. **Implement `omnimind_core.cpp`:**
   - `omnimind_init()` → calls `llama_backend_init()`.
   - `omnimind_load_model()` → calls `llama_load_model_from_file()`, then `llama_new_context_with_model()`.
   - `omnimind_start_generation()` → tokenizes prompt, runs `llama_decode()` loop with sampling, calls callback per token.
   - `omnimind_cancel_generation()` → sets atomic cancel flag.
   - `omnimind_get_model_info()` → reads GGUF metadata via `llama_model_meta_val_str()`.
   - `omnimind_get_chat_template()` → reads `tokenizer.chat_template` from metadata.
   - `omnimind_get_backends()` → enumerates available GGML backends.
   - Thread safety: generation runs on caller's thread; cancel flag is atomic.

4. **Define types in `omnimind_types.h`:**
   ```c
   typedef struct {
       int context_size;       // n_ctx
       int threads;            // n_threads
       int batch_size;         // n_batch
       int gpu_layers;         // n_gpu_layers
   } omnimind_model_params_t;

   typedef struct {
       float temperature;
       float top_p;
       int top_k;
       float min_p;
       float repeat_penalty;
       float presence_penalty;
       float frequency_penalty;
       int max_tokens;
       int seed;
   } omnimind_generation_params_t;

   typedef struct {
       char name[256];
       char architecture[64];
       long long parameter_count;
       int context_length;
       char quantization[32];
       char chat_template[4096];  // or dynamically allocated
       int vocab_size;
       long long file_size;
   } omnimind_model_info_t;

   typedef struct {
       int prompt_tokens;
       int generated_tokens;
       float prompt_tokens_per_sec;
       float generation_tokens_per_sec;
       long long time_to_first_token_ms;
       long long total_time_ms;
   } omnimind_stats_t;

   typedef void (*omnimind_token_callback_t)(const char* token, void* user_data);
   ```

5. **Write native tests (`test_core.cpp`):**
   - Test `omnimind_init()` / `omnimind_shutdown()`.
   - Test `omnimind_validate_gguf()` with a valid and invalid file.
   - Test model loading (requires a test GGUF file — use a tiny model or a test fixture).
   - Test token counting.
   - Test backend enumeration.

6. **Update `native/CMakeLists.txt`:**
   - Build `libomnimind_core` as a shared library linking against llama.cpp.
   - Build test executable.

### Commands to Run

```bash
cd native/
cmake -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build --config Release
./build/test_core   # Run native tests (requires a test model)
```

### Tests

- `omnimind_init()` succeeds and `omnimind_shutdown()` does not crash.
- `omnimind_validate_gguf()` returns success for a valid file, error for invalid.
- `omnimind_get_backends()` returns at least CPU.
- Model loading + info retrieval works with a small test model.
- Token callback receives tokens during generation.
- Cancel stops generation early.

### Expected Result

A working native library (`libomnimind_core.so` / `.dll`) that wraps llama.cpp with a clean C API. Verified on the host platform.

### Exit Criteria

- [ ] `libomnimind_core` builds and links against llama.cpp.
- [ ] All C API functions are implemented.
- [ ] Native tests pass with a test model.
- [ ] Backend enumeration works.
- [ ] Model info extraction works (name, architecture, context length, chat template).
- [ ] Streaming generation with callback works.
- [ ] Cancellation works.

### Known Risks

- llama.cpp API changes between versions. Mitigated by pinning submodule.
- Test model must be small enough to include in the repo or downloadable in CI.
- Chat template extraction depends on the model having `tokenizer.chat_template` metadata.

### NOT Implemented in This Phase

- No Android cross-compilation (Phase 3).
- No JNI bridge (Phase 3).
- No model manager (Phase 2).
- No server (Phase 5).
- No prompt formatting using chat template (the core extracts it; formatting is Phase 4).

---

## PHASE 2 — Model Manager

### Objective

Implement the model management subsystem: database schema, model registry, import, download, validation, metadata extraction, and CRUD operations.

### Files/Modules to Create

```
shared/
├── types/
│   ├── model.ts                        # Model type definitions
│   └── settings.ts                     # Settings type definitions
└── api/
    └── types.ts                        # API request/response types

android/app/src/main/java/com/omnimind/
├── data/
│   ├── db/
│   │   ├── OmniMindDatabase.kt        # Room database definition
│   │   ├── ModelDao.kt                 # Model data access object
│   │   ├── ConversationDao.kt          # Conversation DAO (schema only)
│   │   ├── SettingsDao.kt              # Settings DAO
│   │   └── BenchmarkDao.kt            # Benchmark DAO (schema only)
│   ├── entity/
│   │   ├── ModelEntity.kt             # Room entity for models
│   │   ├── ConversationEntity.kt      # Room entity (schema only)
│   │   ├── MessageEntity.kt           # Room entity (schema only)
│   │   ├── SettingsEntity.kt          # Room entity
│   │   └── BenchmarkEntity.kt         # Room entity (schema only)
│   └── repository/
│       ├── ModelRepository.kt          # Model CRUD operations
│       └── SettingsRepository.kt       # Settings operations
├── domain/
│   ├── model/
│   │   ├── ImportModelUseCase.kt       # Import from local file
│   │   ├── DownloadModelUseCase.kt     # Download from URL
│   │   ├── ValidateModelUseCase.kt     # GGUF validation
│   │   └── ModelMetadataExtractor.kt   # Extract info from GGUF
│   └── util/
│       └── StorageUtils.kt            # Free space checks, file operations
```

### Dependencies

- Phase 0 (project structure, Room dependencies)
- Phase 1 (native core for metadata extraction — `omnimind_validate_gguf`, `omnimind_get_model_info`)

### Implementation Sequence

1. **Define Room database schema** — implement all entity classes and DAOs per DESIGN.md Section 6.2.

2. **Implement `ModelRepository`:**
   - `getAll()` → returns `Flow<List<ModelEntity>>`.
   - `getById(id)` → returns single model.
   - `insert(model)` → add to registry.
   - `update(model)` → update metadata.
   - `delete(id)` → check not loaded, remove from DB, optionally delete file.
   - `setDefault(id)` → clear other defaults, set this one.
   - `getDefault()` → return default model.

3. **Implement `ImportModelUseCase`:**
   - Accept a content URI (Android) or file path.
   - Copy file to app's model directory (if not already there).
   - Validate GGUF (via native call or pure-Kotlin magic-byte check).
   - Extract metadata (architecture, params, quantization, context, chat template).
   - Compute SHA-256 hash.
   - Create `ModelEntity` and insert into database.
   - Return result with model info.

4. **Implement `DownloadModelUseCase`:**
   - Accept a URL (Hugging Face direct link).
   - Check free space (file size from HTTP HEAD + 10% buffer).
   - Download to temp file (`.gguf.part`) with progress reporting.
   - Support HTTP Range header for resume.
   - On completion: verify SHA-256, rename to `.gguf`, register in database.
   - On failure: preserve temp file for resume.

5. **Implement `ValidateModelUseCase`:**
   - Check file exists and is readable.
   - Check file size > 0.
   - Read first 4 bytes and verify GGUF magic number (`0x46475547` = "GGUF").
   - Optionally: attempt full load via native core to verify.

6. **Implement `ModelMetadataExtractor`:**
   - Option A (preferred): Call native `omnimind_get_model_info()` via JNI.
   - Option B (fallback): Parse GGUF header in Kotlin (read metadata keys/values from the binary format).
   - Extract: architecture, parameter count, quantization type, context length, chat template, vocab size.

7. **Implement `StorageUtils`:**
   - `getFreeDiskSpace()` → uses `StatFs` on Android.
   - `getModelDirectory()` → returns `getExternalFilesDir(null)/models/`.
   - `ensureModelDirectoryExists()` → creates if needed.
   - `computeSha256(file)` → hash computation.

8. **Implement `SettingsRepository`:**
   - Key-value store for app settings.
   - Typed getters for inference settings (temperature, top_p, etc.) with defaults.

### Commands to Run

```bash
cd android/
./gradlew test                    # Unit tests
./gradlew connectedAndroidTest    # Instrumentation tests (requires device/emulator)
```

### Tests

- **Unit tests:**
  - `ModelEntity` serialization/deserialization.
  - `ValidateModelUseCase` with mock file (valid magic bytes vs. invalid).
  - `StorageUtils.computeSha256()` with known test data.
  - Settings defaults are correct.

- **Instrumentation tests:**
  - Room database migration (create, insert, query).
  - `ModelRepository` CRUD operations.
  - `ImportModelUseCase` with a small test GGUF file.
  - Free space check returns reasonable value.

### Expected Result

A working model management subsystem on Android: database, import, download, validation, metadata, and CRUD. No UI yet.

### Exit Criteria

- [ ] Room database creates successfully with all tables.
- [ ] Model CRUD operations work (insert, query, update, delete).
- [ ] GGUF validation detects valid and invalid files.
- [ ] Metadata extraction returns correct values for the reference model.
- [ ] SHA-256 computation works.
- [ ] Free space check works.
- [ ] Download with progress reporting works (against a test URL).
- [ ] Settings are stored and retrieved correctly.
- [ ] Default model can be set and queried.

### Known Risks

- GGUF metadata extraction via JNI requires Phase 3 (JNI bridge). Alternative: parse GGUF binary in Kotlin directly (more work but no native dependency).
- Download resume depends on server supporting HTTP Range requests (Hugging Face does).
- Scoped storage on newer Android versions may affect file operations.

### NOT Implemented in This Phase

- No UI (Phase 4).
- No model loading into inference engine (Phase 3/4).
- No chat functionality.
- No benchmark storage (schema exists, no logic).

---

## PHASE 3 — Android Native Integration

### Objective

Cross-compile llama.cpp for Android ARM64, implement the JNI bridge, and verify native inference works on an Android device.

### Files/Modules to Create

```
android/app/src/main/
├── cpp/
│   ├── CMakeLists.txt                  # Android native build config
│   └── omnimind_jni.cpp                # JNI implementation
├── jniLibs/
│   └── arm64-v8a/                      # Built .so files go here
│       ├── libllama.so
│       ├── libggml.so
│       └── libomnimind_jni.so
└── java/com/omnimind/
    └── native/
        ├── LlamaEngine.kt              # Kotlin JNI bridge class
        ├── ModelParams.kt              # JNI parameter types
        ├── GenerationSettings.kt       # JNI generation settings
        ├── GenerationCallback.kt       # Token callback interface
        ├── ModelInfo.kt                # Model info data class
        ├── InferenceStats.kt           # Stats data class
        └── BackendInfo.kt             # Backend info data class
```

### Dependencies

- Phase 0 (Android project structure)
- Phase 1 (native inference core — `omnimind_core.h`)
- Android NDK r26+

### Implementation Sequence

1. **Cross-compile llama.cpp for ARM64:**
   ```bash
   cd native/
   bash scripts/build-android.sh
   ```
   The script runs:
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

   cmake --build build-android --config Release
   ```
   Copy `libllama.so` and `libggml.so` to `android/app/src/main/jniLibs/arm64-v8a/`.

2. **Create `android/app/src/main/cpp/CMakeLists.txt`:**
   - Define `omnimind_jni` shared library.
   - Link against pre-built `libllama.so` and `libggml.so`.
   - Include `llama.h` headers from `native/llama.cpp/include/`.

3. **Implement `omnimind_jni.cpp`:**
   - `JNIEXPORT` functions matching the `LlamaEngine` Kotlin class.
   - `Java_com_omnimind_native_LlamaEngine_loadModel` → loads model, returns handle (pointer as `jlong`).
   - `Java_com_omnimind_native_LlamaEngine_unloadModel` → frees model.
   - `Java_com_omnimind_native_LlamaEngine_getModelInfo` → returns metadata as a Java object.
   - `Java_com_omnimind_native_LlamaEngine_startGeneration` → runs generation on a new native thread, calls Java callback per token.
   - `Java_com_omnimind_native_LlamaEngine_cancelGeneration` → sets cancel flag.
   - `Java_com_omnimind_native_LlamaEngine_getAvailableBackends` → enumerates backends.
   - `Java_com_omnimind_native_LlamaEngine_tokenize` → returns token count.
   - `Java_com_omnimind_native_LlamaEngine_getPerformanceStats` → returns stats.

   Key implementation details:
   - Model handles stored as `jlong` (cast to/from pointers).
   - Token callback: cache the `JNIEnv*` and `jobject` for the callback. Use `AttachCurrentThread()` for the generation thread.
   - Memory: the JNI layer does not copy model data into Java heap. Only token strings (small) cross the JNI boundary.
   - Error handling: return error codes or throw Java exceptions on failure.

4. **Implement Kotlin JNI bridge (`LlamaEngine.kt`):**
   ```kotlin
   object LlamaEngine {
       init {
           System.loadLibrary("omnimind_jni")
       }

       external fun loadModel(path: String, contextSize: Int, threads: Int,
                              batchSize: Int, gpuLayers: Int): Long
       external fun unloadModel(handle: Long)
       external fun getModelInfo(handle: Long): ModelInfo
       external fun startGeneration(handle: Long, prompt: String,
                                    settings: GenerationSettings,
                                    callback: GenerationCallback): Long
       external fun cancelGeneration(genHandle: Long)
       external fun getAvailableBackends(): Array<BackendInfo>
       external fun tokenize(handle: Long, text: String): Int
       external fun getPerformanceStats(genHandle: Long): InferenceStats
   }
   ```

5. **Configure Android build to compile native code:**
   In `app/build.gradle.kts`:
   ```kotlin
   android {
       externalNativeBuild {
           cmake {
               path = file("src/main/cpp/CMakeLists.txt")
           }
       }
       defaultConfig {
           ndk {
               abiFilters += "arm64-v8a"
           }
       }
   }
   ```

6. **Write a simple test Activity** that:
   - Loads a small GGUF model from the device.
   - Calls `getModelInfo()` and logs the result.
   - Runs a short generation and logs tokens.
   - Verifies cancellation works.

### Commands to Run

```bash
# Build native libraries
cd native/
bash scripts/build-android.sh

# Build and install Android app
cd android/
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Run with a test model on device
adb push test-model.gguf /sdcard/Download/
```

### Tests

- Native `.so` files are produced for arm64-v8a.
- Android project compiles with JNI code.
- `LlamaEngine.loadModel()` succeeds on a device with a valid GGUF.
- `LlamaEngine.getModelInfo()` returns correct metadata.
- Token generation produces output via callback.
- `cancelGeneration()` stops generation within a reasonable time.
- `unloadModel()` frees memory (verify with `Debug.getNativeHeapAllocatedSize()`).
- Multiple load/unload cycles don't leak memory.
- Invalid model path returns error (no crash).

### Expected Result

llama.cpp runs on an Android device via JNI. A model can be loaded, inference produces tokens, and the model can be unloaded. Verified on the Motorola Edge 60 Stylus (or ARM64 emulator).

### Exit Criteria

- [ ] `libllama.so`, `libggml.so`, `libomnimind_jni.so` built for arm64-v8a.
- [ ] Android app loads and unloads a GGUF model without crash.
- [ ] `getModelInfo()` returns correct metadata.
- [ ] Generation produces tokens via callback.
- [ ] Cancellation works.
- [ ] Memory is released on unload.
- [ ] Backend enumeration lists at least CPU.

### Known Risks

- JNI callback threading: calling Java methods from a native thread requires `AttachCurrentThread()`. Must detach properly to avoid leaks.
- Large models may cause the Android low-memory killer to terminate the process. Need to handle this gracefully.
- Debug builds of llama.cpp are extremely slow (missing SIMD optimizations); always build Release.

### NOT Implemented in This Phase

- No UI (Phase 4).
- No chat template formatting (Phase 4).
- No model manager integration (Phase 4).
- No GPU acceleration testing (Phase 8).

---

## PHASE 4 — Android Chat Application

### Objective

Build the full Android chat application with all screens, connecting the model manager, JNI bridge, chat engine, and Compose UI.

### Files/Modules to Create

```
android/app/src/main/java/com/omnimind/
├── OmniMindApp.kt                      # Application class
├── MainActivity.kt                      # Main activity (updated)
├── ui/
│   ├── navigation/
│   │   └── NavGraph.kt                  # Navigation graph
│   ├── theme/
│   │   ├── Theme.kt                     # Material 3 theme
│   │   ├── Color.kt                     # Color definitions
│   │   └── Typography.kt               # Typography definitions
│   ├── chat/
│   │   ├── ChatScreen.kt               # Chat screen Compose UI
│   │   ├── ChatViewModel.kt            # Chat ViewModel
│   │   ├── MessageBubble.kt            # Message display component
│   │   └── ChatInput.kt                # Input field + send button
│   ├── conversations/
│   │   ├── ConversationListScreen.kt   # Conversation list
│   │   └── ConversationListViewModel.kt
│   ├── models/
│   │   ├── ModelPickerScreen.kt        # Model selection
│   │   ├── ModelManagerScreen.kt       # Full model management
│   │   ├── ModelManagerViewModel.kt
│   │   └── ModelImportDialog.kt        # Import/download dialog
│   ├── settings/
│   │   ├── SettingsScreen.kt           # Settings UI
│   │   └── SettingsViewModel.kt
│   ├── benchmark/
│   │   ├── BenchmarkScreen.kt          # Benchmark UI
│   │   └── BenchmarkViewModel.kt
│   └── about/
│       └── AboutScreen.kt              # About/licenses screen
├── domain/
│   ├── chat/
│   │   ├── SendMessageUseCase.kt       # Send + generate
│   │   ├── ChatTemplateFormatter.kt    # Format prompt using chat template
│   │   └── ContextManager.kt          # Manage context window
│   └── benchmark/
│       └── RunBenchmarkUseCase.kt      # Execute benchmark
├── data/
│   └── repository/
│       ├── ConversationRepository.kt   # Conversation CRUD
│       └── BenchmarkRepository.kt      # Benchmark CRUD
└── di/
    └── AppModule.kt                    # Dependency injection (manual or Hilt)
```

### Dependencies

- Phase 2 (model manager, database)
- Phase 3 (JNI bridge, native libraries)

### Implementation Sequence

1. **Create application theme** (Material 3, dark/light mode).

2. **Implement navigation graph** with routes:
   - `/chat/{conversationId}` — chat screen
   - `/conversations` — conversation list
   - `/models` — model manager
   - `/settings` — settings
   - `/benchmark` — benchmark
   - `/about` — about/licenses

3. **Implement `ChatViewModel`:**
   - State: current conversation, messages (as `StateFlow`), generation status, loaded model.
   - Actions: sendMessage, stopGeneration, regenerate, editAndResend, newConversation.
   - Uses `SendMessageUseCase` which:
     a. Saves user message to DB.
     b. Assembles prompt using `ChatTemplateFormatter`.
     c. Calls `LlamaEngine.startGeneration()` with callback.
     d. Callback emits tokens to a `MutableStateFlow<String>`.
     e. Periodic DB flush of partial content.
     f. On completion: mark message as complete, save stats.

4. **Implement `ChatTemplateFormatter`:**
   - Reads the chat template from the loaded model's metadata.
   - For Qwen3 models: uses `<|im_start|>user\n{content}<|im_end|>` format.
   - For models with Jinja2 templates: applies a simplified template engine or falls back to a generic chat format.
   - The formatter is model-agnostic: reads the template, applies it to the message list.

5. **Implement `ChatScreen`:**
   - Scrollable message list with auto-scroll during streaming.
   - Message bubbles for user (right-aligned) and assistant (left-aligned).
   - System prompt indicator.
   - Streaming text appears character-by-character (or chunk-by-chunk).
   - Stop button visible during generation.
   - Input field with send button (disabled during generation).
   - Long-press on assistant message: regenerate, copy.
   - Long-press on user message: edit, copy, delete.

6. **Implement conversation list, model manager, settings, benchmark, and about screens.**

7. **Implement `ConversationRepository`:**
   - CRUD for conversations and messages.
   - Incremental message save during streaming.
   - Soft delete + hard delete cleanup.

8. **Implement dependency injection** — provide ViewModels, repositories, use cases, and the `LlamaEngine` singleton.

9. **Wire everything together in `MainActivity`.**

### Commands to Run

```bash
cd android/
./gradlew assembleDebug
./gradlew test
./gradlew connectedAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Tests

- **Unit tests:**
  - `ChatTemplateFormatter` produces correct prompt for Qwen3 format.
  - `ContextManager` truncates old messages when context is exceeded.
  - ViewModel state transitions are correct (idle → generating → idle).

- **Instrumentation tests:**
  - Create conversation → send message → receive response (with mock or real model).
  - Stop generation mid-stream → partial message is saved.
  - Conversation list shows conversations sorted by last update.
  - Model picker shows registered models.
  - Settings changes persist.

### Expected Result

A working Android chat application that can load a GGUF model, send messages, receive streaming responses, and persist conversations. All screens are functional.

### Exit Criteria

- [ ] Chat screen renders and accepts input.
- [ ] Messages stream in real-time during generation.
- [ ] Stop button cancels generation.
- [ ] Conversations persist across app restarts.
- [ ] Model picker allows selecting and loading models.
- [ ] Model manager allows importing GGUF files.
- [ ] Settings screen allows configuring inference parameters.
- [ ] Benchmark screen can run a benchmark and display results.
- [ ] About screen shows licenses.
- [ ] UI remains responsive during inference.
- [ ] No ANR (Application Not Responding) during model load or generation.

### Known Risks

- Chat template formatting for arbitrary models is complex. Start with Qwen3 format and add a generic fallback.
- Streaming UI performance: rapid StateFlow updates may cause recomposition overhead. Consider debouncing.
- Model loading time can be several seconds; show a loading indicator.

### NOT Implemented in This Phase

- No desktop or web targets.
- No GPU acceleration testing (Phase 8).
- No LAN mode (Phase 9).
- No comprehensive security hardening (Phase 9).

---

## PHASE 5 — Local API / Inference Host

### Objective

Build the local API server that the desktop and web applications will use. This is a thin wrapper around `llama-server` that adds conversation management, model management, and OmniMind-specific endpoints.

### Files/Modules to Create

```
desktop/src-tauri/src/
├── main.rs                              # Updated: Tauri setup with commands
├── commands/
│   ├── mod.rs
│   ├── health.rs                        # Health check command
│   ├── models.rs                        # Model management commands
│   ├── chat.rs                          # Chat completion commands
│   ├── conversations.rs                 # Conversation CRUD commands
│   └── settings.rs                      # Settings commands
├── server/
│   ├── mod.rs
│   ├── manager.rs                       # llama-server process lifecycle
│   └── client.rs                        # HTTP client for llama-server API
└── db/
    ├── mod.rs
    ├── schema.rs                        # SQLite schema (matches DESIGN.md)
    ├── models.rs                        # Model queries
    ├── conversations.rs                 # Conversation queries
    └── settings.rs                      # Settings queries

shared/
├── api/
│   ├── client.ts                        # HTTP API client
│   ├── types.ts                         # API request/response types
│   └── streaming.ts                     # SSE streaming utilities
```

### Dependencies

- Phase 0 (desktop project structure)
- Phase 1 (native inference core — llama-server binary)
- Phase 2 (model manager design — data model)

### Implementation Sequence

1. **Build llama-server for host platform:**
   ```bash
   cd native/
   bash scripts/build-server.sh
   ```
   Copy the binary to `desktop/src-tauri/binaries/` with the correct target-triple suffix.

2. **Implement `server::manager`:**
   - `ServerManager` struct with:
     - `start(model_path, port, settings)` → spawn llama-server process.
     - `stop()` → kill process gracefully (SIGTERM, then SIGKILL after timeout).
     - `restart(model_path, settings)` → stop + start.
     - `health_check()` → GET `/health`.
     - `is_running()` → check process status.
   - Port allocation: bind TcpListener to port 0, read allocated port, close listener, use that port.
   - Process monitoring: check for unexpected exit in a background task.

3. **Implement `server::client`:**
   - HTTP client (using `reqwest`) to communicate with llama-server.
   - `chat_completion(messages, settings, stream)` → POST `/v1/chat/completions`.
   - `get_models()` → GET `/v1/models`.
   - `health()` → GET `/health`.
   - Streaming: read response body as SSE events, parse JSON chunks.

4. **Implement `db` module:**
   - SQLite database using `rusqlite`.
   - Create schema on first run (same schema as DESIGN.md Section 6.2).
   - CRUD operations for models, conversations, messages, settings, benchmarks.

5. **Implement Tauri commands:**
   - Each command is a `#[tauri::command]` function.
   - Commands match the internal API (DESIGN.md Section 10.2):
     - `health_check` → returns server status + loaded model.
     - `list_models` → queries local DB.
     - `get_model` → queries local DB.
     - `load_model` → restart llama-server with selected model.
     - `unload_model` → stop llama-server.
     - `chat_completion` → proxy to llama-server, save conversation to DB.
     - `cancel_generation` → abort ongoing request.
     - `list_conversations` → queries local DB.
     - `get_conversation` → queries local DB with messages.
     - `delete_conversation` → soft delete in DB.
     - `rename_conversation` → update in DB.
     - `get_settings` / `update_settings` → DB operations.
     - `import_model` → validate file, register in DB.

6. **Implement shared TypeScript API client (`shared/api/client.ts`):**
   - Functions that call Tauri commands (for desktop) or HTTP endpoints (for web).
   - Abstract the transport layer so the same API types are used everywhere.
   - SSE streaming utilities for handling `text/event-stream` responses.

7. **Implement shared TypeScript types (`shared/api/types.ts`):**
   - Types matching the API schemas in DESIGN.md Section 10.2.

### Commands to Run

```bash
# Build llama-server
cd native/ && bash scripts/build-server.sh

# Copy to Tauri binaries
cp native/build/bin/llama-server desktop/src-tauri/binaries/llama-server-<target-triple>

# Build and test desktop backend
cd desktop/
cargo test --manifest-path src-tauri/Cargo.toml

# Run desktop app in dev mode
npm run tauri dev
```

### Tests

- **Rust tests:**
  - Server manager starts and stops llama-server.
  - Health check returns expected status.
  - Database CRUD operations work.
  - Chat completion proxy returns valid response.
  - Streaming events are parsed correctly.

- **TypeScript tests:**
  - API types are correctly defined.
  - SSE parser handles well-formed and malformed events.
  - API client handles errors gracefully.

### Expected Result

A working Tauri backend that manages llama-server, provides a database for conversations/models, and exposes Tauri commands for the frontend. The shared API client is ready for both desktop and web.

### Exit Criteria

- [ ] llama-server binary is built and bundled as a sidecar.
- [ ] Server manager starts/stops/restarts llama-server.
- [ ] Health check works.
- [ ] Model loading (via server restart) works.
- [ ] Chat completion (non-streaming and streaming) works via proxy.
- [ ] Conversation CRUD works in the database.
- [ ] Model registry CRUD works in the database.
- [ ] Settings CRUD works.
- [ ] Shared TypeScript API client compiles.
- [ ] SSE streaming parser works.

### Known Risks

- llama-server startup time varies; health check polling must handle slow starts.
- Process management on Windows differs from Linux (TerminateProcess vs SIGTERM).
- Port conflicts if another application uses the allocated port between allocation and server start (unlikely but possible).

### NOT Implemented in This Phase

- No desktop UI (Phase 6).
- No web UI (Phase 7).
- No LAN mode (Phase 9).
- No authentication (Phase 9).

---

## PHASE 6 — Desktop Application

### Objective

Build the desktop React frontend within the Tauri shell, providing a full chat interface, model management, and settings.

### Files/Modules to Create

```
desktop/src/
├── App.tsx                              # Root component with routing
├── main.tsx                             # Entry point
├── index.css                            # Global styles
├── components/
│   ├── ChatView.tsx                     # Chat interface
│   ├── MessageBubble.tsx                # Message display
│   ├── ChatInput.tsx                    # Input with send button
│   ├── Sidebar.tsx                      # Conversation list sidebar
│   ├── ModelPicker.tsx                  # Model selection
│   ├── SettingsPanel.tsx                # Settings UI
│   ├── StatusBar.tsx                    # Server status, model info
│   └── LoadingOverlay.tsx              # Model loading indicator
├── hooks/
│   ├── useChat.ts                       # Chat state management
│   ├── useModels.ts                     # Model management
│   ├── useSettings.ts                   # Settings management
│   ├── useServerStatus.ts              # Server health monitoring
│   └── useTauriCommands.ts             # Wrapper around Tauri invoke
├── pages/
│   ├── ChatPage.tsx                     # Main chat page
│   ├── ModelsPage.tsx                   # Model management page
│   ├── SettingsPage.tsx                 # Settings page
│   ├── BenchmarkPage.tsx                # Benchmark page
│   └── AboutPage.tsx                    # About/licenses page
└── utils/
    └── tauriAdapter.ts                  # Adapts shared API client for Tauri
```

### Dependencies

- Phase 5 (Tauri backend, commands, server management)
- Shared components/hooks/types from `shared/`

### Implementation Sequence

1. **Set up routing** — use React Router or a simple state-based router for the desktop app pages.

2. **Create the Tauri adapter (`tauriAdapter.ts`):**
   - Implements the shared API interface using `@tauri-apps/api/core` `invoke()`.
   - Maps each API operation to a Tauri command invocation.

3. **Implement `useChat` hook:**
   - Manages current conversation state.
   - Sends messages via Tauri commands.
   - Handles streaming by reading SSE events from the Tauri backend (which proxies llama-server).
   - Handles cancellation.
   - Auto-saves conversation state.

4. **Implement `useModels` hook:**
   - Lists models from the local database.
   - Loads/unloads models.
   - Imports models via file dialog.
   - Reports model loading progress.

5. **Implement `useServerStatus` hook:**
   - Polls server health periodically.
   - Shows connection status in the UI.
   - Detects server crashes and offers restart.

6. **Build the chat interface:**
   - Sidebar with conversation list (create, rename, delete).
   - Main panel with message history and streaming output.
   - Input bar with send button and stop button.
   - Model selector in the header or sidebar.
   - Generation settings (temperature, etc.) accessible via a settings icon.

7. **Build model management page:**
   - List of imported models with metadata.
   - Import button (opens native file dialog via Tauri).
   - Delete, rename, set default actions.
   - Model info display (architecture, size, quantization, context).

8. **Build settings page:**
   - Inference settings (temperature, top_p, top_k, min_p, penalties, context size, threads, batch, GPU layers).
   - App settings (theme, LAN mode placeholder).
   - Storage information.

9. **Build benchmark and about pages.**

10. **Style the application** — clean, modern design. Dark mode by default.

### Commands to Run

```bash
cd desktop/
npm install
npm run tauri dev                # Development mode
npm run tauri build              # Production build
npm test                         # Frontend tests
```

### Tests

- Chat sends messages and receives streaming responses.
- Conversation list updates when new conversations are created.
- Model import via file dialog works.
- Model switching restarts the server with the new model.
- Settings changes persist across app restarts.
- Stop button cancels generation.
- Error states are displayed (server not running, model load failure).

### Expected Result

A working desktop application on Windows and Linux with full chat, model management, settings, and benchmark functionality.

### Exit Criteria

- [ ] Desktop app launches and connects to llama-server.
- [ ] Chat interface sends and receives messages with streaming.
- [ ] Conversation list works (create, rename, delete, switch).
- [ ] Model management works (import, load, unload, delete).
- [ ] Settings are configurable and persistent.
- [ ] Stop/cancel generation works.
- [ ] Server status is displayed.
- [ ] Error handling is user-friendly.
- [ ] App works on Windows and Linux.

### Known Risks

- WebView rendering differences between Windows (WebView2) and Linux (WebKitGTK).
- File dialog behavior may differ between platforms.
- Streaming through Tauri commands may need a different pattern than direct SSE (consider Tauri events).

### NOT Implemented in This Phase

- No macOS support (future).
- No LAN mode (Phase 9).
- No GPU acceleration testing (Phase 8).

---

## PHASE 7 — Web Application

### Objective

Build a standalone web interface that connects to a locally running inference server (llama-server) over HTTP/SSE.

### Files/Modules to Create

```
web/src/
├── App.tsx                              # Root component
├── main.tsx                             # Entry point
├── index.css                            # Global styles
├── components/
│   ├── ChatView.tsx                     # Chat interface (shared or web-specific)
│   ├── MessageBubble.tsx
│   ├── ChatInput.tsx
│   ├── Sidebar.tsx
│   ├── ConnectionStatus.tsx             # Server connection indicator
│   ├── ServerConfig.tsx                 # Server URL configuration
│   └── ModelInfo.tsx                    # Display loaded model info
├── hooks/
│   ├── useChat.ts                       # Chat with direct HTTP/SSE
│   ├── useServerConnection.ts          # Connection management
│   └── useLocalStorage.ts              # Browser localStorage for preferences
├── pages/
│   ├── ChatPage.tsx
│   └── SettingsPage.tsx
└── utils/
    └── httpAdapter.ts                   # Adapts shared API client for HTTP
```

### Dependencies

- Phase 5 (shared API types, streaming utilities)
- A running llama-server instance (started manually or by the desktop app)

### Implementation Sequence

1. **Create the HTTP adapter (`httpAdapter.ts`):**
   - Implements the shared API interface using `fetch` and `EventSource`.
   - Base URL is configurable (default: `http://localhost:8080`).
   - Handles CORS if the server is on a different origin.

2. **Implement server connection UI:**
   - On first load, prompt for server URL (with localhost default).
   - Show connection status indicator (connected, disconnected, connecting).
   - Auto-reconnect with exponential backoff.

3. **Implement chat interface:**
   - Reuse shared components from `shared/components/` where possible.
   - Web-specific: server URL configuration, connection status.
   - SSE streaming via `fetch` with `ReadableStream` or `EventSource`.

4. **Implement conversation management:**
   - Conversations are stored on the server (via API calls).
   - The web UI is a thin client; it does not persist conversations in the browser.
   - If the server is restarted, conversations are still available (stored in server's SQLite DB).

5. **Implement settings page:**
   - Inference settings sent with each request.
   - UI preferences (theme) stored in `localStorage`.

6. **Handle disconnection:**
   - If the server goes down during streaming, show partial response and connection error.
   - Offer reconnect button.
   - Do not lose the user's typed input on disconnection.

7. **Responsive design** — works on desktop, tablet, and mobile browsers.

### Commands to Run

```bash
cd web/
npm install
npm run dev                      # Start dev server
npm run build                    # Production build
npm test                         # Frontend tests

# Start the inference server (separately)
cd native/build/bin/
./llama-server --model /path/to/model.gguf --host 127.0.0.1 --port 8080
```

### Tests

- Web app connects to a running llama-server.
- Chat sends and receives messages.
- SSE streaming works correctly.
- Connection loss is detected and displayed.
- Reconnection works.
- Server URL configuration persists.
- Responsive layout works on different screen sizes.

### Expected Result

A working web interface that can chat with a locally running llama-server. Functional in any modern browser.

### Exit Criteria

- [ ] Web app loads and connects to the configured server.
- [ ] Chat interface works with streaming.
- [ ] Connection status is displayed.
- [ ] Disconnection is handled gracefully.
- [ ] Server URL is configurable.
- [ ] Responsive design works.
- [ ] Works in Chrome, Firefox, and Edge.

### Known Risks

- CORS issues if the web app is served from a different origin than llama-server.
- `EventSource` does not support POST requests; may need `fetch` + `ReadableStream` instead.
- Browser may throttle background tabs, affecting long-running generations.

### NOT Implemented in This Phase

- No local conversation storage in the browser (server handles persistence).
- No model management in the web UI (manage via desktop app or CLI).
- No file upload for model import (model files are too large for browser upload in the initial version).

---

## PHASE 8 — Performance / Acceleration

### Objective

Implement the benchmark subsystem, test optional GPU acceleration on the target device, and optimize performance.

### Files/Modules to Create

```
android/app/src/main/java/com/omnimind/
├── domain/benchmark/
│   ├── RunBenchmarkUseCase.kt          # Updated: full implementation
│   ├── BenchmarkScenario.kt           # Benchmark configuration
│   └── ThermalMonitor.kt              # Thermal state monitoring
├── data/repository/
│   └── BenchmarkRepository.kt         # Updated: full implementation

native/
├── scripts/
│   ├── build-android-opencl.sh        # Build with OpenCL backend
│   └── build-android-vulkan.sh        # Build with Vulkan backend
```

### Dependencies

- Phase 4 (Android app functional)
- Phase 6 (Desktop app functional)

### Implementation Sequence

1. **Implement the full benchmark flow on Android:**
   - Standardized prompt (fixed text, configurable length).
   - Configurable generation length (default: 256 tokens).
   - Record all metrics from DESIGN.md Section 12.3.
   - Display results in the benchmark screen with comparison table.

2. **Implement thermal monitoring:**
   - On Android: use `PowerManager.getThermalHeadroom()` (API 30+).
   - Record thermal state before and after benchmark.
   - Record battery level before and after.

3. **Build llama.cpp with optional GPU backends for Android:**
   - **OpenCL**: Requires OpenCL headers (from Khronos) and ICD loader.
     ```bash
     cmake ... -DGGML_OPENCL=ON
     ```
   - **Vulkan**: Requires Vulkan SDK headers.
     ```bash
     cmake ... -DGGML_VULKAN=ON
     ```
   - These produce additional `.so` files that are loaded at runtime if available.

4. **Test GPU backends on the Motorola Edge 60 Stylus:**
   - Attempt to load the model with `gpu_layers > 0`.
   - If GPU initialization fails, fall back to CPU.
   - Run benchmarks with CPU-only and GPU-offloaded configurations.
   - Compare results.
   - Document whether the Adreno 710 provides any acceleration.

5. **Implement benchmark comparison on desktop:**
   - Similar flow, adapted for the desktop environment.
   - Test CPU-only and optional Vulkan/CUDA backends.

6. **Performance optimizations:**
   - Tune default thread count based on device (4 for big cores on the Snapdragon 7s Gen 2).
   - Tune batch size for optimal prompt processing.
   - Verify memory-mapping is working correctly on Android.
   - Profile and optimize the streaming pipeline (JNI callback overhead, UI recomposition frequency).

### Commands to Run

```bash
# Build with OpenCL (Android)
cd native/ && bash scripts/build-android-opencl.sh

# Build with Vulkan (Android)
cd native/ && bash scripts/build-android-vulkan.sh

# Run benchmark on device
adb shell am start -n com.omnimind/.BenchmarkActivity
```

### Tests

- Benchmark completes and records all metrics.
- Multiple benchmarks can be compared.
- GPU backend detection works (reports available backends).
- GPU fallback to CPU works when GPU initialization fails.
- Thermal monitoring records state.
- Benchmark results persist across app restarts.

### Expected Result

A working benchmark subsystem that can measure and compare inference performance across backends. GPU acceleration tested (results documented, not guaranteed).

### Exit Criteria

- [ ] Benchmark runs to completion on Android and desktop.
- [ ] All metrics are recorded (tokens/sec, TTFT, memory, thermal, battery).
- [ ] Benchmark results are stored and displayable.
- [ ] Comparison between runs is possible.
- [ ] GPU backend detection works.
- [ ] GPU fallback to CPU works.
- [ ] Performance is documented for the reference device + model.

### Known Risks

- GPU backends may not work on the Adreno 710. This is expected and acceptable — CPU is the baseline.
- Thermal throttling during benchmarks may produce inconsistent results. Run multiple times and note thermal state.
- OpenCL driver access on Android may require specific device configurations.

### NOT Implemented in This Phase

- No advanced GPU optimizations (future).
- No automatic backend selection (user chooses based on benchmark results).

---

## PHASE 9 — Security / Hardening

### Objective

Implement security measures, LAN mode with authentication, input validation, and harden all user-facing endpoints.

### Files/Modules to Create

```
desktop/src-tauri/src/
├── security/
│   ├── mod.rs
│   ├── auth.rs                          # Bearer token authentication
│   ├── validation.rs                    # Input validation utilities
│   └── sanitize.rs                      # Path and string sanitization

desktop/src/
├── components/
│   └── LanModeWarning.tsx              # LAN mode warning dialog

shared/
├── utils/
│   └── validation.ts                    # Shared input validation
```

### Dependencies

- Phase 5, 6, 7 (all targets functional)

### Implementation Sequence

1. **Input validation:**
   - Implement server-side validation for all API inputs (per DESIGN.md Section 11.4).
   - Validate model file paths (no path traversal, must be within allowed directories).
   - Validate inference parameters (ranges).
   - Validate string lengths (model names, messages, system prompts).
   - Sanitize all user-provided strings before logging or displaying.

2. **LAN mode implementation:**
   - Add setting to toggle LAN mode.
   - When enabled: change llama-server bind address from `127.0.0.1` to `0.0.0.0`.
   - Generate a random bearer token (256-bit, base64-encoded).
   - Display warning dialog with security implications.
   - Display the token and server URL for sharing.
   - Add middleware to the API that checks `Authorization: Bearer <token>` for non-localhost requests.
   - CORS configuration: allow requests from LAN origins when LAN mode is active.

3. **Process security (desktop):**
   - llama-server is spawned with minimal environment variables.
   - No shell interpolation in process arguments.
   - Process stdout/stderr are captured for logging (not exposed to the UI directly).

4. **Web security:**
   - React auto-escapes output (no XSS from model responses by default).
   - Verify no `dangerouslySetInnerHTML` usage without sanitization.
   - Content-Security-Policy headers for the web app.

5. **Model file security:**
   - GGUF validation before loading.
   - Model file paths are never constructed from user input without validation.
   - Prevent loading files outside the model directory (unless explicitly imported).

### Commands to Run

```bash
cd desktop/
cargo test --manifest-path src-tauri/Cargo.toml  # Security-related tests

cd tests/
npm test                                          # API security tests
```

### Tests

- Path traversal attempts are rejected.
- Invalid parameter ranges are rejected.
- LAN mode toggle changes server binding.
- Bearer token authentication works (valid token accepted, invalid rejected).
- Localhost requests work without authentication.
- CORS headers are correct in LAN mode.
- Model names with special characters are sanitized.
- Very long inputs are truncated or rejected.

### Expected Result

All user-facing surfaces are hardened against common attacks. LAN mode works with authentication.

### Exit Criteria

- [ ] All inputs are validated.
- [ ] Path traversal is blocked.
- [ ] LAN mode works with bearer token authentication.
- [ ] Warning dialog is shown when enabling LAN mode.
- [ ] CORS is correctly configured.
- [ ] No XSS vulnerabilities in rendered model output.
- [ ] Process management is secure.

### Known Risks

- LAN mode security is basic (bearer token, no TLS). Users should be warned this is not suitable for untrusted networks.
- Model output could contain markdown that renders unexpectedly. Ensure proper rendering boundaries.

### NOT Implemented in This Phase

- No TLS for LAN mode (would require certificate management).
- No user accounts or multi-user authentication.

---

## PHASE 10 — Testing

### Objective

Implement comprehensive test suites across all platforms and components.

### Files/Modules to Create

```
tests/
├── api/
│   ├── health.test.ts                   # Health endpoint tests
│   ├── models.test.ts                   # Model API tests
│   ├── chat.test.ts                     # Chat completion tests
│   ├── streaming.test.ts               # SSE streaming tests
│   ├── conversations.test.ts           # Conversation API tests
│   ├── validation.test.ts              # Input validation tests
│   └── security.test.ts                # Security tests (auth, CORS)
├── e2e/
│   ├── chat_flow.test.ts               # End-to-end chat flow
│   └── model_management.test.ts        # End-to-end model management
├── fixtures/
│   └── tiny-test.gguf                   # Minimal GGUF for testing (or download script)
├── package.json
├── vitest.config.ts
└── setup.ts                             # Test setup (start/stop server)

android/app/src/test/java/com/omnimind/
├── data/
│   ├── ModelRepositoryTest.kt
│   ├── ConversationRepositoryTest.kt
│   └── SettingsRepositoryTest.kt
├── domain/
│   ├── ValidateModelUseCaseTest.kt
│   ├── ChatTemplateFormatterTest.kt
│   └── ContextManagerTest.kt
└── native/
    └── LlamaEngineTest.kt              # JNI bridge tests (with mocks)

android/app/src/androidTest/java/com/omnimind/
├── data/
│   ├── DatabaseMigrationTest.kt
│   └── ModelImportTest.kt
├── ui/
│   ├── ChatScreenTest.kt
│   └── ModelManagerScreenTest.kt
└── native/
    └── JniBridgeInstrumentedTest.kt    # Tests with real model on device
```

### Dependencies

- All previous phases (all targets functional)

### Implementation Sequence

1. **Create test fixtures:**
   - Obtain or create a tiny GGUF model for testing (smallest possible model that llama.cpp can load).
   - Create mock data for conversations, models, settings.

2. **Implement API contract tests (`tests/api/`):**
   - Start a real llama-server with the test model.
   - Test each endpoint for correct response format, status codes, error handling.
   - Test streaming event format.
   - Test cancellation.
   - Test malformed requests.

3. **Implement E2E tests (`tests/e2e/`):**
   - Full chat flow: connect → load model → send message → receive response → verify persistence.
   - Model management: import → validate → load → unload → delete.

4. **Implement Android unit tests:**
   - Test repositories with in-memory Room database.
   - Test use cases with mocked dependencies.
   - Test chat template formatting.
   - Test context management.

5. **Implement Android instrumentation tests:**
   - Test database operations on real SQLite.
   - Test model import with a real (small) GGUF file.
   - Test UI interactions with Compose testing framework.
   - Test JNI bridge with a real model on an ARM64 device/emulator.

6. **Implement desktop tests:**
   - Rust backend: test server manager, database, commands.
   - Frontend: test React components, hooks.

7. **Implement stress/edge-case tests:**
   - Low memory simulation.
   - Full disk simulation.
   - Very long messages.
   - Rapid send/cancel cycles.
   - Multiple model load/unload cycles.

### Commands to Run

```bash
# API tests (requires running server)
cd tests/ && npm test

# Android unit tests
cd android/ && ./gradlew test

# Android instrumentation tests
cd android/ && ./gradlew connectedAndroidTest

# Desktop Rust tests
cd desktop/ && cargo test --manifest-path src-tauri/Cargo.toml

# Desktop frontend tests
cd desktop/ && npm test

# Web frontend tests
cd web/ && npm test
```

### Tests

All tests defined in DESIGN.md Section 16.2.

### Expected Result

Comprehensive test coverage across all platforms. Tests are runnable in CI and locally.

### Exit Criteria

- [ ] API contract tests cover all endpoints.
- [ ] Streaming tests verify event format and ordering.
- [ ] Cancellation tests verify partial response preservation.
- [ ] Android unit tests pass.
- [ ] Android instrumentation tests pass on ARM64 device/emulator.
- [ ] Desktop Rust tests pass.
- [ ] Desktop frontend tests pass.
- [ ] Web frontend tests pass.
- [ ] E2E tests pass with a real model.
- [ ] Edge case tests (OOM, full disk, long messages) pass.

### Known Risks

- Testing with real models requires downloading GGUF files, which increases CI time and storage.
- Android instrumentation tests require a device or emulator with ARM64.
- E2E tests may be flaky due to timing issues with server startup.

### NOT Implemented in This Phase

- No performance regression tests (future).
- No fuzz testing (future).

---

## PHASE 11 — Packaging / Release

### Objective

Package the application for distribution: Android APK/AAB, desktop installers, web deployment bundle.

### Files/Modules to Create

```
scripts/
├── package-android.sh                   # Build signed APK/AAB
├── package-desktop.sh                   # Build desktop installers
├── package-web.sh                       # Build web production bundle
└── release-checklist.md                # Pre-release checklist

.github/                                 # (Optional) CI/CD configuration
├── workflows/
│   ├── android.yml
│   ├── desktop.yml
│   ├── web.yml
│   └── test.yml

android/app/
├── proguard-rules.pro                   # ProGuard/R8 rules
└── keystore.properties                  # Signing configuration (gitignored)
```

### Dependencies

- All previous phases (tested, hardened application)

### Implementation Sequence

1. **Android packaging:**
   - Configure ProGuard/R8 rules to not strip JNI-referenced classes.
   - Set up release signing configuration.
   - Build release APK: `./gradlew assembleRelease`.
   - Build release AAB (for Play Store if desired): `./gradlew bundleRelease`.
   - Test release build on the target device.

2. **Desktop packaging:**
   - Configure Tauri build for each target platform.
   - Windows: produces `.msi` and/or `.exe` installer.
   - Linux: produces `.deb`, `.rpm`, `.AppImage`.
   - Verify sidecar binary is included in the package.
   - Test installers on clean machines.

3. **Web packaging:**
   - Build production bundle: `npm run build`.
   - Output is a static directory that can be served by any HTTP server.
   - Or: bundle with the desktop app and serve from the Tauri backend.

4. **Create release checklist:**
   - Version bump.
   - Changelog update.
   - All tests pass.
   - Build on all target platforms.
   - Test on clean machines.
   - Verify llama-server sidecar is bundled.
   - Verify license information is included.
   - Create GitHub release with artifacts.

5. **CI/CD (optional but recommended):**
   - GitHub Actions workflows for:
     - Running tests on push/PR.
     - Building Android APK.
     - Building desktop installers for Windows and Linux.
     - Building web bundle.

### Commands to Run

```bash
# Android
cd android/ && ./gradlew assembleRelease

# Desktop
cd desktop/ && npm run tauri build

# Web
cd web/ && npm run build
```

### Tests

- Release APK installs and runs on a real device.
- Desktop installer installs on a clean Windows/Linux machine.
- Desktop app launches, finds the bundled llama-server, and works end-to-end.
- Web build serves correctly from a static file server.
- ProGuard/R8 does not break JNI.

### Expected Result

Distributable packages for all three targets. Ready for v0.1 release.

### Exit Criteria

- [ ] Signed Android APK installs and works on the target device.
- [ ] Windows installer (.msi) installs and works.
- [ ] Linux package (.deb or .AppImage) installs and works.
- [ ] Web build is a static bundle that works against a running server.
- [ ] All sidecar binaries are bundled correctly.
- [ ] License information is included.
- [ ] Release checklist is documented.

### Known Risks

- Code signing (Android keystores, Windows code signing) requires credentials.
- Different Linux distributions may have different WebKitGTK versions.
- ProGuard/R8 obfuscation may break JNI; need careful keep rules.

### NOT Implemented in This Phase

- No auto-update mechanism (future).
- No Play Store / Microsoft Store submission (future).
- No macOS build (future).
