# Offline ASR

Java 11 + Spring Boot 2.7 的本机离线中文语音转文字服务。模型放入 `models/SenseVoiceSmall/` 后运行平台对应的启动脚本，浏览器访问 `http://127.0.0.1:8080`。

- 浏览器录音上限 60 秒，页面显示倒计时，到时自动停止；服务端也限制解码音频时长。
- 支持在网页上传 WAV、MP3、M4A、AAC、FLAC、OGG、WebM 等 FFmpeg 可解码音频文件；上传文件与麦克风录音共用 `POST /api/asr` 接口。
- 默认最大上传 50 MB、音频时长 60 秒，可通过 `ASR_MAX_UPLOAD_MB` 和 `ASR_MAX_DURATION_SECONDS` 调整。
- 默认监听 `127.0.0.1:8080`，只允许本机访问。只有明确需要局域网访问时才设置 `ASR_HOST=0.0.0.0`，并配置防火墙。
- 日志写入 `logs/offline-asr.log`，按天滚动并保留 15 天。请求日志包含上传文件名、临时文件路径、采样率、音频时长、RMS/峰值/削波样本数、解码耗时和推理耗时；不记录音频内容和识别文本。
- 接口：`POST /api/asr`（multipart 字段 `file`）和 `GET /api/asr/health`。前端可调用 `await OfflineASR.recognize(audioBlob, filename)`，或监听 `asr-result`。
- Windows 现场部署和排障流程见 [Windows 现场部署与排障手册](docs/Windows现场部署与排障手册.md)。

## Build and automated checks

macOS ARM64 本机开发使用已安装的 JDK 11 和 Maven：

```bash
mvn clean test package
node scripts/verify-js-contract.mjs
```

Windows x64 发布包使用：

```powershell
mvn -Pwindows-x64 clean test package
```

GitHub Actions 在 Windows runner 上执行 Windows x64 Maven 测试/打包、PowerShell 脚本语法检查、原生库打包隔离检查和浏览器 JavaScript 合同测试。

## Deployment assets

Windows 部署包需要单独放入 Java 11 x64 Runtime、官方 sherpa-onnx v2.0.3 Windows x64 native JAR 构建出的应用 JAR、SenseVoiceSmall INT8 模型、tokens.txt 和 Windows FFmpeg。Windows 包不能包含 macOS ARM64 native library。

## Validation boundary

自动化测试、Windows CI 构建和 native JAR 静态打包检查均不能替代目标客户主机上的 JNI 加载、Chrome 麦克风、文件上传、断网启动、服务化运行及性能验收。现场验收项目见部署手册。
