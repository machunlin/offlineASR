# Linux x64 部署手册

适用目标：Ubuntu 22.04 LTS、x86_64、Java 11。当前不承诺 Linux ARM64。

## 1. 构建部署包

在可联网的 Linux x64 构建机上执行：

~~~bash
bash scripts/linux-package.sh
~~~

脚本下载并校验 sherpa-onnx v2.0.3 官方 Linux x64 原生 JAR，然后执行 Maven clean test package，生成 dist/linux-x64/。deploy/linux-x64/ 仅保存配置和发布说明模板，不是最终输出目录。

## 2. 补齐离线运行资产

将经许可核验的 Java 11 Linux x64 Runtime 解压到 dist/linux-x64/jdk/；将模型文件放入：

- dist/linux-x64/models/SenseVoiceSmall/model.int8.onnx
- dist/linux-x64/models/SenseVoiceSmall/tokens.txt

将 Linux x64 FFmpeg 可执行文件放入 dist/linux-x64/ffmpeg/bin/ffmpeg，并赋予执行权限：

~~~bash
chmod +x dist/linux-x64/ffmpeg/bin/ffmpeg
~~~

若不随包提供 Java 或 FFmpeg，必须明确客户机需要预装对应依赖，不能将其描述为完整离线包。

## 3. 生成并验证发布清单

~~~bash
cd dist/linux-x64
./scripts/linux-generate-checksums.sh
./scripts/linux-verify-package.sh
~~~

校验脚本会检查必要文件、清单中的 SHA-256、重复路径、非法路径及未纳入清单的文件。校验通过后再压缩整个目录交付；生成清单后不得修改或替换任何文件。

## 4. 部署与启动

将目录整体复制到目标机，例如：

~~~bash
sudo mkdir -p /opt/offline-asr
sudo cp -a dist/linux-x64/. /opt/offline-asr/
~~~

由客户授权管理员确认部署用户和目录权限。随后在目标机执行：

~~~bash
cd /opt/offline-asr
./scripts/linux-verify-package.sh
./linux-start.sh
~~~

启动脚本优先使用包内 jdk/bin/java 和 ffmpeg/bin/ffmpeg；不存在时才使用 JAVA_HOME、PATH 中的系统依赖。模型和临时文件路径使用部署目录绝对路径。

## 5. 验收

另开终端执行：

~~~bash
curl --fail http://127.0.0.1:8080/api/asr/health
~~~

确认 applicationStatus=READY、modelLoaded=true、ffmpegAvailable=true。随后使用已授权的真实音频执行 POST /api/asr，并在本机 Chrome 验证麦克风录音和文件上传识别。

健康检查成功不代表 JNI 已完成真实推理。断网验收应断开网络后重启服务，再重复真实音频识别。

## 6. 内存验收

~~~bash
free -h
ps -eo pid,rss,vsz,%mem,comm,args | grep '[j]ava'
~~~

至少记录模型加载完成后的空闲 RSS、连续识别峰值 RSS 和系统可用内存。2 GB 内存主机不应仅根据 JVM -Xmx 判断进程总内存。

## 7. systemd

offline-asr.service 是模板。安装前必须检查服务用户、工作目录、Java 路径和权限。未经客户授权，不得创建系统用户、写入 /etc/systemd/system/、启用服务或修改防火墙。

## 8. 安全边界

服务默认绑定 127.0.0.1:8080，当前没有用户认证，不应直接绑定公网或不可信局域网地址。
