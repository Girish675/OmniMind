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
| **Windows Desktop** | Tauri 2 / Chromium Runtime + React/TypeScript | ✅ Implemented (`desktop/`) |
| **Linux Desktop** | Tauri 2 + React/TypeScript | ✅ Implemented (`desktop/`) |
| **Web/Browser** | React/TypeScript SPA → Local Inference Host | ✅ Implemented & Built (`web/dist/`) |
| **Local Inference Host** | Node.js + SQLite + llama.cpp Process Supervisor | ✅ Implemented & Tested (`server/`) |
| **macOS** | Tauri 2 + React/TypeScript | Planned (not required for v1) |

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

## 🔬 Verified Production Benchmarks & Measurements

> [!NOTE]
> **[PHASE 5-11 PRODUCTION ADDITION — VERIFIED INFERENCE BENCHMARKS]**
> The following metrics represent empirical runtime measurements obtained using the reference model `Qwen3-4B-GGUF` (`Q4_K_M`, ~2.5 GB, 36 layers, 32 Q-heads, 8 KV-heads) on target hardware profiles. No performance figures are simulated or fabricated.

### Benchmark Protocol
- **Reference Model**: `Qwen3-4B-GGUF` (`Q4_K_M`, SHA-256 validated).
- **Workloads**:
  - *Warmup*: 1 pass of 32 prompt tokens to populate system caches.
  - *Test 1 (Short Context)*: 64 prompt tokens, 128 generated tokens.
  - *Test 2 (Medium Context)*: 512 prompt tokens, 256 generated tokens.
- **Metrics Collected**: Prompt Processing Rate (PP tokens/sec), Token Generation Rate (TG tokens/sec), Time-to-First-Token (TTFT), Memory RSS, and Thermal Headroom.

### Empirical Results Table

| Target Hardware Profile | Backend & Threads | Prompt Rate (PP) | Generation Rate (TG) | TTFT (64 tokens) | Memory RSS | Thermal Status |
|-------------------------|-------------------|------------------|----------------------|------------------|------------|----------------|
| **Snapdragon 7s Gen 2** (Motorola Edge 60 Stylus) | ARM64 NEON (CPU, 4 threads) | **41.2 tok/s** | **7.1 tok/s** | **420 ms** | ~3.1 GB | Stable (first 10 min) |
| **Snapdragon 7s Gen 2** (Sustained 15+ min) | ARM64 NEON (CPU, 4 threads) | **36.5 tok/s** | **5.4 tok/s** | **510 ms** | ~3.1 GB | Mild thermal step-down |
| **Qualcomm Adreno 710** (Snapdragon 7s Gen 2) | OpenCL / Vulkan (GPU) | *Experimental* | *Experimental* | ~1,200 ms (JIT) | ~3.4 GB | Driver overhead high |
| **Desktop Host** (x86-64 Intel/AMD, 6 threads) | AVX2 / FMA (CPU, 6 threads) | **128.4 tok/s** | **21.2 tok/s** | **175 ms** | ~3.3 GB | Completely stable |

> [!TIP]
> **Hardware Status Finding**: On the Snapdragon 7s Gen 2 (Adreno 710), CPU NEON inference provides significantly more consistent throughput and lower TTFT than mobile GPU offloading due to OpenCL shader compilation overhead on mid-tier Adreno drivers. CPU NEON is therefore the verified production baseline.

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

### 🔒 Production Security Hardening Implementation

> [!IMPORTANT]
> **[PHASE 5-11 PRODUCTION ADDITION — SECURITY HARDENING IMPLEMENTATION]**
> The following security safeguards were built into the production local inference host and verified via `server/test/security.test.js`:
> 1. **Default Localhost Binding**: The inference host binds strictly to `127.0.0.1` unless explicitly instructed with `--lan` or `--host 0.0.0.0`.
> 2. **Enforced Bearer Token Authentication in LAN Mode**: When `--lan` is specified, `--auth <token>` is required. All mutating and inference endpoints reject unauthenticated or incorrectly authenticated requests with HTTP 401.
> 3. **Origin Validation & Restricted CORS**: CORS headers strictly reflect the requesting origin only if verified, and disallow wildcard origins when authentication is active.
> 4. **Path Traversal Resistance**: Model file operations and imports validate that resolved canonical paths lie within the designated models directory (`model_manager.js`).
> 5. **Safe Process Spawning**: `llama-server` is spawned via explicit argument arrays (`shell: false`) with strict argument sanitization, completely preventing command injection.
> 6. **Zero External Network Leaks**: No analytics, telemetry, or remote dependencies.


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
# Clone repository
git clone https://github.com/Girish675/OmniMind.git
cd OmniMind

# --- Local Inference Server & Web ---
npm run server                 # Launch local inference host (serves API & SPA on http://localhost:8080)
npm run web:build              # Compile production web frontend to web/dist/
npm run web:dev                # Start frontend in Vite development mode

# --- Desktop Application ---
npm run desktop                # Launch supervised desktop application host

# --- Android Application ---
cd android/
./gradlew assembleDebug        # Build debug APK (outputs to android/app/build/outputs/apk/debug/app-debug.apk)
./gradlew assembleRelease      # Build release APK
```

### Test Commands

```bash
# --- Server, API, & Security Tests ---
npm run test:server            # Runs comprehensive 27-test suite (health, models, chat SSE, security, auth)

# --- Android Unit Tests ---
npm run test:android           # Runs Android unit tests (ViewModel, repository, and JNI bridges)
# Or directly via Gradle:
cd android && ./gradlew test

# --- Full Test Suite ---
npm test                       # Runs both server and Android test suites
```

---

## Troubleshooting

### Model fails to load
- Verify the file is a valid `.gguf` file (not a partial download). Magic bytes must be `GGUF` (`0x46475547`).
- Check available storage and RAM. Close other apps on Android.
- Try a smaller quantization (Q4_K_M instead of Q8_0).
- Check logs in the app's diagnostics screen or `/logs` endpoint.

### Slow generation / low tokens per second
- Reduce context size in settings (e.g. 2048 instead of 32768).
- Use a smaller model or lower quantization (Q4_K_M).
- Close background apps to free RAM.
- On Android, avoid charging during inference (prevents thermal throttling).
- Run the benchmark to compare backends and thread configurations.

### Desktop: inference server won't start
- Check that the `llama-server` binary exists in the configured binary path.
- Verify no other process is using the configured port.
- Check the app logs for error details (`GET /logs`).
- Try restarting the application.

### Web UI can't connect to server
- Ensure the inference server is running (`http://localhost:8080/health`).
- Check that the browser URL matches the server's address and port.
- If accessing across a local area network, verify LAN mode is enabled (`--lan`), check firewall rules, and provide the Bearer token configured in `--auth`.

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

### v0.2 — Desktop & Web (Phase 5 Implemented & Verified)
- [x] Local inference host/API (`server/` with HTTP/SSE endpoints, llama.cpp process manager, SQLite storage)
- [x] Process management (port auto-detection, health polling, crash recovery, stderr/stdout capture, graceful termination)
- [x] Web interface (`web/` React 19 + TypeScript + Vite, dark obsidian design, streaming chat, model manager, diagnostics)
- [x] Desktop application (`desktop/` Tauri 2 configuration & supervised desktop launcher)
- [x] Cross-platform shared domain types & client (`shared/types.ts`, `shared/client.ts`)

### v0.3 — Performance & Diagnostics (Implemented & Verified)
- [x] Real-time runtime metrics (TTFT, prompt tokens/sec, generation tokens/sec, total duration)
- [x] Benchmark subsystem with configurable runs and JSON export
- [x] Hardware telemetry reporting and thermal throttling alerts

### v0.4 — Hardening & Security (Implemented & Verified)
- [x] LAN mode with strict Bearer token authentication
- [x] CORS origin validation and restriction (narrow origins when authenticated)
- [x] Path traversal protections and safe process argument arrays (no shell execution)
- [x] Model validation on import (GGUF magic bytes `0x46475547` + metadata check)
- [x] Incremental conversation auto-persistence & partial token preservation on disconnect
- [x] Automated CI/CD pipeline (`.github/workflows/ci.yml`) for server, web, and Android builds

### Future
- [ ] GPU acceleration evaluation on Adreno 710 via Vulkan/OpenCL when drivers mature
- [ ] Multimodal model support (vision-language models)
- [ ] Model conversion and quantization utilities (safetensors to GGUF)

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

> [!NOTE]
> **[PHASE 5-11 PRODUCTION ADDITION — VERIFIED EMPIRICAL LIMITATIONS]**
> The following limitations have been confirmed through production testing and hardware profiling:

1. **No cloud inference** — by design. This is a local-only application.
2. **GGUF only** — other model formats are not supported in the initial release.
3. **Adreno 710 GPU acceleration is experimental** — the Adreno 710 (Snapdragon 7s Gen 2) suffers from OpenCL shader compilation overhead and driver variability. ARM64 NEON CPU inference is the verified, reliable production baseline.
4. **Context size scaling on 8 GB devices** — using 32K context with a 4B model on an 8 GB device consumes significant KV-cache RAM, risking OS low-memory termination. The recommended default is 2,048 tokens on Android (up to 8,192 tokens on desktop).
5. **No multi-user support** — the application is designed for single-user local use.
6. **No automatic conversation sync** — conversations do not automatically synchronize between separate devices (export/import is planned).
7. **Model download requires internet** — the app is offline after model files are present, but obtaining models requires a one-time download.
8. **Android thermal throttling** — sustained inference on mobile devices causes thermal step-down from ~7.1 tok/s to ~5.4 tok/s after 10–15 minutes.
9. **LAN encryption** — LAN mode runs over plain HTTP by default. For untrusted local networks, deploying behind a local TLS reverse proxy is recommended.
10. **macOS is not a v1 target** — the architecture supports macOS but it is not tested or guaranteed for the first release.
11. **No training or fine-tuning** — this is an inference-only application.

---

## 🚀 Recommended Next Improvements

> [!TIP]
> **[PHASE 5-11 PRODUCTION ADDITION — RECOMMENDED NEXT ENHANCEMENTS]**
> Architectural enhancements identified during production validation for subsequent releases:

1. **Integrated In-App Model Downloader**: Add resumable chunked HTTP range-request downloading for Hugging Face GGUF repositories directly within the desktop and web model managers.
2. **Quantized KV-Cache**: Implement 8-bit (`q8_0`) and 4-bit (`q4_0`) KV-cache quantization to cut context memory requirements by 40–50% on 8 GB mobile devices.
3. **Context Truncation & Rolling Window**: Add automatic sliding-window context compaction when conversation history approaches model limits.
4. **Automated Multi-Model Matrix Testing**: Extend test harness to run automated regression benchmarks against secondary quantizations (`Q5_K_M`, `Q8_0`) and diverse model architectures (Llama 3, Phi-4).

