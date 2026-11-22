# NobodyGenerator — Project Proposal

**Status:** Draft for approval · **Branch:** `experimental` · **Date:** 2026-11-22

## 1. What it is

NobodyGenerator turns a written script into speech. It uses neural text-to-speech to produce a voice with human-level clarity, natural rhythm, breathing pauses and intonation that still *reads as a machine*. You write the words; the machine does the talking.

The name is the design rule: **the voice belongs to nobody.** Every voice is built by blending and shaping synthetic voice embeddings, not by cloning a real person. That gives you a distinct, ownable "computer" voice with no impersonation risk, which matters if this ends up in a public-facing assistant.

A second goal is to be the **voice layer for a Jarvis-style live assistant** later on. From day one, the engine streams audio sentence by sentence and runs as a local service, so an LLM or a command system can send it text and get speech back with low latency.

## 2. Platform priority

| Priority | Platform | Role |
|---|---|---|
| 1 | **Arch Linux** | Primary target. Packaging (PKGBUILD/AUR) is designed here first |
| 2 | **CachyOS** | **Reference test platform: every release is tested on CachyOS running the Ryoku shell** |
| 3 | **Fedora** | Supported after Arch/CachyOS are stable (RPM spec, `dnf` deps) |

Windows and macOS are not goals.

### Reference test environment: CachyOS + Ryoku shell
- Wayland session (Hyprland or niri) with Ryoku's Quickshell desktop
- Audio through **PipeWire** (with `pipewire-pulse`). NobodyGenerator plays through PipeWire directly and makes no assumptions about X11
- CachyOS's x86-64-v3/v4 optimised packages mean the CPU path should run at least as fast as the numbers below

## 3. Engine choice

**Core: Kokoro-82M (ONNX build)**, Apache-2.0 licensed.

Why:
- **Quality:** natural prosody, among the best open TTS models of its size
- **Speed:** runs on CPU via ONNX Runtime with no GPU required. *Measured in the build sandbox:* 5.7 s of speech generated in 4.2 s on **2 CPU cores**, so faster than real time even on weak hardware. A desktop CPU should be several times faster
- **54 built-in synthetic voices** (American/British English plus other languages), and **voice blending works**: mixing embeddings creates a new voice that belongs to nobody. Verified in the sandbox
- Small footprint: about 330 MB of model files, downloaded once to `~/.local/share/nobodygenerator/`
- Licence permits commercial use

The engine sits behind a **pluggable interface**, so a more expressive model, or GPU acceleration through ONNX Runtime ROCm/CUDA, can be added later without changing scripts or integrations.

## 4. Making it "clearly not human": the Nobody Filter

A post-processing DSP chain with a single `--synthetic 0.0–1.0` intensity dial. At `0` you get clean neural speech; as it goes up, the voice becomes increasingly, deliberately machine:

- **Harmonic shimmer:** a subtle ring modulation/sub-harmonic layer (the classic "AI" sheen)
- **Formant shift:** moves the vocal character slightly outside the human range
- **Pitch quantisation:** a light glide-to-grid effect, much gentler than full autotune
- **Spectral gating/band shaping:** a faint "transmitted" quality
- **Short metallic room:** places the voice "inside" the machine

Presets (for example `jarvis`, `terminal`, `ghost`, `clean`) bundle the voice blend, speed and filter settings together.

Optionally, each file can carry an embedded metadata tag marking it as synthetic.

## 5. Script format

Plain text works as-is. Optional inline directives:

```
[voice: nobody-01] [speed: 0.95] [synthetic: 0.4]
Good evening. All systems are nominal.
[pause: 600ms]
I've taken the liberty of running diagnostics.
[voice: terminal]
Diagnostics complete.
```

Output: WAV/FLAC/OGG files, or live playback.

## 6. Architecture

```
script / text ─▶ parser ─▶ sentence chunker ─▶ engine (Kokoro ONNX) ─▶ Nobody Filter ─▶ sink
                                                                                     ├─ file (wav/flac/ogg)
                                                                                     ├─ PipeWire playback
                                                                                     └─ stream (socket/HTTP)
```

Components:
1. **`nobody` CLI:** `nobody say "text"`, `nobody render script.txt -o out.flac`, `nobody voices`, `nobody blend`
2. **Python library:** `from nobodygenerator import Nobody`, with a `speak_stream()` generator for assistants
3. **`nobodyd` daemon:** a systemd **user** service on a local Unix socket, plus optional localhost HTTP. It keeps the model warm, so a later assistant gets sub-second time-to-first-audio
4. **Voice lab:** blend and save named voices (`~/.config/nobodygenerator/voices/*.toml`)

**Stack:** Python 3.12+, `onnxruntime`, `kokoro-onnx`, `espeak-ng` (phonemiser), `numpy`/`scipy` (DSP), `soundfile`, PipeWire playback via `sounddevice` or `pw-play`.

## 7. Packaging & install

- **Arch/CachyOS:** a `PKGBUILD` (AUR-ready). System deps: `espeak-ng`, `pipewire`, `python`. Python deps are vendored into an isolated venv under `/opt` or installed via `uv`/`pipx`
- **Fedora:** an RPM spec at a later milestone (`dnf install espeak-ng pipewire-utils`)
- First run downloads the model files, with checksum verification
- A systemd user unit for `nobodyd`

## 8. Ryoku / desktop integration (later milestone)

- A Hyprland/niri keybind for "speak selection/clipboard" (`wl-paste | nobody say -`)
- An optional Quickshell widget for Ryoku: speaking indicator, mute, preset switch
- Desktop notifications can be spoken (opt-in)

## 9. Branch & release policy

- **`experimental`:** every experimental/pre-release build, *only*. This proposal lives here
- **`main`:** full releases *only*. Nothing lands on `main` until it has passed CachyOS + Ryoku release testing
- Versioning: `0.x.0-exp.N` tags on `experimental`; `vX.Y.Z` tags on `main`
- I handle the merges (`experimental` → `main`) once a release is signed off

## 10. Testing

- **Automated (done by me in the build environment):** unit tests for the parser and DSP, a synthesis smoke test, a real-time-factor benchmark, and checks of audio output against expected length and level
- **Release testing (done by you on CachyOS + Ryoku):** each release ships a short checklist. Feedback I'll need:
  1. Did install succeed? (paste any error)
  2. Does `nobody say "test"` play through your speakers?
  3. The real-time factor printed by `nobody bench`
  4. Subjective rating 1–5: realism, and how "clearly machine" each preset sounds
  5. For the daemon: time-to-first-audio printed by `nobody bench --daemon`

## 11. Milestones

| Version | Branch | Deliverable |
|---|---|---|
| 0.1.0-exp | experimental | CLI: text/script → WAV/FLAC, built-in voices, voice blending |
| 0.2.0-exp | experimental | Nobody Filter + presets, live PipeWire playback |
| 0.3.0-exp | experimental | Streaming API + `nobodyd` daemon (systemd user service) |
| 0.4.0-exp | experimental | PKGBUILD, first-run model download, `nobody bench` |
| **1.0.0** | **main** | First full release after CachyOS + Ryoku sign-off |
| 1.1+ | experimental → main | Fedora RPM, Ryoku Quickshell widget, keybinds, GPU path |

## 12. Open questions for you

1. **Accent/base character:** British (Jarvis-like) or American as the default "nobody" voice?
2. **GPU:** AMD or NVIDIA on the test machine? This decides which acceleration path goes in first
3. **Assistant brain (future):** a local LLM, a cloud API, or undecided? This only affects the daemon's API shape
