OfflineASR - Linux x64 离线部署包

目录说明：
- app/：Spring Boot 应用
- jdk/：可选的 Java 11 Runtime；正式离线交付时应放入经过许可核验的 Linux x64 Runtime
- models/SenseVoiceSmall/：SenseVoiceSmall INT8 模型
- ffmpeg/bin/：可选的 Linux x64 FFmpeg
- config/：应用配置
- logs/、temp/：运行日志和临时音频
- scripts/：完整性清单生成和校验脚本

构建机：
1. 在 Linux x64 构建机运行 bash scripts/linux-package.sh。
2. 将经许可核验的 Java 11 Runtime 放入 jdk/。
3. 将 model.int8.onnx 和 tokens.txt 放入 models/SenseVoiceSmall/。
4. 将 Linux x64 ffmpeg 可执行文件放入 ffmpeg/bin/ffmpeg，并赋予执行权限。
5. 执行 scripts/linux-generate-checksums.sh，再执行 scripts/linux-verify-package.sh。
6. 校验通过后，将整个目录压缩交付；不要在生成清单后再修改或替换任何文件。

目标机：
1. 解压到 /opt/offline-asr，确保部署用户对目录有读写权限。
2. 执行 ./scripts/linux-verify-package.sh。
3. 执行 ./linux-start.sh 前台启动并完成健康检查和真实音频识别。
4. 需要 systemd 时，先按部署手册检查服务用户、路径和权限，再由授权人员安装模板。

默认仅监听 127.0.0.1:8080。当前服务没有用户认证，不要直接暴露公网或不可信局域网。
若未随包提供 jdk/ 或 ffmpeg/bin/，目标机必须预装 Java 11 或 FFmpeg；这不属于完整离线交付包。
