---
name: offline-asr-delivery
description: Build, test, package, or troubleshoot this repository's Java sherpa-onnx offline ASR service, especially its macOS ARM64 and Windows x64 delivery paths.
---

# Offline ASR Delivery

Use this skill for this repository only. Preserve the selected stack: Java 11, Spring Boot 2.7, sherpa-onnx v2.0.3, SenseVoiceSmall INT8, FFmpeg, CPU inference, and localhost-only access.

## Non-obvious constraints

- sherpa-onnx v2.0.3 Java JARs are official GitHub Release assets, not Maven Central artifacts. The project keeps verified JARs in lib; Maven uses systemPath deliberately and Spring Boot must retain includeSystemScope=true so the executable JAR contains those assets.
- Use SDKMAN Java at /Users/chunlin/.sdkman/candidates/java/current and Maven at /Users/chunlin/Documents/maven-3.9.16/bin/mvn. Use -Dmaven.repo.local=.m2 for normal builds so the user Maven cache is not modified.
- models, temp, .validation, .m2, and logs are ignored. Do not add model files, recordings, decoded PCM, or local build caches to Git.
- A domestic mirror may be used for large model downloads only after verifying the downloaded model file against an official published SHA-256. Record mirror URL, official source, size, hash, and date in docs/验证记录.md.
- Model files required at runtime are model.int8.onnx and tokens.txt. The model must be loaded once at application startup and reused for requests.
- The application always binds to 127.0.0.1; do not expand it to LAN access without explicit authorization.

## Platform assets

- macos-arm64 is the default Maven profile and may contain only sherpa-onnx-native-lib-osx-aarch64-2.0.3.jar.
- windows-x64 may contain only sherpa-onnx-native-lib-win-x64-2.0.3.jar. Never produce or call a Windows package complete until this JAR is fully downloaded and SHA-256-verified against the official v2.0.3 Release.
- A successful macOS build or recognition run is not evidence that Windows JNI/DLL loading, Visual C++ Runtime, USB microphone, Windows service, or target-machine performance works.

## Verification order

1. Run focused tests before changing behavior; for every new behavior write a failing test first.
2. Run the complete build with Java 11, the project Maven repository and clean test package goals.
3. For macOS model-path changes, run the packaged JAR with a verified local model and prove both /api/asr/health and a multipart WAV request.
4. Record real results and unverified Windows items in docs/测试报告.md; never infer hardware, microphone, or offline-mode results without a target-machine run.

## Windows delivery

- scripts/windows-package.bat intentionally refuses to package without the complete Windows native JAR.
- scripts/windows-start.bat validates JAR, Java 11 Runtime, model, tokens, FFmpeg, and config before startup.
- WinSW requires matching names: scripts/offline-asr.exe and scripts/offline-asr.xml. Validate foreground startup before installing a service.
- The six Chinese documents in docs/01-* through docs/06-* are delivery artifacts. Keep commands, expected results, rollback, and safe deletion boundaries accurate when scripts change.

## Maintenance

When a new verified issue materially changes build, model, deployment, or recovery decisions, update this Skill and the corresponding evidence document in the same change. Do not turn one-off network instability into a universal rule.
