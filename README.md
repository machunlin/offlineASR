# Offline ASR

Java 11 + Spring Boot 2.7 的本机离线中文语音转文字服务。模型放入 `models/SenseVoiceSmall/` 后运行 `scripts/mac-start.sh`，浏览器访问 `http://127.0.0.1:8080`。

接口：`POST /api/asr`（multipart 字段 `file`）和 `GET /api/asr/health`。前端可调用 `await OfflineASR.recognize(audioBlob)`，或监听 `asr-result`。

模型、临时音频和日志不提交版本库；实际验证与限制见 `docs/验证记录.md`、`docs/测试报告.md`。

当前 macOS 可执行包为 `target/offline-asr-0.1.0-SNAPSHOT.jar`。其中仅包含 macOS ARM64 原生库；Windows 包必须在取得并校验完整的官方 Windows x64 原生 JAR 后独立组装和实机验证。

阶段三自动化验证命令：

```bash
JAVA_HOME=/Users/chunlin/.sdkman/candidates/java/current \
MAVEN_HOME=/Users/chunlin/Documents/maven-3.9.16 \
scripts/verify-stage-three.sh
```

该脚本执行 Java 配置、错误映射、上传限制、FFmpeg 命令/超时、临时文件、模型未就绪、REST JSON、JavaScript 结果派发和 Windows x64 包隔离验证。
