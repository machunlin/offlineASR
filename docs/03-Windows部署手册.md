# Windows 部署手册

部署前核验：Windows x64、16 GB 内存、Chrome、USB 麦克风、Java 11 Runtime、Visual C++ Runtime、FFmpeg、模型及官方 `sherpa-onnx-native-lib-win-x64-2.0.3.jar`。每个二进制和模型必须记录来源、版本、SHA-256。

解压部署包后，确认 `app/`、`jdk/`、`models/`、`ffmpeg/`、`config/`、`logs/`、`scripts/` 完整。在命令提示符运行 `scripts\windows-start.bat`，浏览器打开 `http://127.0.0.1:8080`，再执行 `scripts\windows-status.bat`。预期 health 中 `applicationStatus` 为 `READY`。断网后重启并以麦克风录制一段语音，记录结果和日志。

本仓库尚未在目标 Windows 主机执行这些步骤，必须由现场实机验收。
