# Read It To Me

An Android app that reads your documents aloud and gives you an MP3.

**Add a PDF, Markdown file, article link or text → hit Go → get notified → listen, save or share the MP3.**

- **Natural voices (optional, offline):** a one-time ~350 MB download of the Kokoro AI voices, which sound far more human than the phone's built-in ones. British and American accents (Kokoro has no Australian voice); British Emma is the default once installed. Runs entirely on the phone.
- Or your phone's built-in voices, Australian English by default
- Handles messy formatting: Markdown symbols, PDF line-wraps, headers and footers, and web page clutter are cleaned up before reading
- Runs in the background with a progress notification
- Built-in player with fine speed control (0.05× steps, 0.5×–3×), plus Save to any folder and Share

## Install

Open this repo's **[Releases](../../releases)** page on your phone and tap the latest `.apk`.
Every update is pushed straight to `main` and published as a new release.

If there's no Australian voice: **Settings → Accessibility → Text-to-speech → Speech Services by Google ⚙ → Install voice data → English (Australia)**.

## Building

GitHub Actions builds every push to `main`, runs the on-device tests on an emulator, and only publishes a release if they pass (see `.github/workflows/android.yml`). The build downloads the pinned sherpa-onnx library (hash-checked) on first run. Locally: Android Studio, or `gradle assembleRelease` with JDK 17 and the Android SDK/NDK.

## Licences

App code © Faraday Microsystems. Bundled [LAME](app/src/main/cpp/lame/VENDORED.md) MP3 encoder: LGPL. [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx): Apache-2.0. Natural voices: [Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M), Apache-2.0 (downloaded on the phone, not bundled).
