# NobodyGenerator

An Android app that reads your documents aloud and gives you an MP3.

**Add a PDF, Markdown file, article link or text → hit Go → get notified → listen, save or share the MP3.**

- Australian English voice by default (uses your phone's built-in offline neural voices)
- Handles messy formatting: Markdown symbols, PDF line-wraps, headers and footers, and web page clutter are cleaned up before reading
- Runs in the background with a progress notification
- Built-in player, plus Save to any folder and Share

## Install

Open this repo's **[Releases](../../releases)** page on your phone and tap the latest `.apk`.
Experimental builds are marked *Pre-release*; full releases come from `main`.

If there's no Australian voice: **Settings → Accessibility → Text-to-speech → Speech Services by Google ⚙ → Install voice data → English (Australia)**.

## Building

GitHub Actions builds every push (see `.github/workflows/android.yml`). Locally: Android Studio, or `gradle assembleRelease` with JDK 17 and the Android SDK/NDK.

## Licences

App code © Faraday Microsystems. Bundled [LAME](app/src/main/cpp/lame/VENDORED.md) MP3 encoder: LGPL.
