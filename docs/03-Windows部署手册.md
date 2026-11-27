# Windows x64 部署手册

适用目标：Windows 10/11 x64。OfflineASR 应用包只包含应用文件，外部运行依赖由客户或技术支持工程师准备。

## 1. 应用包

应用包包含 `app/offline-asr.jar`、`config/application.yml`、启动/状态/服务/卸载脚本、`LICENSE`、`THIRD_PARTY_NOTICES.md`、第三方组件清单、Maven 依赖清单、SBOM、许可材料和 `checksums.sha256`。

应用包不包含 Java Runtime、sherpa-onnx Native Runtime、ONNX Runtime、SenseVoiceSmall 模型、FFmpeg 或 Microsoft Visual C++ Redistributable。

## 2. 第一阶段：准备运行环境

### 2.1 Java 11

1. 下载 Temurin 11 JRE x64，推荐 `11.0.33+10`。
2. 官方 Release：https://adoptium.net/temurin/releases/?version=11
3. 直接 ZIP：https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.33%2B10/OpenJDK11U-jre_x64_windows_hotspot_11.0.33_10.zip
4. SHA-256：`d07c70035f7462924cd385912d76d50622baa17b15e5260df8facabcf2fff998`
5. 校验命令：
   ~~~powershell
   Get-FileHash OpenJDK11U-jre_x64_windows_hotspot_11.0.33_10.zip -Algorithm SHA256
   ~~~
6. 解压后设置 `JAVA_HOME=<解压目录>`，并将 `%JAVA_HOME%\bin` 加入 `PATH`。
7. 验证：
   ~~~powershell
   "%JAVA_HOME%\bin\java.exe" -version
   ~~~
   成功判定：输出包含 `version "11`。

### 2.2 sherpa-onnx Native Runtime

1. 下载 sherpa-onnx 2.0.3 Windows x64 Native JAR：
   https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.3/sherpa-onnx-native-lib-win-x64-2.0.3.jar
2. SHA-256：`26a9072f4a47be3a73bfbbba5f25e6e8b84b24734be651de2d480ae753ae6508`
3. 校验：
   ~~~powershell
   Get-FileHash sherpa-onnx-native-lib-win-x64-2.0.3.jar -Algorithm SHA256
   ~~~
4. 将两个库扁平解压到应用目录下的 `external\sherpa-onnx-native\`：
   ~~~powershell
   New-Item -ItemType Directory -Force external\sherpa-onnx-native | Out-Null
   tar -xf sherpa-onnx-native-lib-win-x64-2.0.3.jar `
     -C external\sherpa-onnx-native --strip-components=3 `
     sherpa-onnx/native/win-x64/onnxruntime.dll `
     sherpa-onnx/native/win-x64/sherpa-onnx-jni.dll
   ~~~
5. 解压后目录根层必须同时存在：
   - `onnxruntime.dll`
   - `sherpa-onnx-jni.dll`
6. 可用 `ASR_SHERPA_NATIVE_PATH` 指定其他绝对路径。
7. 确认目标机已安装 Microsoft Visual C++ Redistributable x64；项目不随包分发该运行库。

### 2.3 SenseVoiceSmall INT8

1. 下载：
   https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09.tar.bz2
2. SHA-256：`7305f7905bfcf77fa0b39388a313f3da35c68d971661a65475b56fb2162c8e63`
3. 解压后把 `model.int8.onnx` 和 `tokens.txt` 放入：
   ~~~text
   C:\offline-asr\models\SenseVoiceSmall
   ~~~
4. 也可设置 `ASR_MODEL_DIR` 指向其他目录。
5. 校验：
   ~~~powershell
   Get-FileHash models\SenseVoiceSmall\model.int8.onnx -Algorithm SHA256
   ~~~

### 2.4 FFmpeg

1. 下载固定 LGPL-only 构建：
   https://github.com/FFmpeg-Builds/FFmpeg-Builds/releases/download/autobuild-2026-11-26-12-56/ffmpeg-n9.0.2-982-g836df4e858-win64-lgpl-9.0.zip
2. SHA-256：`1b60deb4fd8c4f13f987b9df3dbb3abe02eb135f66f080046e9e497f3f748fac`
3. 解压后将 `bin` 加入 `PATH`，或设置 `ASR_FFMPEG` 为 `ffmpeg.exe` 绝对路径。
4. 验证：`ffmpeg -version`

## 3. 第二阶段：部署 OfflineASR

1. 解压应用包到固定目录，例如 `C:\offline-asr`。
2. 执行：
   ~~~powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\windows-verify-package.ps1
   ~~~
   成功判定：静态自检通过。
3. 确认环境变量：
   - `JAVA_HOME`
   - `ASR_SHERPA_NATIVE_PATH`（未设置时默认 `external\sherpa-onnx-native`）
   - `ASR_MODEL_DIR`（未设置时默认 `models\SenseVoiceSmall`）
   - `ASR_FFMPEG` 或 `PATH`
4. 前台启动：
   ~~~powershell
   .\scripts\windows-start.bat
   ~~~
5. 浏览器访问 `http://127.0.0.1:8080`。
6. 健康检查：
   ~~~powershell
   curl.exe --fail http://127.0.0.1:8080/api/asr/health
   ~~~
   成功判定：`applicationStatus=READY`、`modelLoaded=true`、`ffmpegAvailable=true`。

## 4. 第三阶段：验收

1. 使用已授权真实音频调用 `POST /api/asr`，确认返回非空识别结果。
2. 在 Chrome 中验证麦克风权限、设备选择、录音和上传识别。
3. 断网后重启服务，重复真实音频识别。
4. 需要服务化运行时，先获得客户授权，检查 `JAVA_HOME`、`ASR_SHERPA_NATIVE_PATH`、`ASR_MODEL_DIR` 和服务账号权限，再安装 WinSW 服务。WinSW 模板通过 `windows-start.bat` 做 Java 11、Native、模型和 FFmpeg 检查，因此服务账号必须能读取同一组环境变量与目录。
5. 静态校验不替代 Windows x64 JNI、真实麦克风、断网、服务化和性能验收。

## 5. 常见故障

| 现象 | 检查 | 处理 |
|---|---|---|
| Java 不是 11 | `java -version`、`JAVA_HOME`、`PATH` | 安装/切换到 Java 11 |
| `onnxruntime.dll` 或 `sherpa-onnx-jni.dll` 缺失 | `ASR_SHERPA_NATIVE_PATH` | 重新下载/解压对应平台 Native JAR |
| 模型缺失 | `ASR_MODEL_DIR`、两个必需文件 | 重新下载并解压模型包 |
| FFmpeg 不可用 | `ffmpeg -version`、`ASR_FFMPEG`、`PATH` | 安装 FFmpeg 或修正路径 |
| JNI 加载失败 | Native 文件平台/架构、VC++ Runtime | 使用 win-x64 文件并安装运行库 |

默认监听 `127.0.0.1:8080`，服务无用户认证，不得直接暴露公网或不可信局域网。
