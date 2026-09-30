# OmniMind

**A private, offline-first local LLM application for Android, Desktop, and Web.**

OmniMind is an open-source application that runs large language models entirely on your device. Model weights stay on your device, inference happens locally, and no data leaves your machine. It supports Android phones/tablets, Windows and Linux desktops, and a browser interface backed by a local inference server. Built on llama.cpp, OmniMind works with any GGUF-format model and ships with first-class support for Qwen3-4B as its initial reference model.

---

## Table of Contents

- [Goals](#goals)
- [Non-Goals](#non-goals)
- [Supported Platforms](#supported-platforms)
- [Privacy & Offline Principles](#privacy--offline-principles)
- [Architecture Overview](#architecture-overview)
- [Key Features](#key-features)
- [Supported Model Format](#supported-model-format)
- [Initial Reference Model](#initial-reference-model)
- [Device Requirements](#device-requirements)
- [Installation](#installation)
  - [Android](#android-installation)
  - [Desktop](#desktop-installation)
  - [Web Interface](#web-interface)
- [Model Management](#model-management)
  - [Importing Models](#importing-models)
  - [Downloading Models](#downloading-models)
- [Conversation Storage](#conversation-storage)
- [Network Access](#network-access)
- [Security Considerations](#security-considerations)
- [Development](#development)
  - [Prerequisites](#prerequisites)
  - [Build Commands](#build-commands)
  - [Test Commands](#test-commands)
- [Troubleshooting](#troubleshooting)
- [Roadmap](#roadmap)
- [License](#license)
- [Third-Party Licenses & Dependencies](#third-party-licenses--dependencies)
- [Known Limitations](#known-limitations)

---

## Goals

1. **Fully local inference** — all model execution happens on-device with no cloud dependency.
2. **Privacy by architecture** — conversations and model weights never leave the device unless the user explicitly exports them.
3. **Three user-facing targets** — native Android app, Tauri-based desktop app (Windows/Linux), and a browser interface connected to a local inference server.
4. **Single monorepo** — one repository for Android, desktop, web, shared libraries, and native inference code.
5. **GGUF model support** — load any GGUF-compatible model; not locked to a single model family.
6. **Practical for mid-range hardware** — designed and benchmarked for devices with 8 GB RAM and ARM64 CPUs.
7. **Streaming chat** — real-time token streaming with responsive UI during generation.
8. **Open source** — Apache-2.0 licensed application code.

## Non-Goals

1. **Cloud inference** — OmniMind does not send prompts to remote servers. There is no cloud inference backend.
2. **Model training or fine-tuning** — this is an inference-only application.
3. **Embedding a third-party consumer LLM app** — OmniMind is built from scratch; it does not wrap Ollama, LM Studio, Open WebUI, or similar products.
4. **Non-GGUF model formats** — initial releases support only GGUF. Other formats (e.g., safetensors, ONNX) are out of scope.
5. **Multi-user server deployment** — the application is designed for single-user, local use.
6. **Guaranteed GPU acceleration** — GPU backends are optional and depend on device capabilities. CPU inference is the baseline.

---

## Supported Platforms

| Platform | Technology | Status |
|----------|-----------|--------|
| **Android** | Kotlin + Jetpack Compose + llama.cpp (JNI) | ✅ Implemented & Built (`app-debug.apk`) |
| **Windows** | Tauri 2 + React/TypeScript + llama-server | Primary target (Phase 5–6) |
| **Linux** | Tauri 2 + React/TypeScript + llama-server | Primary target (Phase 5–6) |
| **macOS** | Tauri 2 + React/TypeScript + llama-server | Planned (not required for v1) |
| **Web/Browser** | React/TypeScript → local inference host | Primary target (Phase 5, 7) |

---

## Privacy & Offline Principles

- **Works completely offline**: All inference, conversation storage, and model management work without any network connection. You can put your device in airplane mode and use OmniMind.
- **Optional network functionality**: Downloading model files from Hugging Face or other repositories requires an internet connection. This is the *only* feature that uses internet access.
- **Local network functionality**: The desktop inference server and web interface communicate over `localhost` by default. LAN access is an optional, explicitly-enabled setting.
- **No cloud inference**: There is no remote inference endpoint. No API keys are needed. No data is sent to any server.
- **No telemetry**: OmniMind does not collect analytics, crash reports, or usage data. Logs are stored locally and never uploaded.
- **Model weights stay on-device**: Downloaded or imported GGUF files remain in local storage. They are never copied to or processed by external services.

---

## Architecture Overview

```
┌────────────────────────────────────────────────────┐
│                   User Interfaces                   │
│                                                     │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────┐  │
│  │   Android    │  │   Desktop    │  │    Web     │  │
│  │  (Compose)   │  │ (Tauri+React)│  │  (React)   │  │
│  └──────┬──────┘  └──────┬───────┘  └─────┬─────┘  │
│         │                │                │         │
│    JNI Bridge       Tauri Cmds      HTTP/SSE        │
│         │                │                │         │
│         │         ┌──────┴───────┐        │         │
│         │         │ llama-server │◄───────-┘         │
│         │         │ (localhost)  │                   │
│         │         └──────┬───────┘                   │
│  ┌──────┴──────┐         │                          │
│  │  llama.cpp  │         │                          │
│  │  (native)   │  ┌──────┴───────┐                  │
│  └──────┬──────┘  │  llama.cpp   │                  │
│         │         │  (native)    │                  │
│         │         └──────────────┘                  │
│  ┌──────┴──────────────────────────────────────┐    │
│  │            GGUF Model Files                  │    │
│  │         (local storage only)                 │    │
│  └──────────────────────────────────────────────┘    │
└────────────────────────────────────────────────────┘
```

- **Android**: Kotlin/Compose UI → JNI bridge → llama.cpp native library (in-process).
- **Desktop**: Tauri 2 + React UI → Tauri Rust commands → managed llama-server process → localhost HTTP.
- **Web**: React SPA → HTTP/SSE → locally running llama-server (user must start the inference host).

---

## Key Features

- **Chat interface** with streaming token output
- **Multiple conversations** with persistent history
- **Configurable generation parameters** (temperature, top-p, top-k, min-p, repetition penalty, etc.)
- **System prompt** customization
- **Model manager** — import, download, delete, rename, inspect models
- **Benchmark screen** — measure and compare inference performance
- **Stop/cancel generation** mid-stream
- **Regenerate** last response
- **Edit and resend** previous messages
- **Multiple quantization support** within the same model family
- **Crash-resilient conversation storage** — conversations are persisted incrementally
- **Local-only logging** with export capability

---

## Supported Model Format

**GGUF** (GPT-Generated Unified Format) is the only supported model format. GGUF files are self-contained, including model weights, tokenizer data, and metadata (architecture, context length, chat template, etc.).

OmniMind reads model metadata directly from the GGUF file to configure inference parameters and prompt templating automatically.

---

## Initial Reference Model

| Property | Value |
|----------|-------|
| **Model family** | Qwen3 |
| **Model** | Qwen3-4B |
| **Format** | GGUF |
| **Primary quantization** | Q4_K_M |
| **File size (Q4_K_M)** | ~2.5 GB |
| **Parameters** | 4.0B total (3.6B non-embedding) |
| **Architecture** | 36 layers, 32 Q-heads, 8 KV-heads (GQA) |
| **Native context length** | 32,768 tokens |
| **Extended context** | Up to 131,072 tokens (YaRN) |
| **Chat template** | Jinja2-based, `im_start`/`im_end` format |
| **Thinking mode** | Supported (`<think>` tags, togglable) |
| **License** | Apache-2.0 |
| **Repository** | [Qwen/Qwen3-4B-GGUF](https://huggingface.co/Qwen/Qwen3-4B-GGUF) |
| **Secondary test quantizations** | Q5_K_M, Q6_K, Q8_0 |

> **Note**: OmniMind is not limited to Qwen3. Any GGUF model that llama.cpp supports can be imported.

---

## Device Requirements

### Android

| Requirement | Minimum | Recommended |
|-------------|---------|-------------|
| **OS** | Android 10 (API 29) | Android 13+ |
| **RAM** | 6 GB | 8 GB+ |
| **Storage** | 8 GB free | 16 GB+ free |
| **CPU** | ARM64 (arm64-v8a) | Cortex-A78 or better |
| **GPU** | Not required (CPU-only) | Adreno 7xx+ (optional acceleration) |

**Reference device**: Motorola Edge 60 Stylus (Snapdragon 7s Gen 2, 8 GB RAM, 256 GB storage, Adreno 710, Android 15).

### Desktop

| Requirement | Minimum | Recommended |
|-------------|---------|-------------|
| **OS** | Windows 10 (64-bit), Ubuntu 20.04+ | Windows 11, Ubuntu 22.04+ |
| **RAM** | 8 GB | 16 GB+ |
| **Storage** | 8 GB free | 20 GB+ free |
| **CPU** | x86-64 with AVX2 or ARM64 | Modern multi-core |
| **GPU** | Not required | Vulkan-capable GPU (optional) |

### Model Size Considerations

Actual usable model size depends on:

- **Model quantization** — lower quantization = smaller file + less RAM.
- **Context size** — longer context requires more memory for the KV cache.
- **Android memory pressure** — the OS itself uses 2–4 GB of the total 8 GB.
- **Backend** — GPU offloading may use shared memory differently.
- **Runtime overhead** — inference engine, app UI, and OS services all consume RAM.

**Compatibility matrix** (approximate, for 8 GB device with Q4_K_M):

| Model Size | Context 2K | Context 4K | Context 8K | Context 32K |
|------------|-----------|-----------|-----------|-------------|
| ≤1B | ✅ Comfortable | ✅ Comfortable | ✅ Comfortable | ⚠️ Possible |
| 3–4B | ✅ Comfortable | ✅ Comfortable | ⚠️ Tight | ❌ Likely OOM |
| 7–8B | ⚠️ Tight | ⚠️ Very tight | ❌ Likely OOM | ❌ OOM |
| 13B+ | ❌ Unlikely | ❌ Unlikely | ❌ OOM | ❌ OOM |

> These are estimates. Run the built-in benchmark to determine actual viability on your device.

---

## Installation

### Android Installation

1. Download the APK from the [Releases](../../releases) page or build from source.
2. Enable "Install from unknown sources" if installing the APK directly.
3. Install the application.
4. On first launch, import or download a GGUF model file.
5. Select the model and start chatting.

> The app does not include bundled model weights. You must import or download a model separately.

### Desktop Installation

1. Download the installer for your platform from [Releases](../../releases):
   - Windows: `.msi` or `.exe` installer
   - Linux: `.deb`, `.AppImage`, or `.rpm`
2. Install and launch.
3. On first launch, import or download a GGUF model file.
4. The app automatically manages the local inference server.

### Web Interface

The web interface is a React single-page application that connects to a locally running inference host.

1. Start the inference server:
   ```bash
   # From the OmniMind repository
   cd scripts/
   ./start-server.sh --model /path/to/model.gguf
   ```
   Or use the desktop application, which can expose its inference server.
2. Open `http://localhost:8080` in your browser (or the port reported by the server).
3. The browser communicates with your local server via HTTP and Server-Sent Events (SSE).

> The web UI does NOT load model files in the browser. The browser is a thin client; all inference runs on the local server.

---

## Model Management

### Importing Models

- **Android**: Use the in-app file picker to select a `.gguf` file from device storage, downloads folder, or SD card. The app uses Android scoped storage APIs; no special file manager permissions are needed.
- **Desktop**: Use the in-app import dialog or drag-and-drop a `.gguf` file. The file can remain in its original location or be copied to OmniMind's model directory.
- **Web**: Upload through the web interface (files are sent to the local inference server, not to any cloud).

### Downloading Models

OmniMind provides an optional model download feature:

1. Browse a curated list of known-compatible models (included in the app).
2. Enter a Hugging Face model URL directly.
3. Download progress is shown in the UI.
4. Downloads support resume on interruption (where the server supports HTTP range requests).
5. Free-space is checked before starting a download.
6. SHA-256 checksum verification is performed when a checksum is known.

> **Network required**: Downloading models is the only feature that requires internet access.

---

## Conversation Storage

- Conversations are stored in a **local database** on the device/machine.
  - Android: Room/SQLite database in the app's private storage.
  - Desktop: SQLite database in the app's data directory.
  - Web: Conversations are stored server-side on the local inference host (SQLite). Browser `localStorage` is used only for UI preferences.
- Messages are persisted **incrementally** as they are generated, not only when generation completes.
- If the app crashes during generation, the conversation up to the last successfully written token batch is preserved.
- Conversations are never synced to any cloud service.

---

## Network Access

| Feature | Network Required | Type |
|---------|-----------------|------|
| Chat / Inference | ❌ No | Fully offline |
| Model import from local file | ❌ No | Fully offline |
| Conversation storage | ❌ No | Fully offline |
| Settings | ❌ No | Fully offline |
| Benchmarks | ❌ No | Fully offline |
| Model download from Hugging Face | ✅ Yes | Internet |
| Web UI ↔ Local server | ⚠️ Localhost | Local network |
| LAN access (optional) | ⚠️ LAN only | Local network |

There is **no cloud inference**, **no telemetry**, and **no remote API calls** in the default configuration.

---

## Security Considerations

- **Localhost binding**: The inference server binds to `127.0.0.1` by default. External connections are rejected.
- **LAN mode**: When explicitly enabled, the server binds to `0.0.0.0`. A clear warning is shown. Optional token-based authentication is available.
- **No arbitrary shell execution**: The API does not expose OS shell commands. Model names, file paths, and settings are validated and sanitized.
- **Path traversal protection**: File operations validate paths against allowed directories.
- **Model validation**: GGUF files are validated (magic bytes, metadata integrity) before loading.
- **Process isolation**: On desktop, the llama-server runs as a managed child process with restricted capabilities.
- **No API key for local use**: Local inference does not require authentication. Authentication is only relevant for LAN mode.

---

## Development

### Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| **Android Studio** | Latest stable | Android development, NDK, SDK |
| **Android NDK** | r26+ | Cross-compiling llama.cpp for ARM64 |
| **CMake** | 3.22+ | Native build system |
| **JDK** | 17+ | Android/Kotlin compilation |
| **Kotlin** | 2.0+ | Android app language |
| **Rust** | stable (latest) | Tauri backend |
| **Node.js** | 20 LTS+ | React frontend, build tooling |
| **npm** | 10+ | Package management |
| **Git** | 2.30+ | Source control |
| **Clang/GCC** | Recent | C/C++ compilation (Linux desktop) |
| **MSVC** | VS 2022+ | C/C++ compilation (Windows desktop) |

### Build Commands

```bash
# Clone with submodules (llama.cpp)
git clone --recurse-submodules https://github.com/user/OmniMind.git
cd OmniMind

# --- Android ---
cd android/
./gradlew assembleDebug        # Debug APK
./gradlew assembleRelease      # Release APK

# --- Desktop ---
cd desktop/
npm install
npm run tauri build             # Production build
npm run tauri dev               # Development mode

# --- Web ---
cd web/
npm install
npm run build                   # Production bundle
npm run dev                     # Development server

# --- Native (llama.cpp for host) ---
cd native/
cmake -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build --config Release
```

### Test Commands

```bash
# --- Unit tests ---
cd shared/
npm test

# --- Android ---
cd android/
./gradlew test                          # Unit tests
./gradlew connectedAndroidTest          # Instrumentation tests

# --- Desktop ---
cd desktop/
npm test                                # Frontend tests
cargo test --manifest-path src-tauri/Cargo.toml  # Rust tests

# --- Web ---
cd web/
npm test

# --- API integration tests ---
cd tests/
npm test                                # API / E2E tests
```

---

## Troubleshooting

### Model fails to load
- Verify the file is a valid `.gguf` file (not a partial download).
- Check available storage and RAM. Close other apps on Android.
- Try a smaller quantization (Q4_K_M instead of Q8_0).
- Check logs in the app's diagnostics screen.

### Slow generation / low tokens per second
- Reduce context size in settings.
- Use a smaller model or lower quantization.
- Close background apps to free RAM.
- On Android, avoid charging during inference (thermal throttling).
- Run the benchmark to compare backends.

### Desktop: inference server won't start
- Check that the llama-server binary exists in the expected location.
- Verify no other process is using the configured port.
- Check the app logs for error details.
- Try restarting the application.

### Web UI can't connect to server
- Ensure the inference server is running (`http://localhost:<port>/health`).
- Check that the browser URL matches the server's address and port.
- If accessing from another machine, verify LAN mode is enabled and check firewall rules.

### Out of memory on Android
- Use a smaller model (≤4B parameters with Q4_K_M for 8 GB devices).
- Reduce context size to 2048 or 4096 tokens.
- Force-stop other apps before loading a large model.
- Restart the device if memory is heavily fragmented.

### App crashes during generation
- The conversation is auto-saved. Reopen the app to continue.
- If crashes persist, try a different model or reduce context size.
- Export diagnostics and check for patterns.

---

## Roadmap

### v0.1 — Foundation (Phase 0 – Phase 3 Implemented & Verified)
- [x] Native inference core (llama.cpp pinned commit `931351ea5`, ARM64 NEON, dynamic chat templating)
- [x] Model manager (import, GGUF validation, SHA-256, metadata extraction, atomic operations)
- [x] Android native integration (JNI bridge, lifecycle, safe model import off UI thread)
- [x] Local chat storage (Room database, incremental 500ms auto-save, partial output preservation)
- [x] Android Material 3 UI MVP (Chat, Model Manager, Diagnostics, Settings, About)
- [x] Performance diagnostics screen (real token metrics, hardware/thermal warnings)
- [x] Unit test suites (Core C++ tests + Android JVM unit tests)
- [x] Android APK build verified (`app-debug.apk`)

### v0.2 — Desktop & Web
- [ ] Tauri desktop application
- [ ] Local inference server management
- [ ] Web interface
- [ ] Cross-platform model management

### v0.3 — Performance & Polish
- [ ] Benchmark subsystem
- [ ] Optional GPU acceleration testing
- [ ] Performance optimization
- [ ] Conversation export/import

### v0.4 — Hardening
- [ ] Security audit
- [ ] LAN mode with authentication
- [ ] Comprehensive error handling
- [ ] Extended testing

### Future
- [ ] macOS support
- [ ] Multimodal model support (vision models)
- [ ] Model conversion utilities
- [ ] Plugin/extension system
- [ ] Themes and UI customization

---

## License

OmniMind application code is licensed under the **Apache License 2.0**.

See [LICENSE](LICENSE) for the full text.

Model weights are distributed under their own licenses (e.g., Qwen3 is Apache-2.0). OmniMind does not bundle model weights.

---

## Third-Party Licenses & Dependencies

| Dependency | License | Purpose |
|-----------|---------|---------|
| [llama.cpp](https://github.com/ggml-org/llama.cpp) | MIT | Inference engine |
| [Qwen3-4B-GGUF](https://huggingface.co/Qwen/Qwen3-4B-GGUF) | Apache-2.0 | Reference model weights |
| [Tauri 2](https://tauri.app/) | Apache-2.0 / MIT | Desktop app framework |
| [React](https://react.dev/) | MIT | UI framework |
| [TypeScript](https://www.typescriptlang.org/) | Apache-2.0 | Language |
| [Jetpack Compose](https://developer.android.com/compose) | Apache-2.0 | Android UI toolkit |
| [Room](https://developer.android.com/training/data-storage/room) | Apache-2.0 | Android database |
| [SQLite](https://sqlite.org/) | Public domain | Database engine |
| [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) | Apache-2.0 | Async programming |
| [Vite](https://vitejs.dev/) | MIT | Frontend build tool |

All dependencies use permissive open-source licenses (MIT, Apache-2.0, or public domain).

---

## Known Limitations

1. **No cloud inference** — by design. This is a local-only application.
2. **GGUF only** — other model formats are not supported in the initial release.
3. **GPU acceleration is not guaranteed** — the Adreno 710 (Snapdragon 7s Gen 2) may or may not benefit from OpenCL/Vulkan acceleration. CPU inference on ARM64 is the verified baseline.
4. **Large context uses significant memory** — using 32K context with a 4B model on an 8 GB device is likely to cause out-of-memory conditions. Practical context sizes on mobile are 2K–8K tokens.
5. **No multi-user support** — the application is designed for single-user local use.
6. **No conversation sync** — conversations do not synchronize between devices.
7. **Model download requires internet** — the app is offline after model files are present, but obtaining models requires a one-time download.
8. **Android thermal throttling** — sustained inference on mobile devices will cause thermal throttling and reduced performance over time.
9. **macOS is not a v1 target** — the architecture supports macOS but it is not tested or guaranteed for the first release.
10. **No training or fine-tuning** — this is an inference-only application.
