# macOS ARM64 运行指南

适用环境：Apple Silicon macOS。本指南只运行 OfflineASR 应用；Java Runtime、sherpa-onnx Native Runtime、模型和 FFmpeg 均由本机准备，项目不打包这些外部依赖。

## 1. 运行条件

- macOS ARM64
- Java 11
- sherpa-onnx 2.0.3 osx-aarch64 Native Runtime
- SenseVoiceSmall INT8 2025-09-09
- FFmpeg
- Maven（源码运行时需要）

## 2. 第一阶段：准备运行环境

### 2.1 Java 11

推荐 Eclipse Temurin 11 JRE aarch64，版本 `11.0.33.1+1`。

1. 直接下载：
   https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.33.1%2B1/OpenJDK11U-jre_aarch64_mac_hotspot_11.0.33.1_1.tar.gz
2. SHA-256：`207b126d486e57135b8e82cd7679cfa1c87e9f9c5378f7c5c5aa214ee4b3fea5`
3. 校验：
   ~~~bash
   shasum -a 256 OpenJDK11U-jre_aarch64_mac_hotspot_11.0.33.1_1.tar.gz
   ~~~
4. 解压后设置 `JAVA_HOME`：
   ~~~bash
   export JAVA_HOME=/path/to/temurin-11
   export PATH="$JAVA_HOME/bin:$PATH"
   ~~~
5. 验证：`java -version`。成功判定：输出包含 `version "11`。

### 2.2 sherpa-onnx Native Runtime

1. 下载：
   https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.3/sherpa-onnx-native-lib-osx-aarch64-2.0.3.jar
2. SHA-256：`9c85930bcf081eb285a324f0f97eec63054a2a6ba7baf47408b83683add2a675`
3. 将两个库扁平解压到项目下的 `external/sherpa-onnx-native/`：
   ~~~bash
   mkdir -p external/sherpa-onnx-native
   unzip -j sherpa-onnx-native-lib-osx-aarch64-2.0.3.jar \
     'sherpa-onnx/native/osx-aarch64/*' -d external/sherpa-onnx-native
   ~~~
4. 确认目录根层同时存在 `libonnxruntime.dylib` 和 `libsherpa-onnx-jni.dylib`。

### 2.3 SenseVoiceSmall INT8

1. 下载：
   https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09.tar.bz2
2. SHA-256：`7305f7905bfcf77fa0b39388a313f3da35c68d971661a65475b56fb2162c8e63`
3. 解压后放入 `models/SenseVoiceSmall/`，确保包含 `model.int8.onnx` 和 `tokens.txt`。

### 2.4 FFmpeg（固定版本 9.0.2）

项目不提供 macOS FFmpeg 二进制。为避免 Homebrew 滚动升级导致版本不可复现，建议从 FFmpeg 官方源码构建固定版本 `9.0.2`。前置条件：安装 Xcode Command Line Tools（`xcode-select --install`），确保 `clang`、`make`、`xz` 可用。

```bash
curl -fL https://ffmpeg.org/releases/ffmpeg-9.0.2.tar.xz -o ffmpeg-9.0.2.tar.xz
printf '%s  %s\\n' '8c3850283eb25fa026482078a04051e0be17347b09ef81a0849bec15a96e002e' 'ffmpeg-9.0.2.tar.xz' | shasum -a 256 -c -
tar -xJf ffmpeg-9.0.2.tar.xz
cd ffmpeg-9.0.2
./configure \\
  --prefix="$HOME/opt/ffmpeg-9.0.2" \\
  --disable-debug \\
  --disable-doc \\
  --disable-ffplay \\
  --disable-network \\
  --disable-autodetect \\
  --disable-shared \\
  --enable-static \\
  --enable-ffmpeg
make -j"$(sysctl -n hw.ncpu)"
make install
```

SHA-256 `8c3850283eb25fa026482078a04051e0be17347b09ef81a0849bec15a96e002e` 是第三方 Fossies 对同一官方源码归档公布的校验值，不是 FFmpeg 官方发布页提供的 checksum；如客户要求强制验证发布者身份，应另外验证 FFmpeg 官方 `.asc` 签名。版本说明：[FFmpeg 9.0.2](https://www.ffmpeg.org/download.html)。

配置和验证：

```bash
export ASR_FFMPEG="$HOME/opt/ffmpeg-9.0.2/bin/ffmpeg"
"$ASR_FFMPEG" -version
```

成功判定：输出包含 `ffmpeg version 9.0.2`。将 `ASR_FFMPEG` 配置到启动 OfflineASR 的 shell 环境中。

## 3. 第二阶段：启动 OfflineASR

在仓库根目录执行：

~~~bash
export ASR_MODEL_DIR="$PWD/models/SenseVoiceSmall"
export ASR_SHERPA_NATIVE_PATH="$PWD/external/sherpa-onnx-native"
bash scripts/mac-start.sh
~~~

脚本会检查 Java 11、模型文件、Native Runtime 文件和 FFmpeg，然后启动服务。

另开终端验证：

~~~bash
curl --fail http://127.0.0.1:8080/api/asr/health
~~~

成功判定：`applicationStatus=READY`、`modelLoaded=true`、`ffmpegAvailable=true`。

## 4. 第三阶段：验证

~~~bash
curl --fail -X POST http://127.0.0.1:8080/api/asr \
  -H 'Accept: application/json' \
  -F 'file=@./sample.wav'
~~~

确认返回成功且识别文本非空。随后在 Chrome 中验证麦克风权限、设备选择、录音识别和文件上传。

## 5. 停止与日志

在启动终端按 `Ctrl+C` 停止。应用日志位于 `logs/`，临时音频位于 `temp/`。

## 6. 边界

- 本指南只适用于 Apple Silicon ARM64，不适用于 Intel Mac。
- 本机推理通过不代表 Windows x64 或 Linux x64 的 Native Runtime、驱动和服务化验收通过。
- 不要把 macOS Native 文件复制到 Windows/Linux 部署环境。
- 模型和 FFmpeg 许可问题未完全解决前，不得宣称商业分发许可已完成。
