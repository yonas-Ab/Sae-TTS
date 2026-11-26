# Sae TTS Benchmark

**Sae TTS Benchmark** is a native Kotlin + Jetpack Compose Android application for benchmarking offline neural Text-to-Speech (TTS) models on low-resource Android hardware (primary target: **TECNO Pop 8 / BG6**, Android 13 Go Edition, Unisoc T606 ARM64, 4 GB RAM, 720×1612).

It serves as the offline voice-engine evaluation tool for the **Sae** AI companion project using the official **sherpa-onnx** Android runtime (`OfflineTts`).

---

## Supported Offline TTS Models

All models are downloaded **on user request only** from official `k2-fsa/sherpa-onnx` GitHub Releases (never bundled in the APK):

1. **Model A — Kitten Nano v0.8 INT8 (`kitten-nano-en-v0_8-int8`)**
   - Archive: `https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kitten-nano-en-v0_8-int8.tar.bz2` (~24 MB)
   - Sample rate: `24000 Hz` • 8 English speakers (`Bella`, `Jasper`, `Luna`, `Bruno`, `Rosie`, `Hugo`, `Kiki`, `Leo`)
   - License: **Apache-2.0**

2. **Model B — Kokoro English v0.19 INT8 (`kokoro-int8-en-v0_19`)**
   - Archive: `https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-en-v0_19.tar.bz2` (~103 MB)
   - Sample rate: `24000 Hz` • 11 English speakers (`af`, `af_bella`, `af_nicole`, `af_sarah`, `af_sky`, `am_adam`, `am_michael`, `bf_emma`, `bf_isabella`, `bm_george`, `bm_lewis`)
   - License: **Apache-2.0**

3. **Model C — Supertonic-3 INT8 (`sherpa-onnx-supertonic-3-tts-int8-2026-05-11`)**
   - Archive: `https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2` (~129 MB)
   - Architecture: 66M flow-matching + ConvNeXt vocoder (`duration_predictor.int8.onnx`, `text_encoder.int8.onnx`, `vector_estimator.int8.onnx`, `vocoder.int8.onnx`)
   - Sample rate: `44100 Hz` • 10 preset character style vectors (`F1–F5`, `M1–M5`, reference-audio-independent)
   - Inline tags: `<laugh>`, `<breath>`, `<sigh>` • Configurable denoising steps (`GenerationConfig.numSteps`)
   - License: **OpenRAIL-M (Weights) / MIT (Code)**

---

## Architecture & Key Safeguards

- **Strict Single-Model Residency (`TtsEngineManager`)**: Protected by a `Mutex` so two models are never loaded simultaneously. Any active `OfflineTts` instance is explicitly released before loading another model.
- **Low-Memory Defaults**: `minSdk = 26`, `targetSdk = 36`, `arm64-v8a` native ABIs, default **2 CPU threads** (configurable `1–4` in Settings).
- **Native Audio Playback (`AudioPlayer`)**: Streams 16-bit PCM directly through Android `AudioTrack` with Play, Pause, Resume, Stop, Replay, Seek, and Volume controls.
- **Persistent History & Export (`BenchmarkDatabase`, `BenchmarkRepository`)**: Room SQLite stores objective metrics (`initTimeMs`, `isColdInit`, `timeToFirstAudioMs`, `synthesisTimeMs`, `audioDurationMs`, `realTimeFactor`, `rssBeforeMb`, `rssAfterMb`, `rssDeltaMb`) separately from subjective 1–5 evaluations, with **CSV** and **JSON** export via `FileProvider`.

---

## Model Storage & Download Verification

Models are stored in app-specific external storage (`getExternalFilesDir(null)`), requiring **no Android storage permissions**:

```text
Android/data/com.aistudio.saetts.mrxqkp/files/
├── models/
│   ├── kitten-nano-en-v0_8-int8/
│   ├── kokoro-int8-en-v0_19/
│   ├── supertonic-3-tts-int8/
│   └── shared/lexicon-en-us.txt        (optional)
├── audio_outputs/                      (saved PCM16 WAV outputs)
└── exports/                            (CSV / JSON benchmark reports)
```

Download pipeline (`ModelDownloadManager`):
1. Checks free storage via `StatFs` before starting.
2. Downloads to `.downloads/<model>.tar.bz2.part` with live MB, `%`, speed, and ETA updates.
3. Supports user cancellation and **HTTP `Range` resume** (`ETag`/`If-Range` guarded).
4. Validates BZip2 header (`BZh`) and minimum archive size before extraction.
5. Extracts using Apache Commons Compress to a `.staging-*` directory with strict canonical-path **Zip-Slip / path-traversal checks**.
6. Verifies all required model files (`*.onnx`, `voices.bin`/`voice.bin`, `tokens.txt`, `espeak-ng-data/` or lexicon) before atomically promoting the directory.

---

## Build & Device Installation

### Prerequisites
- Android SDK (API 26–36, NDK/ABI `arm64-v8a` support)
- JDK 11+ & Gradle (`gradle` CLI)
- Network access on first build so Gradle task `:app:downloadSherpaOnnxAar` fetches official `sherpa-onnx-1.12.36.aar` into `app/libs/`.

### Build Commands
```bash
# Run JVM & Robolectric unit tests
gradle :app:testDebugUnitTest

# Build debug APK
gradle :app:assembleDebug

# Build release APK (requires signing env vars if packaging for release)
gradle :app:assembleRelease
```

### Install on Device (TECNO Pop 8 / Android 13)
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Known Limitations & Verified Facts

1. **Batch Synthesis Only (No True Audio Streaming)**: `sherpa-onnx` `OfflineTts` returns the complete waveform at the end of synthesis rather than progressive PCM chunks. Consequently, `Time to First Audio (TTFA)` equals total `Synthesis Time`, and the app does not simulate fake chunked streaming.
2. **No Model-Exposed Phoneme Timestamps**: `sherpa-onnx` `OfflineTts` returns `GeneratedAudio(samples, sampleRate)` without phoneme alignment or duration timestamps. The **Phoneme & VRM Lip-Sync Lab** in Settings clearly reports this limitation and previews untimed text-level G2P -> VRM visemes (`aa`, `ih`, `ou`, `ee`, `oh`). For production VRM avatars on Unisoc T606, real-time audio RMS envelope animation during playback is recommended.
3. **No Categorical Emotion Conditioning API**: Neither Kitten Nano, Kokoro v0.19, nor Supertonic-3 exposes an emotion-ID parameter in `OfflineTts`. Emotional test sentences rely on punctuation/phrasing (plus Supertonic-3 inline cue tags `<laugh>`, `<breath>`, `<sigh>`).
4. **Memory Telemetry Scope**: `VmRSS` / `VmHWM` values are read directly from `/proc/self/status` when readable by the OS (`N/A` if restricted).
