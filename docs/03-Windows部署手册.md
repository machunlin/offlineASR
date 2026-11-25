# Windows x64 部署手册

> 面向技术支持工程师。所有命令应在管理员命令提示符执行；未在目标机实测的项不得标记为通过。

## 1. 部署前条件

| 项目 | 要求 | 检查方式 | 正常结果 |
|---|---|---|---|
| 系统 | Windows x64 | systeminfo | 64 位系统 |
| 内存 | 推荐 16 GB | 任务管理器 → 性能 | 记录可用内存 |
| CPU | 客户机实际型号 | wmic cpu get name | 保存型号，不承诺统一性能 |
| 浏览器 | Chrome | 打开 Chrome | 可访问 localhost |
| 麦克风 | USB 麦克风 | Windows 设置 → 声音 | 有输入电平 |
| Java | 打包 Java 11 x64 Runtime | jdk\bin\java.exe -version | Java 11 |
| FFmpeg | Windows x64 FFmpeg | ffmpeg\bin\ffmpeg.exe -version | 显示版本 |
| VC++ | x64 Runtime | 应用和功能 | 已安装对应 Runtime |

## 2. 离线资产与校验

部署前在可联网构建机下载、记录 SHA-256；复制到离线主机后重新计算并比对。哈希不一致时停止部署。

| 资产 | 必需路径 | 要求 |
|---|---|---|
| 应用 JAR | app\offline-asr.jar | 使用 Windows profile 构建 |
| JVM API | 打入应用 JAR | sherpa-onnx-jvm-2.0.3.jar |
| 原生 JAR | lib\sherpa-onnx-native-lib-win-x64-2.0.3.jar | 官方 v2.0.3；严禁使用 macOS JAR |
| 模型 | models\SenseVoiceSmall\model.int8.onnx、tokens.txt | 已核验 SenseVoiceSmall INT8 |
| FFmpeg | ffmpeg\bin\ffmpeg.exe | Windows x64 |
| Java | jdk\ | Java 11 x64 Runtime |
| 服务包装器 | scripts\offline-asr.exe、offline-asr.xml | 可选；由 WinSW 重命名而来；记录版本与 SHA-256 |

PowerShell 校验示例：

    Get-FileHash .\models\SenseVoiceSmall\model.int8.onnx -Algorithm SHA256
    Get-FileHash .\lib\sherpa-onnx-native-lib-win-x64-2.0.3.jar -Algorithm SHA256

## 3. 构建 Windows 应用 JAR

完整且已校验的 Windows x64 原生 JAR 放入项目 lib 目录后，在构建机执行：

    set MAVEN_HOME=C:\tools\apache-maven-3.9.16
    scripts\windows-package.bat

预期出现 deploy\windows-x64\app\offline-asr.jar。脚本提示原生 JAR 缺失时，不能继续发布。

## 4. 目录与配置

部署根目录应包含：

    offline-asr-windows-x64/
    ├── app/offline-asr.jar
    ├── jdk/
    ├── models/SenseVoiceSmall/model.int8.onnx
    ├── models/SenseVoiceSmall/tokens.txt
    ├── ffmpeg/bin/ffmpeg.exe
    ├── config/application.yml
    ├── logs/
    └── scripts/

将 deploy\windows-x64\config\application.yml 复制为 config\application.yml。默认仅监听 127.0.0.1:8080、CPU 线程数 2、最大上传 50 MB、最大时长 600 秒。调整线程数前必须记录 CPU、内存和变更原因。

## 5. 前台启动与首次验收

    cd C:\offline-asr-windows-x64
    scripts\windows-start.bat

预期命令窗口保持运行。浏览器打开 http://127.0.0.1:8080；另开窗口执行：

    scripts\windows-status.bat

预期 JSON 的 applicationStatus 为 READY、modelLoaded 为 true、ffmpegAvailable 为 true、inferenceDevice 为 cpu。若不为 READY，先按常见故障排查处理。

## 6. Chrome、离线与服务验收

1. 在 Windows 隐私设置允许桌面应用使用麦克风。
2. Chrome 访问 localhost 页面并允许麦克风。
3. 完成短普通话录音；记录时间、文本、耗时、请求 ID 和健康检查。
4. 分别测试拒绝权限、USB 拔出、长录音和错误音频。
5. 断网后重启服务并重复录音；记录断网方式和实际结果。

前台验收通过后，如使用 WinSW，将已校验的 winsw.exe 重命名为 offline-asr.exe，与 offline-asr.xml 同目录放入 scripts，再执行：

    scripts\windows-install.bat
    sc query offline-asr
    scripts\windows-status.bat

卸载服务：

    scripts\windows-uninstall-service.bat

卸载服务不会删除模型、配置和日志。

## 7. 发布包静态自检

在客户机管理员 PowerShell 中，先生成并验证发布资产清单：

    cd C:\offline-asr-windows-x64
    powershell -ExecutionPolicy Bypass -File .\scripts\windows-generate-checksums.ps1
    powershell -ExecutionPolicy Bypass -File .\scripts\windows-verify-package.ps1

预期：自检脚本确认 JAR、Java、模型、FFmpeg、配置均存在，能启动 Java/FFmpeg 版本检查，且所有 checksums.sha256 条目一致。此检查通过不等于 Windows JNI、麦克风或服务运行已通过。

## 8. 当前验证边界

本项目已完成 macOS ARM64 的真实识别和可执行 JAR 验证。Windows 原生库、服务、USB 麦克风、VC++ Runtime、断网和性能均须在目标客户机按本手册实测并记录。
