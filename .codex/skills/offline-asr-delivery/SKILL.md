---
name: offline-asr-delivery
description: Build, test, package, or troubleshoot this repository's Java sherpa-onnx offline ASR service, browser JavaScript SDK, and macOS ARM64 / Windows x64 / Linux x64 delivery paths.
---

# Offline ASR Delivery

Use this skill for this repository only. Preserve the selected stack: Java 11, Spring Boot 2.7, sherpa-onnx v2.0.3, SenseVoiceSmall INT8, FFmpeg, CPU inference, localhost-only access, and a browser SDK that is independent from the Demo DOM.

## Non-obvious constraints

- sherpa-onnx v2.0.3 Java JARs are official GitHub Release assets, not Maven Central artifacts. The project keeps verified JARs in lib; Maven uses systemPath deliberately and Spring Boot must retain includeSystemScope=true so the executable JAR contains those assets.
- Prefer JAVA_HOME and MAVEN_HOME environment variables. If unset, use PATH or discover the current SDKMAN installation; never hardcode a developer's personal absolute path. Use -Dmaven.repo.local=.m2 for normal builds so the user Maven cache is not modified.
- models, temp, .validation, .m2, and logs are ignored. Do not add model files, recordings, decoded PCM, or local build caches to Git.
- A domestic mirror may be used for large model downloads only after verifying the downloaded model file against an official published SHA-256. Record mirror URL, official source, size, hash, and date in docs/验证记录.md.
- Model files required at runtime are model.int8.onnx and tokens.txt. The model must be loaded once at application startup and reused for requests.
- The application always binds to 127.0.0.1; do not expand it to LAN access without explicit authorization.
- The requirement baseline for maximum recording/audio duration is 600 seconds unless the project owner explicitly approves a requirement change. Keep browser SDK, Demo, backend defaults and deployment configuration aligned.

## Browser SDK boundaries

- The reusable browser library is src/main/resources/static/sdk/offline-asr-sdk.js. It must not read or manipulate Demo DOM, must not depend on index.html/app.js, and must expose a stable documented API.
- index.html and app.js are Demo-only. All microphone enumeration, device selection, stream lifecycle, volume analysis, recording, silence protection and ASR HTTP requests must go through the SDK. Do not reimplement these capabilities in Demo code.
- microphone-test.html remains an independent diagnostic tool. It must not upload audio or call the ASR endpoint.
- Keep SDK API, event names, error codes, option defaults and docs/SDK接入文档.md synchronized. Add or update scripts/verify-sdk-contract.mjs whenever the public contract changes.
- Browser microphone access requires HTTPS or localhost and explicit user permission. Remote HTTPS pages calling a local ASR service require separate verification of CORS and browser Private Network Access; do not assume baseUrl alone solves it.
- The SDK must release MediaStream tracks, AudioContext, MediaRecorder, timers and devicechange listeners on release(). Do not leave a microphone open after page/component teardown.
- Do not set multipart Content-Type manually; the browser must supply its boundary. Preserve the current POST /api/asr multipart field name file and X-Request-Id correlation header.

## Platform assets

- macos-arm64 is the default Maven profile and may contain only sherpa-onnx-native-lib-osx-aarch64-2.0.3.jar.
- linux-x64 uses the official sherpa-onnx-native-lib-linux-x64-2.0.3.jar from the v2.0.3 GitHub Release. Download it with scripts/download-linux-native.sh and verify SHA-256 before building.
- windows-x64 may contain only sherpa-onnx-native-lib-win-x64-2.0.3.jar. Never produce or call a Windows package complete until this JAR is fully downloaded and SHA-256-verified against the official v2.0.3 Release.
- windows-x64 may contain only sherpa-onnx-native-lib-win-x64-2.0.3.jar. Never produce or call a Windows package complete until this JAR is fully downloaded and SHA-256-verified against the official v2.0.3 Release.
- A successful macOS build or recognition run is not evidence that Windows JNI/DLL loading, Visual C++ Runtime, USB microphone, Windows service, or target-machine performance works.

## Collaboration and Git rules

- Before every commit, fetch the latest remote branch and inspect its current HEAD. Never commit against a stale base. If the remote branch advanced, incorporate the latest changes first, resolve conflicts without discarding user changes, then re-run the relevant checks.
- Commit each completed, independently verifiable implementation step. Use a concise Chinese commit message. Do not push to or modify master, force-push, merge, or perform destructive history rewrites unless explicitly authorized.
- Write logs, commit messages, documentation, and code comments in Chinese wherever practical. Keep Java/JavaScript identifiers, API names, protocol fields, and standard technical terms in their conventional English form.
- When modifying scripts, provide the complete executable script in the handoff, not isolated fragments.

## Verification order

1. Run focused tests before changing behavior; for every new behavior write a failing test first.
2. Run node scripts/verify-js-contract.mjs after every SDK or Demo change. This includes the SDK public API, UMD/CommonJS loading, event subscription, Demo decoupling, microphone diagnostic page and documentation contract.
3. Run the complete build with Java 11, the project Maven repository and clean test package goals.
4. For macOS model-path changes, run the packaged JAR with a verified local model and prove both /api/asr/health and a multipart WAV request.
5. Record real results and unverified Windows items in docs/测试报告.md; never infer hardware, microphone, and offline-mode results without a target-machine run.

## Windows delivery

- scripts/windows-package.bat intentionally refuses to package without the complete Windows native JAR.
- scripts/windows-start.bat validates JAR, Java 11 Runtime, model, tokens, FFmpeg, and config before startup.
- WinSW requires matching names: scripts/offline-asr.exe and scripts/offline-asr.xml. Validate foreground startup before installing a service.
- For browser recordings, do not assume a valid WebM container means usable speech. Inspect the decoded PCM RMS and peak; RMS=0 and peak=0 means the recording contains digital silence. Correlate the requestId with both the upload log and the subsequent decode-statistics log. Check Chrome microphone permission, selected input device, operating-system microphone privacy/mute state, and MediaStreamTrack muted/readyState before blaming FFmpeg or SenseVoice.
- On Windows run scripts/windows-generate-checksums.ps1 followed by scripts/windows-verify-package.ps1 before the first start. Static package verification does not replace an actual JNI, microphone, service, or offline run.
- The six Chinese documents in docs/01-* through docs/06-* are delivery artifacts. Keep commands, expected results, rollback, and safe deletion boundaries accurate when scripts change.

## Maintenance

When a new verified issue materially changes build, model, deployment, browser SDK, or recovery decisions, update this Skill and the corresponding evidence document in the same change. Do not turn one-off network instability into a universal rule.

## Verified branch guardrails（2026-11-26）

These findings are for commit b8ee67c and must be rechecked after fixes:

- The requirement baseline is still 600 seconds. Do not treat the previous 60-second frontend/backend defaults as the requirement.
- Always run Maven with clean after switching between macos-arm64 and windows-x64. A non-clean default-profile package after a Windows build reused stale target content and failed with UnsatisfiedLinkError: no sherpa-onnx-jni.
- scripts/mac-start.sh was not executable and had an unclosed quote on line 3 at the time of the audit. Run bash -n and attempt the actual script before claiming script deployment works.
- Unit tests with MockMultipartFile do not cover Servlet-level multipart failures. Verify real HTTP requests for non-multipart, missing file, oversized upload, invalid media and the configured duration boundary.
- The real HTTP contract previously returned 500 for missing multipart and oversized upload, and 422 for invalid media against the required 400/413/415 statuses. Treat this as unresolved until integration tests prove otherwise.
- DefaultFfmpegProcessLauncher previously discarded stderr and the timeout path did not wait for forced termination. A field diagnosis must not claim FFmpeg root cause from exit code alone.
- On Windows, deploy/windows-x64/config/application.yml overrides the packaged default. Check effective properties under the exact foreground and WinSW startup paths.
- windows-package.bat did not copy every script listed in the delivery manual, and windows-verify-package.ps1 only enforced a minimum manifest count. Run the actual package script and assert the complete asset set before marking a Windows package complete.

## Additional verification sequence

1. bash -n scripts/mac-start.sh and actual ./scripts/mac-start.sh startup.
2. mvn clean test package, then node scripts/verify-js-contract.mjs.
3. Start the packaged JAR and send real HTTP valid, missing-file, oversized, invalid-media and duration-boundary requests.
4. Inspect temp/ after each request and sample logs for controlled one-line errors rather than repeated exception stacks.
5. Run ./scripts/verify-stage-three.sh; remember it ends with the Windows profile, so clean-build the macOS profile before any subsequent local JAR test.
6. For Windows work, separately verify package script, checksum coverage, foreground startup and WinSW service startup with effective configuration readback.


## Linux x64 delivery

- The supported Linux target is Ubuntu 22.04 x86_64 on Java 11. Linux ARM64 is not part of this delivery target.
- Run `bash scripts/linux-package.sh` on a Linux x64 build host. It downloads the official v2.0.3 native JAR, verifies the published SHA-256, runs `mvn -Plinux-x64 clean test package`, and creates deploy/linux-x64.
- Linux deployments require Java 11, FFmpeg, model.int8.onnx and tokens.txt. Keep the default bind address at 127.0.0.1. Do not expose the unauthenticated service to a public or LAN interface without a separately approved security design.
- The provided systemd unit is a template. Confirm the service account, Java path, deployment directory and file permissions on the target host before enabling it.
- The current 2 GB Ubuntu VM is a constrained validation target. Measure process RSS after model load and during repeated recognition before setting JAVA_OPTS or systemd memory limits. JVM -Xmx does not cap native sherpa-onnx memory.
- Temporary-file cleanup uses the yyyyMMddHHmmss filename timestamp, not BasicFileAttributes.creationTime(), because creation-time semantics vary across file systems and providers. Keep the single temp directory, 14-digit timestamp filenames and four-hour retention requirement.

## Cross-platform verification

- GitHub Actions must run independent macOS ARM64, Ubuntu 22.04 x64 and Windows x64 jobs.
- Always use `clean` when switching Maven platform profiles. Assert that each executable JAR contains only its matching sherpa-onnx native library.
- Run `bash -n` on all shell scripts. Keep PowerShell syntax checks on Windows.
- A green CI build proves compile, unit tests and static package contents only. It does not prove real microphone capture, native inference, service startup, offline operation or target-host memory.
