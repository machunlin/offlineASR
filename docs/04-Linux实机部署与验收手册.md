# Linux x64 实机部署与验收操作手册

适用环境：Ubuntu 22.04 LTS、x86_64。本文配合 [Linux 部署手册](04-Linux部署手册.md) 使用，目标是验证客户在目标机自行准备外部依赖后，能够独立部署并完成真实语音识别。

## 1. 验收边界

本流程只验证应用及其运行依赖，不会自动替客户安装 Java、Native Runtime、模型或 FFmpeg，也不会自动创建系统用户、安装 systemd 服务、修改防火墙或断开网络。

验收分为：

1. 环境准备与文件校验。
2. 应用部署与健康检查。
3. 真实音频识别。
4. 断网重启与离线识别。
5. 浏览器麦克风和上传识别。
6. 性能与验收记录。

**服务启动成功不等于验收通过。** 至少需要完成一次真实音频识别；离线能力必须在断网并重启服务后再次识别成功。

## 2. 目标机基线检查

登录 Ubuntu 服务器后执行：

```bash
echo "===== 系统 ====="
uname -a
cat /etc/os-release

echo "===== CPU ====="
lscpu | grep -E 'Architecture|CPU\\(s\\)|Model name'

echo "===== 内存 ====="
free -h

echo "===== 磁盘 ====="
df -h

echo "===== Java ====="
java -version 2>&1 || true

echo "===== 端口 ====="
ss -lntp | grep ':8080' || true
```

确认：

- 系统为 Ubuntu 22.04 LTS。
- CPU 架构为 `x86_64`。
- 磁盘空间足以存放应用、模型、Native 文件、日志和临时音频。
- 8080 端口未被其他进程占用，或已明确调整服务端口。
- 记录 CPU 型号、内存容量和系统版本，作为性能测试的环境基线。

## 3. 安装基础工具

```bash
sudo apt update
sudo apt install -y curl unzip xz-utils bzip2 file python3
```

以上为验收辅助工具，不代表安装 ASR 运行依赖。

## 4. 准备外部运行依赖

依赖版本、下载地址和 SHA-256 以 [第三方组件与许可证清单](08-第三方组件与许可证清单.md) 为准。不要从非可信镜像下载，也不要跳过校验。

### 4.1 Java 11

下载 Temurin 11.0.33.1+1 Linux x64 JRE：

```bash
cd /tmp
wget -O temurin11.tar.gz \
  'https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.33.1%2B1/OpenJDK11U-jre_x64_linux_hotspot_11.0.33.1_1.tar.gz'

echo 'e32167b352a56497b0ac401409321c00948e0a4131d9d1f92abf0f901e7bf62d  temurin11.tar.gz' | sha256sum -c -

sudo mkdir -p /opt/temurin-java-11
sudo tar -xzf temurin11.tar.gz --strip-components=1 -C /opt/temurin-java-11

export JAVA_HOME=/opt/temurin-java-11
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

输出必须为 Java 11。建议将环境变量配置到实际运行服务的用户环境中；不要为了本次验收直接覆盖系统全局 Java。

### 4.2 sherpa-onnx Native Runtime

下载 Linux x64 sherpa-onnx 2.0.3 Native JAR：

```bash
cd /tmp
wget -O sherpa-native.jar \
  'https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.3/sherpa-onnx-native-lib-linux-x64-2.0.3.jar'

echo '42330fbcfe4f771386545397ebf8a8dd2176ff913a254331226c01ad87982e17  sherpa-native.jar' | sha256sum -c -

sudo mkdir -p /opt/offline-asr/external/sherpa-onnx-native
sudo unzip -j sherpa-native.jar 'sherpa-onnx/native/linux-x64/*' \
  -d /opt/offline-asr/external/sherpa-onnx-native

ls -lh /opt/offline-asr/external/sherpa-onnx-native
file /opt/offline-asr/external/sherpa-onnx-native/*.so
```

目录根层必须同时存在：

- `libonnxruntime.so`
- `libsherpa-onnx-jni.so`

该 Native JAR 已包含 JNI 和 ONNX Runtime Native，**不需要再单独下载 ONNX Runtime**。

### 4.3 SenseVoiceSmall INT8 模型

```bash
cd /tmp
wget -O sensevoice.tar.bz2 \
  'https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09.tar.bz2'

echo '7305f7905bfcf77fa0b39388a313f3da35c68d971661a65475b56fb2162c8e63  sensevoice.tar.bz2' | sha256sum -c -

sudo mkdir -p /opt/offline-asr/models/SenseVoiceSmall
sudo tar -xjf sensevoice.tar.bz2 \
  -C /opt/offline-asr/models/SenseVoiceSmall --strip-components=1

ls -lh /opt/offline-asr/models/SenseVoiceSmall
```

必须存在：

- `model.int8.onnx`
- `tokens.txt`

### 4.4 FFmpeg

使用第三方 FFmpeg-Builds 固定的 Linux x64 LGPL-only 构建：

```bash
cd /tmp
wget -O ffmpeg-linux.tar.xz \
  'https://github.com/FFmpeg-Builds/FFmpeg-Builds/releases/download/autobuild-2026-11-26-12-56/ffmpeg-n9.0.2-982-g836df4e858-linux64-lgpl-9.0.tar.xz'

echo '8a30403651be3fdcba2bbbd2cc036093517daa77cf6d7adda835a61569cf5afc  ffmpeg-linux.tar.xz' | sha256sum -c -

mkdir -p /tmp/ffmpeg-extract
tar -xJf ffmpeg-linux.tar.xz -C /tmp/ffmpeg-extract
find /tmp/ffmpeg-extract -type f -name ffmpeg
```

找到解压目录中的 `bin/ffmpeg` 后，配置其实际绝对路径，例如：

```bash
export ASR_FFMPEG=/实际解压目录/bin/ffmpeg
"$ASR_FFMPEG" -version
```

不要照抄示例路径。FFmpeg 构建来源、许可证和再分发义务见第三方组件清单；本项目不代客户分发 FFmpeg。

## 5. 部署应用包

从当前项目分支获取 Linux x64 应用包，解压到：

```text
/opt/offline-asr
```

应用目录至少应包含：

```text
/opt/offline-asr/
├── app/offline-asr.jar
├── config/application.yml
├── linux-start.sh
├── scripts/linux-verify-package.sh
├── models/SenseVoiceSmall/
├── external/sherpa-onnx-native/
├── logs/
└── temp/
```

应用包本身不包含 Java、模型、Native Runtime 或 FFmpeg。若应用包校验脚本要求的文件缺失，应先重新获取完整应用包，不要手工修改校验清单来绕过错误。

执行：

```bash
cd /opt/offline-asr
bash scripts/linux-verify-package.sh
```

确认校验通过。

## 6. 配置并启动服务

在启动服务的同一个 shell 会话中设置：

```bash
export JAVA_HOME=/opt/temurin-java-11
export PATH="$JAVA_HOME/bin:$PATH"
export ASR_MODEL_DIR=/opt/offline-asr/models/SenseVoiceSmall
export ASR_SHERPA_NATIVE_PATH=/opt/offline-asr/external/sherpa-onnx-native
export ASR_FFMPEG=/替换为实际路径/bin/ffmpeg
```

确认 FFmpeg 路径正确后启动：

```bash
cd /opt/offline-asr
chmod +x linux-start.sh
./linux-start.sh
```

首次验收使用前台启动，不要立即配置 systemd。这样可以直接观察 Java、JNI、模型初始化和 FFmpeg 相关日志。

默认监听地址为 `127.0.0.1:8080`。应用没有内置用户认证，不要未经安全评估暴露到公网或不可信局域网。

## 7. 健康检查与真实识别

另开一个 SSH 会话：

```bash
curl -i http://127.0.0.1:8080/api/asr/health
```

依据项目健康检查响应确认：

- HTTP 状态码为 200。
- `applicationStatus=READY`。
- `modelLoaded=true`。
- `ffmpegAvailable=true`。

准备一段已获授权的 WAV 测试音频，例如：

```text
/opt/offline-asr/test-data/test.wav
```

执行识别：

```bash
curl -sS -X POST \
  -F 'file=@/opt/offline-asr/test-data/test.wav' \
  http://127.0.0.1:8080/api/asr
```

成功响应应包含 `success=true` 和非空 `text`，并返回 `requestId`、`audioDuration`、`decodeTime`、`inferenceTime`、`totalTime` 等字段。

## 8. 自动验收脚本

仓库提供：

```text
scripts/linux-acceptance-test.sh
```

部署后执行：

```bash
cd /opt/offline-asr
chmod +x scripts/linux-acceptance-test.sh
scripts/linux-acceptance-test.sh /opt/offline-asr/test-data/test.wav
```

可通过环境变量覆盖部署目录和服务地址：

```bash
ASR_DEPLOY_DIR=/opt/offline-asr \
ASR_BASE_URL=http://127.0.0.1:8080 \
scripts/linux-acceptance-test.sh /path/to/test.wav
```

日志保存在 `logs/acceptance-YYYYMMDDHHMMSS.log`。脚本退出码为 0 表示自动检查没有失败项；如果有警告或跳过项，仍需人工确认。未传音频参数时不会执行真实识别，不能据此判定完整验收通过。

## 9. 断网重启验收

断网验收用于确认服务不依赖运行时外部网络。必须在维护窗口由服务器管理员操作，并通过云厂商控制台、带外管理或其他不会中断管理通道的方式控制网络。

步骤：

1. 在线状态下先完成健康检查和真实音频识别。
2. 记录网络状态、时间和服务进程。
3. 断开服务器外网访问，但保留本机回环网络。
4. 停止并重新启动 OfflineASR。
5. 确认健康检查重新达到 READY。
6. 使用同一段 WAV 文件再次识别。
7. 记录响应内容和时间，完成后恢复网络。

检查命令：

```bash
ps -ef | grep '[o]ffline-asr.jar'
ss -lntp | grep ':8080'
curl --fail http://127.0.0.1:8080/api/asr/health

curl -sS -X POST \
  -F 'file=@/opt/offline-asr/test-data/test.wav' \
  http://127.0.0.1:8080/api/asr
```

不要在远程 SSH 会话中直接执行 `ip link down`、删除默认路由或停止网络服务，以免失去服务器管理连接或影响其他业务。

## 10. 浏览器验收

在目标浏览器环境中检查：

- 页面可正常访问。
- 浏览器可获取麦克风权限。
- 可枚举并选择正确录音设备。
- 音量检测随输入变化。
- 录音、上传、识别结果展示正常。
- 慢网络下 Loading 状态正常。
- 上传失败时错误提示明确。

浏览器麦克风 API 通常要求安全上下文（HTTPS 或 localhost）。通过普通 HTTP 局域网 IP 访问时，麦克风权限可能被浏览器限制；应先检查浏览器安全上下文，不要直接归因于 ASR 服务故障。

## 11. 性能与稳定性记录

至少记录：

- CPU 型号、逻辑核心数、内存总量。
- 服务空闲时 RSS。
- 识别期间峰值 RSS。
- 测试音频格式、采样率、声道数和时长。
- `decodeTime`、`inferenceTime`、`totalTime`。
- 连续识别次数及是否出现错误。
- 服务运行时长和是否发生异常退出。

当前项目没有定义适用于所有客户硬件的统一性能门槛。先采集目标机数据，再结合客户并发、音频时长和响应时间要求制定验收阈值。

## 12. 验收记录模板

| 验收项目 | 结果 | 证据/备注 |
|---|---|---|
| Ubuntu 22.04 LTS x86_64 | 待验收 | |
| Java 11 | 待验收 | |
| 应用包 SHA-256 | 待验收 | |
| sherpa JNI 文件与加载 | 待验收 | |
| ONNX Runtime Native 文件与加载 | 待验收 | |
| SenseVoice 模型加载 | 待验收 | |
| FFmpeg 可执行 | 待验收 | |
| 健康检查 READY | 待验收 | |
| WAV 真实识别 | 待验收 | |
| 浏览器麦克风录音 | 待验收 | |
| 浏览器上传识别 | 待验收 | |
| 断网后服务重启 | 待验收 | |
| 断网后真实识别 | 待验收 | |
| 内存与耗时记录 | 待验收 | |

## 13. 常见问题定位

| 现象 | 优先检查 |
|---|---|
| Java 版本不正确 | `JAVA_HOME`、`PATH`、`java -version` |
| Native 文件缺失 | Native JAR 版本、平台架构、解压目录层级 |
| `UnsatisfiedLinkError` | JNI/ORT 文件、动态库依赖、文件权限、系统架构 |
| 模型未加载 | `ASR_MODEL_DIR`、`model.int8.onnx`、`tokens.txt` |
| FFmpeg 不可用 | `ASR_FFMPEG`、可执行权限、`ffmpeg -version` |
| 健康检查未 READY | 应用启动日志、Native 加载错误、模型初始化错误 |
| 识别接口失败 | HTTP 响应、应用日志、音频格式和 FFmpeg 解码结果 |
| 浏览器不能录音 | HTTPS/localhost 安全上下文、麦克风权限、设备选择 |

## 14. 安全与交付注意事项

- 使用已获授权的测试音频；不要将客户录音、密钥或现场日志提交到 Git。
- 默认只监听 `127.0.0.1`；未经认证和安全评估，不得暴露公网。
- systemd 服务、专用用户、防火墙和反向代理属于部署环境变更，实施前应取得客户授权。
- SenseVoice 模型许可和 FFmpeg LGPL 完整履行义务仍需按第三方组件清单处理；实机测试通过不代表许可证审查完成。
