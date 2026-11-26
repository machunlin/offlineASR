Offline ASR - Linux x64

1. 目标环境：Ubuntu 22.04 x86_64、Java 11、FFmpeg。
2. 解压部署目录到 /opt/offline-asr。
3. 将 SenseVoiceSmall INT8 的 model.int8.onnx 和 tokens.txt 放入 models/SenseVoiceSmall/。
4. 确保 linux-start.sh 可执行：chmod +x linux-start.sh。
5. 前台验收：./linux-start.sh。
6. 健康检查：http://127.0.0.1:8080/api/asr/health。
7. 默认只监听 127.0.0.1；如需远程浏览器访问，需单独评估 ASR_HOST、TLS、认证和防火墙，不要直接暴露无认证服务。
8. 临时音频文件保存在 temp/，文件名 yyyyMMddHHmmss.扩展名，超过 4 小时清理，不递归删除子目录。
9. 2 GB 内存主机必须先测量模型加载后空闲 RSS 和连续识别峰值，再决定 JAVA_OPTS；不要仅凭 -Xmx 判断进程总内存。
