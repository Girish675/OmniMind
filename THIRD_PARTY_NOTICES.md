# Third-Party Notices and Licenses

OmniMind relies on several open-source components. This document provides notice of third-party software and licenses included or linked in the project.

---

## 1. llama.cpp

- **Website / Repository**: https://github.com/ggml-org/llama.cpp
- **License**: MIT License
- **Copyright**: (c) 2023-2026 Georgi Gerganov and llama.cpp contributors
- **Usage**: Core native inference engine and GGML tensor library.

```text
MIT License

Copyright (c) 2023-2026 Georgi Gerganov

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## 2. Android Jetpack & Kotlin Libraries

- **Website / Repository**: https://android.googlesource.com/platform/frameworks/support
- **License**: Apache License, Version 2.0
- **Libraries**:
  - AndroidX Core KTX
  - Jetpack Compose (UI, Foundation, Material 3, Navigation)
  - Lifecycle & ViewModel (Runtime, Compose, Viewmodel-KTX)
  - Room Persistence Library (Runtime, KTX, Compiler)
  - Kotlin Coroutines (Core, Android)

---

## 3. Reference Model: Qwen3-4B-GGUF

- **Repository**: https://huggingface.co/Qwen/Qwen3-4B-GGUF
- **License**: Apache License, Version 2.0
- **Creator**: Alibaba Cloud / Qwen Team
- **Notice**: Model weights are downloaded directly by users or imported locally. No weights are bundled in source distribution.
