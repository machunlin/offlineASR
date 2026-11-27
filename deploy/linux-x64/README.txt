OfflineASR - Linux x64 应用包

目录说明：
- app/：Spring Boot 应用；Fat JAR 仅含 sherpa-onnx Java Wrapper，不含 Native Runtime
- config/：应用配置
- logs/、temp/：运行日志和临时音频
- scripts/：SHA-256 清单生成和校验脚本
- LICENSE：OfflineASR 项目许可
- licenses/：第三方许可文本和来源材料
- third-party/：CycloneDX SBOM、Maven 依赖/许可证清单

本应用包不包含：
- Java Runtime/JDK
- sherpa-onnx JNI 和 ONNX Runtime Native
- SenseVoiceSmall 模型
- FFmpeg

构建机：
1. 安装 Java 11、Maven、Node.js 20、python3。
2. 运行 bash scripts/linux-package.sh。
3. 脚本只构建应用并组装应用包，不下载或复制第三方运行资产。
4. 生成 checksums.sha256 后不得再替换或修改包内文件。

目标机第一阶段（客户准备环境）：
1. 安装 Java 11，执行 java -version。
2. 下载 sherpa-onnx 2.0.3 linux-x64 Native JAR，扁平解压到 external/sherpa-onnx-native/，根层必须包含 libonnxruntime.so 和 libsherpa-onnx-jni.so。
3. 下载 SenseVoiceSmall INT8 2025-09-09，解压到 models/SenseVoiceSmall/，根层必须包含 model.int8.onnx 和 tokens.txt。
4. 安装 FFmpeg，执行 ffmpeg -version。
5. 版本、直接下载地址、SHA-256 和许可状态见 docs/08-第三方组件与许可证清单.md。

目标机第二阶段（部署应用）：
1. 解压到 /opt/offline-asr，确保服务用户对目录有读写权限。
2. 运行 ./scripts/linux-verify-package.sh。
3. 运行 ./linux-start.sh 前台启动，检查 GET /api/asr/health。
4. 成功判定：applicationStatus=READY、modelLoaded=true、ffmpegAvailable=true。

目标机第三阶段（验收）：
1. 使用真实音频调用 POST /api/asr。
2. 验证 Chrome 麦克风、文件上传和识别。
3. 断网重启后重复识别。
4. 需要 systemd 时，先获得授权并检查服务用户、路径和权限。

默认仅监听 127.0.0.1:8080。服务没有用户认证，不要直接暴露公网或不可信局域网。
SenseVoiceSmall 模型和 FFmpeg 仍有待确认的许可边界；不得宣称已完成商业再分发授权。
