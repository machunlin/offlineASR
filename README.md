# Offline ASR

Java 11 + Spring Boot 2.7 的本机离线中文语音转文字服务。模型放入 `models/SenseVoiceSmall/` 后运行平台对应的启动脚本，浏览器访问 `http://127.0.0.1:8080`。

- 浏览器录音默认最长 600 秒；SDK 和服务端时长限制应保持一致，可通过配置项按业务需要调整。
- 支持在网页上传 WAV、MP3、M4A、AAC、FLAC、OGG、WebM 等 FFmpeg 可解码音频文件；上传文件与麦克风录音共用 `POST /api/asr` 接口。
- 默认最大上传 50 MB，可通过 `ASR_MAX_UPLOAD_MB` 调整。
- 默认监听 `127.0.0.1:8080`，只允许本机访问。只有明确需要局域网访问时才设置 `ASR_HOST=0.0.0.0`，并配置防火墙。
- 日志写入 `logs/offline-asr.log`，按天滚动并保留 15 天；不记录音频内容和完整识别文本。
- 原始音频和解码后的 PCM 文件统一保存在 `temp/`，文件名格式为 `yyyyMMddHHmmss.扩展名`。清理任务依据文件名中的创建时间戳判断保留期限，超过 4 小时删除普通文件，不递归删除子目录；该方式不依赖 macOS、Windows、Linux 文件系统对 creationTime 的不同实现。可通过 `ASR_TEMP_RETENTION_HOURS` 和 `ASR_TEMP_CLEANUP_INTERVAL_MS` 调整保留时长与扫描间隔。
- 接口：`POST /api/asr`（multipart 字段 `file`）和 `GET /api/asr/health`。
- 浏览器录音排障：WebM 文件可正常解析但没有声音时，应检查解码后的 PCM RMS/峰值；两者均为 0 表示录到了数字静音，按 requestId 检查 Chrome 麦克风权限、输入设备及系统麦克风状态，详见 [开发协作与代码提交规范](docs/开发协作规范.md)。
- 浏览器端 SDK 位于 `src/main/resources/static/sdk/offline-asr-sdk.js`，负责设备管理、麦克风检测、录音和识别请求。第三方接入方式见 [SDK 接入文档](docs/SDK接入文档.md)。
- `index.html` 是 SDK 功能演示页面，`microphone-test.html` 是独立麦克风诊断工具；第三方系统不应依赖 Demo 的页面脚本。
- 代码提交、中文日志/注释和变更验证约定见 [开发协作规范](docs/开发协作规范.md)。
- Windows 现场部署和排障流程见 [Windows 现场部署与排障手册](docs/Windows现场部署与排障手册.md)。

## 构建与自动化检查

```bash
mvn clean test package
node scripts/verify-js-contract.mjs
```

Windows x64 发布包：

```powershell
mvn -Pwindows-x64 clean test package
```

自动化测试和 三平台 CI 构建不能替代客户主机上的 JNI、Chrome 麦克风、文件上传、断网启动和性能验收。GitHub Actions 分别在 macOS ARM64、Ubuntu 22.04 x64、Windows x64 Runner 上执行 Maven 测试/打包、脚本语法检查、原生库打包隔离检查和浏览器 JavaScript 契约测试。

## Linux x64 部署

Ubuntu 22.04 x86_64 构建机执行 `bash scripts/linux-package.sh`，脚本会下载并校验官方 sherpa-onnx v2.0.3 Linux x64 原生 JAR，再运行 Maven 测试和打包，输出目录为 `deploy/linux-x64/`。客户机需要 Java 11、FFmpeg 和 SenseVoiceSmall INT8 模型；详细步骤见 `docs/04-Linux部署手册.md`。Linux 包不能包含 macOS 或 Windows 原生库。

## Windows 部署资产

Windows 部署包需要单独放入 Java 11 x64 Runtime、官方 sherpa-onnx v2.0.3 Windows x64 native JAR 构建出的应用 JAR、SenseVoiceSmall INT8 模型、tokens.txt 和 Windows FFmpeg。Windows 包不能包含 macOS ARM64 native library。

## 验证边界

自动化测试、Windows CI 构建和 native JAR 静态打包检查均不能替代目标客户 Windows 主机上的 JNI 加载、Chrome 麦克风、文件上传、断网启动、服务化运行及性能验收。现场验收项目见部署手册。

## 当前验证状态（2026-11-26）

分支 `fix/asr-windows-hardening` 已完成 macOS 本机 JAR、模型/JNI 推理、有效 WAV HTTP 识别、JavaScript 契约和 Windows CI 静态打包验证，但仍存在发布前待处理问题：macOS 启动脚本、HTTP 错误契约、超时处理、前端验收字段以及 Windows 服务/打包配置。具体已通过、失败和未验证项目见 [测试报告](docs/测试报告.md)、[优化调整建议报告](docs/优化调整建议报告.md) 和 [测试计划与用例](docs/测试计划与用例.md)。

自动化测试与 CI 结果不能代替客户目标 Windows 主机、Chrome 麦克风、断网运行或客户 CPU 性能验证。
