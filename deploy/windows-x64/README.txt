OfflineASR - Windows x64 离线部署包

目录说明：
- app/：Spring Boot 应用
- jdk/：Java 11 x64 Runtime
- models/SenseVoiceSmall/：SenseVoiceSmall INT8 模型
- ffmpeg/bin/：Windows x64 FFmpeg
- config/：应用配置
- logs/、temp/：运行日志和临时音频
- scripts/：启动、状态、服务管理和完整性校验脚本

构建机：
1. 在 Windows x64 构建机放入经校验的 Windows x64 native JAR 后运行 scriptswindows-package.bat。
2. 将经许可核验的 Java 11 x64 Runtime 放入 jdk/。
3. 将 SenseVoiceSmall INT8 模型放入 modelsSenseVoiceSmall。
4. 将 Windows x64 FFmpeg 放入 ffmpeginfmpeg.exe。
5. 检查目标机所需 Microsoft Visual C++ Redistributable x64；若需随包分发，必须先核对其再分发许可。
6. 执行 scriptswindows-generate-checksums.ps1，再执行 scriptswindows-verify-package.ps1。
7. 校验通过后压缩整个目录交付；生成清单后不得再替换或修改文件。

目标机：
1. 解压到固定目录，例如 C:offline-asr。
2. 执行 scriptswindows-verify-package.ps1。
3. 执行 scriptswindows-start.bat 前台启动并完成健康检查、麦克风和上传识别。
4. 需要 Windows 服务时，先完成前台验收，再按部署手册安装 WinSW。

lib 中的 sherpa-onnx JVM 和 Windows x64 原生依赖已打入 appoffline-asr.jar，不需要将原生 JAR 单独复制到客户目录。
默认仅监听 127.0.0.1:8080。当前服务没有用户认证，不要直接暴露公网或不可信局域网。
