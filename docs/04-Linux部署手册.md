# Linux x64 部署手册

适用目标：Ubuntu 22.04 LTS x86_64。OfflineASR 应用包只包含应用文件，外部运行依赖由客户或技术支持工程师准备。

> 本文负责依赖准备和应用部署。完整的目标机验收步骤、自动验收脚本用法、断网重启验证及结果记录，参见 [Linux 实机部署与验收手册](04-Linux实机部署与验收手册.md)。

## 1. 应用包

应用包包含 `app/offline-asr.jar`、`config/application.yml`、启动脚本、校验脚本、`LICENSE`、`THIRD_PARTY_NOTICES.md`、第三方组件清单、Maven 依赖清单、SBOM、许可材料和 `checksums.sha256`。

应用包不包含 Java Runtime、sherpa-onnx Native Runtime、ONNX Runtime、SenseVoiceSmall 模型或 FFmpeg。

## 2. 第一阶段：准备运行环境

### 2.1 Java 11

推荐 Eclipse Temurin 11 JRE x64，版本 `11.0.33.1+1`。

1. 直接下载：
   https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.33.1%2B1/OpenJDK11U-jre_x64_linux_hotspot_11.0.33.1_1.tar.gz
2. SHA-256：`e32167b352a56497b0ac401409321c00948e0a4131d9d1f92abf0f901e7bf62d`
3. 校验：
   ~~~bash
   sha256sum OpenJDK11U-jre_x64_linux_hotspot_11.0.33.1_1.tar.gz
   ~~~
4. 解压到固定目录并设置 `JAVA_HOME`：
   ~~~bash
   export JAVA_HOME=/opt/temurin-java-11
   export PATH="$JAVA_HOME/bin:$PATH"
   ~~~
5. 验证：`java -version`。成功判定：输出包含 `version "11`。

### 2.2 sherpa-onnx Native Runtime

此处下载的单个 Linux x64 Native JAR 已同时包含 `libsherpa-onnx-jni.so` 和 `libonnxruntime.so`；不需要另外下载 ONNX Runtime。

1. 下载：
   https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.3/sherpa-onnx-native-lib-linux-x64-2.0.3.jar
2. SHA-256：`42330fbcfe4f771386545397ebf8a8dd2176ff913a254331226c01ad87982e17`
3. 校验：
   ~~~bash
   sha256sum sherpa-onnx-native-lib-linux-x64-2.0.3.jar
   ~~~
4. 将两个库扁平解压到目录根层：
   ~~~bash
   mkdir -p /opt/offline-asr/external/sherpa-onnx-native
   unzip -j sherpa-onnx-native-lib-linux-x64-2.0.3.jar \
     'sherpa-onnx/native/linux-x64/*' \
     -d /opt/offline-asr/external/sherpa-onnx-native
   ~~~
5. 确认目录根层同时存在 `libonnxruntime.so` 和 `libsherpa-onnx-jni.so`。
6. 可用 `ASR_SHERPA_NATIVE_PATH` 指定其他绝对路径。

### 2.3 SenseVoiceSmall INT8

1. 下载：
   https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09.tar.bz2
2. SHA-256：`7305f7905bfcf77fa0b39388a313f3da35c68d971661a65475b56fb2162c8e63`
3. 校验并解压：
   ~~~bash
   sha256sum sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09.tar.bz2
   tar -xjf sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09.tar.bz2
   mkdir -p /opt/offline-asr/models/SenseVoiceSmall
   cp sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09/{model.int8.onnx,tokens.txt} \
      /opt/offline-asr/models/SenseVoiceSmall/
   ~~~
4. 也可设置 `ASR_MODEL_DIR` 指向其他目录。

### 2.4 FFmpeg

1. 固定 LGPL-only 构建下载：
   https://github.com/FFmpeg-Builds/FFmpeg-Builds/releases/download/autobuild-2026-11-26-12-56/ffmpeg-n9.0.2-982-g836df4e858-linux64-lgpl-9.0.tar.xz
2. SHA-256：`8a30403651be3fdcba2bbbd2cc036093517daa77cf6d7adda835a61569cf5afc`
3. 解压后将包含 `ffmpeg` 的 `bin` 目录加入 `PATH`，或设置 `ASR_FFMPEG` 为绝对路径。
4. 验证：`ffmpeg -version`

## 3. 第二阶段：部署 OfflineASR

1. 解压应用包到 `/opt/offline-asr`，确保部署用户对目录有读写权限。
2. 执行：
   ~~~bash
   ./scripts/linux-verify-package.sh
   ~~~
3. 确认环境变量：
   ~~~bash
   export JAVA_HOME=/opt/temurin-java-11
   export ASR_SHERPA_NATIVE_PATH=/opt/offline-asr/external/sherpa-onnx-native
   export ASR_MODEL_DIR=/opt/offline-asr/models/SenseVoiceSmall
   export ASR_FFMPEG=/path/to/ffmpeg
   ~~~
4. 前台启动：
   ~~~bash
   ./linux-start.sh
   ~~~
5. 健康检查：
   ~~~bash
   curl --fail http://127.0.0.1:8080/api/asr/health
   ~~~
   成功判定：`applicationStatus=READY`、`modelLoaded=true`、`ffmpegAvailable=true`。

## 4. 第三阶段：验收

1. 使用已授权真实音频调用 `POST /api/asr`，确认非空识别结果。
2. 在本机 Chrome 验证麦克风权限、设备选择、录音和上传识别。
3. 断网后重启服务，重复真实音频识别。
4. 记录空闲 RSS、连续识别峰值 RSS 和可用内存。
5. `offline-asr.service` 是模板；安装前必须核对其中的 `JAVA_HOME`、`ASR_MODEL_DIR`、`ASR_SHERPA_NATIVE_PATH`、服务用户和目录权限。安装系统服务、创建用户或修改防火墙必须先获得客户授权。

## 5. 常见故障

| 现象 | 检查 | 处理 |
|---|---|---|
| Java 不是 11 | `java -version`、`JAVA_HOME`、`PATH` | 安装/切换 Java 11 |
| Native Runtime 缺失 | `ASR_SHERPA_NATIVE_PATH`、两个 `.so` 文件 | 重新下载/解压 linux-x64 Native JAR |
| 模型缺失 | `ASR_MODEL_DIR`、`model.int8.onnx`、`tokens.txt` | 重新下载并解压模型包 |
| FFmpeg 不可用 | `ffmpeg -version`、`ASR_FFMPEG`、`PATH` | 安装 FFmpeg 或修正路径 |
| JNI 加载失败 | Native 文件架构、glibc、权限 | 使用 linux-x64 文件并检查目标架构 |

默认监听 `127.0.0.1:8080`，服务无用户认证，不得直接暴露公网或不可信局域网。
