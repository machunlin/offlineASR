# Offline ASR

Java 11 + Spring Boot 2.7 的本机离线中文语音转文字服务。模型放入 `models/SenseVoiceSmall/` 后运行平台对应的启动脚本，浏览器访问 `http://127.0.0.1:8080`。

- 浏览器录音上限 60 秒，页面显示倒计时，到时自动停止；服务端也限制解码音频时长。
- 支持在网页上传 WAV、MP3、M4A、AAC、FLAC、OGG、WebM 等 FFmpeg 可解码音频文件；上传文件与麦克风录音共用 `POST /api/asr` 接口。
- 默认最大上传 50 MB、音频时长 60 秒，可通过 `ASR_MAX_UPLOAD_MB` 和 `ASR_MAX_DURATION_SECONDS` 调整。
- 默认监听 `127.0.0.1:8080`，只允许本机访问。只有明确需要局域网访问时才设置 `ASR_HOST=0.0.0.0`，并配置防火墙。
- 日志写入 `logs/offline-asr.log`，按天滚动并保留 15 天；不记录音频内容和完整识别文本。
- 原始音频和解码后的 PCM 文件统一保存在 `temp/`，文件名格式为 `yyyyMMddHHmmss.扩展名`，例如 `20261126163025.wav`。文件按文件系统记录的创建时间保留 4 小时；后台每 10 分钟扫描并删除创建时间超过 4 小时的普通文件，不递归删除子目录。可通过 `ASR_TEMP_RETENTION_HOURS` 和 `ASR_TEMP_CLEANUP_INTERVAL_MS` 调整保留时长与扫描间隔。
- 接口：`POST /api/asr`（multipart 字段 `file`）和 `GET /api/asr/health`。
- 浏览器录音排障：WebM 文件可正常解析但没有声音时，应检查解码后的 PCM RMS/峰值；两者均为 0 表示录到了数字静音，按 requestId 检查 Chrome 麦克风权限、输入设备及系统麦克风状态，详见 [开发协作与代码提交规范](docs/开发协作规范.md)。
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

自动化测试和 Windows CI 构建不能替代客户主机上的 JNI、Chrome 麦克风、文件上传、断网启动和性能验收。