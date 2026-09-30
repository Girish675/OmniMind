# OmniMind — Device Setup & Execution Guide (steps.md)

This document provides step-by-step instructions to build, configure, run, and troubleshoot **OmniMind** across all supported platforms:
1. **[Desktop (Windows & Linux)](#1-desktop-setup--execution-windows--linux)**
2. **[Android (Mobile / Motorola Edge 60 Stylus)](#2-android-setup--execution-motorola-edge-60-stylus--arm64)**
3. **[Web Interface (Localhost & Secure LAN Access)](#3-web-interface-setup--execution)**
4. **[Reference Model Acquisition & Preparation](#4-reference-model-acquisition--preparation)**
5. **[Running Automated Test Suites](#5-running-automated-test-suites)**
6. **[Troubleshooting & Diagnostics Quick Reference](#6-troubleshooting--diagnostics-quick-reference)**

---

## 1. Desktop Setup & Execution (Windows & Linux)

OmniMind Desktop runs an original React 19 + TypeScript interface with a supervised local inference host that manages the native `llama.cpp` inference engine.

### 1.1 Prerequisites
- **Node.js**: v20.0.0+ LTS (Node.js v24.14.0 is verified).
- **npm**: v10.0.0+.
- **Rust & Cargo** *(only required if building the native Tauri installer)*: `rustc --version` (stable 1.77+).
- **C/C++ Compiler**:
  - Windows: Visual Studio 2022 C++ Build Tools or MSVC.
  - Linux: GCC 11+ or Clang 14+, CMake 3.22+, `libwebkit2gtk-4.1-dev`, `libgtk-3-dev`.

### 1.2 Quick Start (Developer / Supervised Mode)
This launches the application in desktop supervised mode, auto-detecting an open port, starting the SQLite host, and launching the UI window:

```bash
# Clone the repository
git clone https://github.com/Girish675/OmniMind.git
cd OmniMind

# Install root dependencies
npm install

# Build the web bundle (outputs to web/dist/)
npm run web:build

# Launch the supervised desktop application
npm run desktop
```

### 1.3 Building the Standalone Desktop Installer (Tauri 2)
To compile a native `.msi` / `.exe` installer (Windows) or `.deb` / `.AppImage` (Linux):

```bash
cd desktop

# Install desktop workspace dependencies
npm install

# Build production desktop release
npm run tauri build
```
- Output on Windows: `desktop/src-tauri/target/release/bundle/msi/OmniMind_0.1.0_x64.msi`
- Output on Linux: `desktop/src-tauri/target/release/bundle/deb/omnimind_0.1.0_amd64.deb`

### 1.4 Loading a Model on Desktop
1. Create a `models/` directory in the project root:
   ```bash
   mkdir models
   ```
2. Copy your GGUF model file into `models/` (e.g., `qwen3-4b-q4_k_m.gguf`).
3. In the OmniMind Desktop UI, navigate to the **Models** tab.
4. Click **Import Model** to browse and register the file, or click **Load Model** directly from the list.

---

## 2. Android Setup & Execution (Motorola Edge 60 Stylus / ARM64)

OmniMind Android runs inference completely **in-process** on the mobile device using ARM64 NEON assembly, without relying on an external server or network connection.

### 2.1 Prerequisites
- **JDK**: Java Development Kit 17 or 21 (Android Studio bundled JBR is recommended).
- **Android SDK**: API Level 35 (Android 15) with platform-tools installed.
- **Android NDK**: Version `25.2.9519653` (r25b) or `r26+`.
- **CMake**: 3.22.1+.
- **Physical Device / Emulator**:
  - Target Reference: **Motorola Edge 60 Stylus** (Snapdragon 7s Gen 2, 8 GB RAM, Android 15).
  - Enable **Developer Options** and **USB Debugging** on the device.

### 2.2 Building the Android APK
Run the Gradle wrapper from the `android/` directory:

```powershell
# Windows (PowerShell)
cd c:\Girish\OmniMind\android
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug

# Linux / macOS
cd android/
export JAVA_HOME="/path/to/jdk-17-or-21"
./gradlew assembleDebug
```
- The compiled APK is generated at:  
  `android/app/build/outputs/apk/debug/app-debug.apk` (~62.4 MB including native ARM64 libraries).

### 2.3 Installing the App via ADB
Connect your phone via USB and run:

```bash
# Verify device connection
adb devices

# Install APK to device
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

### 2.4 Transferring Models to the Phone
You can transfer GGUF files to your phone using either ADB or the Android system file picker:

#### Method A: ADB Push (Fastest)
```bash
# Push model to the phone's Downloads directory
adb push qwen3-4b-q4_k_m.gguf /sdcard/Download/
```
Then, open OmniMind on the phone → Go to **Models** → Tap **Import Model** → Pick `qwen3-4b-q4_k_m.gguf` from Downloads. The app will validate magic bytes and copy it safely into app-private scoped storage.

#### Method B: In-App Document Picker
1. Download a GGUF model directly via Chrome on your phone.
2. Open OmniMind → Tap **Models** tab → Tap the floating `+` button.
3. Select the `.gguf` file using Android's system document picker.

### 2.5 Recommended Mobile Runtime Settings (8 GB RAM)
Open the **Settings** screen in OmniMind Android:
- **Backend**: Select `CPU (ARM64 NEON)` *(verified rock-solid baseline)*.
- **Thread Count**: Set to `4` *(matches the 4 Cortex-A78 performance cores on Snapdragon 7s Gen 2)*.
- **Context Size**: Set to `2048` tokens *(preserves RAM headroom and avoids Android OS low-memory termination)*.
- **Temperature**: `0.7`, **Top-P**: `0.9`.

---

## 3. Web Interface Setup & Execution

The web interface is a standalone Single Page Application (React 19 + TypeScript + Vite) that communicates with the local OmniMind host over HTTP and Server-Sent Events (SSE).

### 3.1 Localhost Mode (Default & Secure)
In this mode, the server only accepts connections from the local machine (`127.0.0.1`):

```bash
# From repository root
npm run server
```
- Open your browser to: **`http://localhost:8080`**
- The UI connects directly to the local API. No login or token is required.

### 3.2 Secure LAN Mode (Access from other Devices on your Wi-Fi)
To allow your phone, tablet, or another computer on your home Wi-Fi to use your desktop's inference engine:

```bash
# Launch server in LAN mode with mandatory Bearer Token authentication
node server/index.js --lan --auth "my-secure-access-token"
```

1. Look at your server console to find your machine's local IP (e.g., `192.168.1.50:8080`).
2. On your mobile or tablet browser, navigate to: `http://192.168.1.50:8080`.
3. You will see a security banner indicating **LAN Mode Active**.
4. Go to **Settings** in the web interface and enter your token (`my-secure-access-token`) in the **Bearer Token** field.
5. All requests will now authenticate securely. Other unauthorized devices on your network will be rejected with HTTP 401.

---

## 4. Reference Model Acquisition & Preparation

OmniMind does not bundle multi-gigabyte model weights inside git repositories. You must obtain a GGUF file once.

### 4.1 Recommended Reference Model
- **Model**: `Qwen/Qwen3-4B-GGUF`
- **File**: `qwen3-4b-q4_k_m.gguf`
- **File Size**: ~2.5 GB
- **License**: Apache-2.0

### 4.2 Downloading the Model

#### Option 1: Using curl
```bash
mkdir -p models
cd models
curl -L -O "https://huggingface.co/Qwen/Qwen3-4B-GGUF/resolve/main/qwen3-4b-q4_k_m.gguf"
```

#### Option 2: Using Hugging Face CLI
```bash
pip install huggingface-hub
huggingface-cli download Qwen/Qwen3-4B-GGUF qwen3-4b-q4_k_m.gguf --local-dir ./models --local-dir-use-symlinks False
```

### 4.3 Verifying the Model File
Verify the file integrity before loading:
```powershell
# Windows (PowerShell)
Get-FileHash .\models\qwen3-4b-q4_k_m.gguf -Algorithm SHA256

# Linux / macOS
sha256sum ./models/qwen3-4b-q4_k_m.gguf
```
*(OmniMind automatically verifies GGUF magic bytes `0x46475547` on load and import).*

---

## 5. Running Automated Test Suites

OmniMind includes comprehensive automated test suites covering all layers.

### 5.1 Local Server, API & Security Tests
Tests health endpoints, GGUF binary validation, model locking, SSE streaming deltas, cancellation via `POST /chat/stop`, LAN authentication rejection, CORS restrictions, and SPA static routing:

```bash
# Run 27-test automated Node suite
npm run test:server
```
*Expected Result: `pass 27, fail 0` in ~6.5 seconds.*

### 5.2 Android Unit Tests
Tests storage headroom calculators, state machines, entity transformations, and JNI mappings:

```powershell
# Windows (PowerShell)
cd android
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat test

# Linux / macOS
cd android
./gradlew test
```
*Expected Result: `BUILD SUCCESSFUL (56 actionable tasks: 56 up-to-date/passed)`.*

### 5.3 Web Production Build Verification
Verifies TypeScript compilation and Vite chunk bundling:

```bash
npm run web:build
```
*Expected Result: Built in under 1 second to `web/dist/`.*

---

## 6. Troubleshooting & Diagnostics Quick Reference

| Issue | Probable Cause | Corrective Action |
|---|---|---|
| **Port 8080 already in use** | Another process is using port 8080 | Start server with `--port <port>`: `node server/index.js --port 8090` |
| **Model fails to load: "Invalid magic"** | Incomplete or corrupted GGUF download | Re-download the GGUF file. Header must start with `GGUF` (`0x46475547`). |
| **Android Out Of Memory (OOM)** | Context size too high or background apps consuming RAM | In Android Settings, reduce context to `2048` tokens and close background apps. |
| **Mobile generation speed drops after 10 mins** | Thermal throttling on Snapdragon SoC | Normal mobile behavior. Allow the phone to cool; avoid fast charging while running inference. |
| **Web UI shows "401 Unauthorized"** | LAN mode active without valid token | Enter the Bearer token configured on the host into the Web **Settings** screen. |
| **Desktop shows "llama-server not found"** | Native binary not built or not in path | Ensure `llama-server` is in your system `PATH` or configured binary directory. |
| **CORS error in browser console** | Accessing host from an untrusted origin | Ensure the server was launched with `--lan` if accessing across a local subnet. |
