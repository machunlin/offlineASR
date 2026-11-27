OfflineASR - Windows x64 应用包

目录说明：
- app/：Spring Boot 应用；Fat JAR 仅含 sherpa-onnx Java Wrapper，不含 Native Runtime
- config/：应用配置
- logs/、temp/：运行日志和临时音频
- scripts/：启动、诊断、状态、服务管理和完整性校验脚本
- LICENSE：OfflineASR 项目许可
- licenses/：第三方许可文本和来源材料
- third-party/：CycloneDX SBOM、Maven 依赖/许可证清单

本应用包不包含：
- Java Runtime/JDK
- sherpa-onnx JNI 和 ONNX Runtime Native
- SenseVoiceSmall 模型
- FFmpeg
- Microsoft Visual C++ Redistributable

构建机：
1. 安装 Java 11、Maven、Node.js 20。
2. 运行 scripts\windows-package.bat。
3. 脚本只构建应用并组装应用包，不下载或复制第三方运行资产。
4. 生成 checksums.sha256 后不得再替换或修改包内文件。

目标机第一阶段（客户准备环境）：
1. 安装 Java 11 x64，执行 java -version。
2. 下载 sherpa-onnx 2.0.3 win-x64 Native JAR，扁平解压到 external\sherpa-onnx-native\，根层必须包含 onnxruntime.dll 和 sherpa-onnx-jni.dll。
3. 下载 SenseVoiceSmall INT8 2025-09-09，解压到 models\SenseVoiceSmall\，根层必须包含 model.int8.onnx 和 tokens.txt。
4. 安装 FFmpeg，执行 ffmpeg -version。
5. 安装 Microsoft Visual C++ Redistributable x64。
6. 版本、直接下载地址、SHA-256 和许可状态见 docs\08-第三方组件与许可证清单.md。

目标机第二阶段（部署应用）：
1. 解压到固定目录，例如 C:\offline-asr。
2. 运行 scripts\windows-verify-package.ps1。
3. 运行 scripts\windows-start.bat，检查 http://127.0.0.1:8080/api/asr/health。
4. 成功判定：applicationStatus=READY、modelLoaded=true、ffmpegAvailable=true。

目标机第三阶段（验收）：
1. 使用真实音频调用 POST /api/asr。
2. 验证 Chrome 麦克风、文件上传和识别。
3. 断网重启后重复识别。
4. 需要 Windows 服务时，先完成前台验收并取得授权，再安装 WinSW。

默认仅监听 127.0.0.1:8080。服务没有用户认证，不要直接暴露公网或不可信局域网。
SenseVoiceSmall 模型和 FFmpeg 仍有待确认的许可边界；不得宣称已完成商业再分发授权。
