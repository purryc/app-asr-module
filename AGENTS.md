# APP ASR MODULE

## Purpose
Reusable on-device speech recognition first; optional independent pen input second. Android is implemented. Other platforms have adaptation contracts and plans until built and tested. Preserve the original Lenovo Pen Lab prototype outside this repository.

## Layout and naming
- `modules/asr-core/src/main/java/`: Java-only lifecycle, draft state and public recognition contract; no Android, UI, pen or model dependency.
- `modules/asr-android/src/main/java/`: Android microphone and sherpa-onnx two-pass adapter; no pen/UI imports.
- `modules/asr-ui-android/src/main/java/`: optional native transcription panel.
- `modules/pen-android/src/main/java/`: independent MotionEvent-to-sample adapter; no ASR, microphone permission, model, logging or network.
- `examples/{asr,pen,combined}/`: independently buildable Android hosts. Shared diagnostic view only under `examples/shared/`.
- `models/manifest.json`: pinned upstream asset URLs, archive hashes, extraction mapping and file hashes. Downloaded `models/assets/` and `.deps/` stay ignored.
- `tools/`: Python 3 dependency preparation, module/APK builds, validation and device checks. No global tool installation or automatic push.
- `tests/`: Java behavioral tests and synthetic fixtures only; no user voice/transcripts.
- `docs/`: Chinese-first API, architecture, platform adaptation, accuracy evaluation and bounded verification evidence.
- `third-party/`: notices and provenance only; downloaded third-party binaries stay outside Git.
- `build/`, `.cache/`, `qa/`, signing keys and device captures are disposable/local-only and ignored. Preserve accepted prototype versions outside this repository; never commit raw device logs.
Use lowercase-kebab-case filenames except Java conventions. Update this file before changing the structure.

## Module boundaries
- ASR and pen must compile/package independently; verify pen APK has no RECORD_AUDIO permission or ASR models/runtime, and ASR APK has no pen classes.
- No recognition UI in the ASR engine. Host owns runtime permission, lifecycle and user intent.
- Capture, streaming decoding, and final decoding use separate serial executors. Bounded session duration, no user audio files, network or hidden background capture.
- Draft and final events identify their phrase. Final results must not clear a newer draft or overwrite user edits.
- Stop flushes; cancel suppresses stale callbacks and stops microphone; close is idempotent and releases native resources after queued work. Calls from UI thread only for Android adapter.
- SenseVoice re-decodes original phrase audio; no invented text correction. Do not label two-pass as guaranteed more accurate.
- Pen values are raw units unless their API units are known; declaration and physical observation are distinct. Never invent continuous height from binary distance.
- Never include credentials, private transcripts, original device captures, local absolute paths or signing keys in Git.
- This task authorizes initial GitHub creation and push for `app-asr-module`. Later pushes require a user request.

## Verification
Run pure Java behavioral tests, dependency-boundary tests, compile all three demo variants, verify signatures and APK contents. Install ASR/pen standalone demos on the test tablet when available and run synthetic native instrumentation for ASR. Synthetic input proves code paths, not physical pen capability or real speech accuracy. Keep physical/runtime/compile-only/platform-plan evidence separate.
