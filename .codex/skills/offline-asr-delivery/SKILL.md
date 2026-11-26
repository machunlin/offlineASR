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

## Collaboration and Git rules

- Before every commit, fetch the latest remote branch and inspect its current HEAD. Never commit against a stale base. If the remote branch advanced, incorporate the latest changes first, resolve conflicts without discarding user changes, then re-run the relevant checks.
- Commit each completed, independently verifiable implementation step. Use a concise Chinese commit message. Do not push to or modify master, force-push, merge, or perform destructive history rewrites unless explicitly authorized.
- Write logs, commit messages, documentation, and code comments in Chinese wherever practical. Keep Java/JavaScript identifiers, API names, protocol fields, and standard technical terms in their conventional English form.
- When modifying scripts, provide the complete executable script in the handoff, not isolated fragments.

## Verification order

1. Run focused tests before changing behavior; for every new behavior write a failing test first.
2. Run the complete build with Java 11, the project Maven repository and clean test package goals.
3. For macOS model-path changes, run the packaged JAR with a verified local model and prove both /api/asr/health and a multipart WAV request.
4. Record real results and unverified Windows items in docs/测试报告.md; never infer hardware, microphone, or offline-mode results without a target-machine run.

## Windows delivery

- scripts/windows-package.bat intentionally refuses to package without the complete Windows native JAR.
- scripts/windows-start.bat validates JAR, Java 11 Runtime, model, tokens, FFmpeg, and config before startup.
- WinSW requires matching names: scripts/offline-asr.exe and scripts/offline-asr.xml. Validate foreground startup before installing a service.
- For browser recordings, do not assume a valid WebM container means usable speech. Inspect the decoded PCM RMS and peak; RMS=0 and peak=0 means the recording contains digital silence. Correlate the requestId with both the upload log and the subsequent decode-statistics log. Check Chrome microphone permission, selected input device, operating-system microphone privacy/mute state, and MediaStreamTrack muted/readyState before blaming FFmpeg or SenseVoice.
- On Windows run scripts/windows-generate-checksums.ps1 followed by scripts/windows-verify-package.ps1 before the first start. Static package verification does not replace an actual JNI, microphone, service, or offline run.
- The six Chinese documents in docs/01-* through docs/06-* are delivery artifacts. Keep commands, expected results, rollback, and safe deletion boundaries accurate when scripts change.

## Maintenance

When a new verified issue materially changes build, model, deployment, or recovery decisions, update this Skill and the corresponding evidence document in the same change. Do not turn one-off network instability into a universal rule.

## Verified branch guardrails（2026-11-26）

These findings are for commit `b8ee67c` and must be rechecked after fixes:

- The requirement baseline is still 600 seconds unless the project owner formally changes it. Do not treat the branch's
  60-second frontend/backend defaults as the requirement without an approved requirement change.
- Always run Maven with `clean` after switching between `macos-arm64` and `windows-x64`. A non-clean default-profile
  package after a Windows build reused stale target content and failed with `UnsatisfiedLinkError: no sherpa-onnx-jni`.
- As of this commit, `scripts/mac-start.sh` is not executable and has an unclosed quote on line 3. Run `bash -n` and
  attempt the actual script before claiming script deployment works.
- Unit tests with `MockMultipartFile` do not cover Servlet-level multipart failures. Verify real HTTP requests for
  non-multipart, missing `file`, oversized upload, invalid media and the configured duration boundary.
- As of this commit, the real HTTP contract returned 500 for missing multipart and oversized upload, and 422 for invalid
  media against the required 400/413/415 statuses. One oversized request also produced repeated full exception stacks.
  Treat this as unresolved until integration tests prove otherwise.
- `DefaultFfmpegProcessLauncher` currently discards stderr and the timeout path does not wait for forced termination. A
  field diagnosis must not claim FFmpeg root cause from exit code alone.
- On Windows, `deploy/windows-x64/config/application.yml` still overrides the packaged default. Check the effective
  property under the exact foreground and WinSW startup paths; do not trust a health response or a single YAML file.
- `windows-package.bat` does not currently copy every script listed in the delivery manual, and
  `windows-verify-package.ps1` only enforces a minimum manifest count. Run the actual package script and assert the
  complete asset set before marking a Windows package complete.

## Additional verification sequence for this branch

1. `bash -n scripts/mac-start.sh` and actual `./scripts/mac-start.sh` startup.
2. `mvn clean test package`, then `node scripts/verify-js-contract.mjs`.
3. Start the packaged JAR and send real HTTP valid, missing-file, oversized, invalid-media and duration-boundary
   requests.
4. Inspect `temp/` after each request and sample the log for one-line controlled errors rather than repeated stacks.
5. Run `./scripts/verify-stage-three.sh`; remember it ends with the Windows profile, so clean-build the macOS profile
   before any subsequent local JAR test.
6. For Windows work, separately verify the package script, checksum coverage, foreground startup and WinSW service
   startup with effective configuration readback.
