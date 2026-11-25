Windows x64 部署包骨架：app/、jdk/、models/、ffmpeg/、config/、logs/、scripts/。

1. 在构建机放入经校验的 Windows x64 native JAR 后运行 scripts\windows-package.bat。
2. 将经过核验的 Java 11 x64 Runtime、SenseVoiceSmall INT8 模型、Windows x64 FFmpeg 和可选 WinSW 复制到相应目录。
3. 在 Windows 管理员 PowerShell 中运行 scripts\windows-generate-checksums.ps1，再运行 scripts\windows-verify-package.ps1。
4. 通过静态自检后，使用 scripts\windows-start.bat 前台验证；成功后才能安装 WinSW 服务。

lib 只能放入校验过的 sherpa-onnx JVM JAR 和 Windows x64 原生 JAR，不能混入 macOS dylib。当前未完成 Windows 实机验证。
