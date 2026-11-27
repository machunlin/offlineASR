# AI Agent 部署任务入口

本文是 OfflineASR 构建、校验和部署的统一入口。AI Agent 先阅读本文，再按目标平台执行；平台细节见正式部署手册。OfflineASR 的应用包只包含项目自身文件，Java 11、sherpa-onnx JNI/ONNX Runtime Native、SenseVoiceSmall 模型和 FFmpeg 均由客户或技术支持工程师在目标机自行准备。

## 一句话任务

> 阅读 `docs/AI_AGENT_DEPLOYMENT.md`，先识别是“构建应用包”还是“部署已有应用包”，再识别操作系统和 CPU 架构。按三阶段准备 `Java 11`、`sherpa-onnx 2.0.3 Native Runtime`、`SenseVoiceSmall INT8 2025-09-09` 和 `FFmpeg`；部署并校验 OfflineASR；最后执行 health、真实音频、浏览器麦克风、断网和服务化验收。每步记录命令、成功/失败判定、日志和未验证边界。不得自动下载外部依赖，不得开放局域网/公网，不得创建系统用户、修改防火墙或安装服务，除非现场负责人另行授权。

## 1. 场景和平台

| 场景 | 目标 | 入口 |
|---|---|---|
| 构建应用包 | 用源码构建 Fat JAR，组装只含应用文件的 Windows/Linux 包 | 第 2 节 |
| 部署已有包 | 在目标机准备外部依赖、校验应用包、启动和验收 | 第 3 节 |

| 平台 | 架构 | 正式手册 | Native 文件 |
|---|---|---|---|
| Windows 10/11 | x64 | `docs/03-Windows部署手册.md` | `onnxruntime.dll`、`sherpa-onnx-jni.dll` |
| Ubuntu 22.04 等 Linux | x86_64 | `docs/04-Linux部署手册.md` | `libonnxruntime.so`、`libsherpa-onnx-jni.so` |
| macOS | Apple Silicon ARM64 | `docs/05-macOS运行指南.md` | `libonnxruntime.dylib`、`libsherpa-onnx-jni.dylib` |

不支持 Intel macOS、Windows ARM64、Linux ARM64 或 32 位系统。不得混用其他平台的 Native JAR。

## 2. 构建应用包

### 2.1 构建机前置条件

- Java 11，本机安装；项目不下载或随包复制 JDK/JRE。
- Maven 3.8+，建议 3.9.x。
- Node.js 20。
- 构建机上的 `lib/sherpa-onnx-jvm-2.0.3.jar` 仅提供 Java API/Wrapper。
- 构建机和 CI 不需要 Native JAR、模型或 FFmpeg 才能完成 Maven 编译测试。

### 2.2 macOS/Linux 构建

```bash
export JAVA_HOME=/path/to/java-11
mvn -B -Dmaven.repo.local=.m2 clean test package
node scripts/verify-js-contract.mjs
node scripts/verify-sdk-contract.mjs
```

成功判定：Maven 退出码 0；两项 Node 契约测试退出码 0；`target/offline-asr-0.1.0-SNAPSHOT.jar` 存在。

Fat JAR 必须：

```bash
jar tf target/offline-asr-0.1.0-SNAPSHOT.jar | grep -q 'BOOT-INF/lib/sherpa-onnx-jvm-2.0.3.jar'
! jar tf target/offline-asr-0.1.0-SNAPSHOT.jar | grep -Eq 'sherpa-onnx-native-lib-|onnxruntime|sherpa-onnx-jni'
```

失败判定：Java Wrapper 缺失，或 Fat JAR 中出现 Native JAR、`onnxruntime`、`sherpa-onnx-jni`，必须停止打包。

### 2.3 Linux 应用包

```bash
bash scripts/linux-package.sh
```

输出 `dist/linux-x64/`。脚本会构建、测试、生成 Maven 清单/SBOM、复制配置/脚本/许可材料和校验脚本，不下载第三方资产。成功判定为脚本输出“应用包已生成并通过静态校验”。

### 2.4 Windows 应用包

```powershell
.\scripts\windows-package.bat
```

输出 `dist\windows-x64\`。成功判定为脚本退出码 0，且包内存在 `app/offline-asr.jar`、`config/application.yml`、`scripts/windows-verify-package.ps1` 和 `checksums.sha256`。

应用包允许包含：应用 JAR、配置、启动/状态/服务/校验脚本、部署文档、第三方组件清单、Maven 依赖清单、SBOM、许可材料和 SHA-256 清单。

应用包不得包含：JDK/JRE、`models/`、`ffmpeg/`、`external/sherpa-onnx-native/`、`sherpa-onnx-native-lib-*.jar`、客户录音或日志。

## 3. 部署已有应用包

### 3.1 第一阶段：客户准备运行环境

按 `docs/08-第三方组件与许可证清单.md` 的固定版本、官方地址和 SHA-256 下载。项目不提供自动下载脚本。

#### 3.1.1 Java 11

```bash
java -version
```

成功判定：输出包含 `version "11`，且架构与操作系统一致。失败时停止，不通过降级或修改启动脚本绕过。

#### 3.1.2 sherpa-onnx Native Runtime

固定版本：sherpa-onnx `2.0.3`。Windows x64、Linux x64、macOS ARM64 必须使用各自 Native JAR。该 JAR 同时包含 JNI 与 ONNX Runtime，解压时必须把两个库放到目录根层：

```text
<部署根目录>/external/sherpa-onnx-native/
  onnxruntime.dll | libonnxruntime.so | libonnxruntime.dylib
  sherpa-onnx-jni.dll | libsherpa-onnx-jni.so | libsherpa-onnx-jni.dylib
```

检查命令：

```bash
# Linux/macOS，按平台选择两个文件名
find external/sherpa-onnx-native -maxdepth 1 -type f -print
```

```powershell
# Windows
dir external\sherpa-onnx-native\onnxruntime.dll
dir external\sherpa-onnx-native\sherpa-onnx-jni.dll
```

成功判定：两个文件都存在且位于 Native 目录根层。常见失败是把 JAR 内的 `sherpa-onnx/native/<platform>/` 子目录层级一并保留；应用不会从该子目录加载。

#### 3.1.3 SenseVoiceSmall INT8 模型

固定版本：`sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09`。

```bash
mkdir -p models/SenseVoiceSmall
tar -xjf sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09.tar.bz2 \
  -C models/SenseVoiceSmall --strip-components=1
test -f models/SenseVoiceSmall/model.int8.onnx
test -f models/SenseVoiceSmall/tokens.txt
```

Windows PowerShell:

```powershell
New-Item -ItemType Directory -Force models\SenseVoiceSmall | Out-Null
tar -xjf sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09.tar.bz2 -C models\SenseVoiceSmall --strip-components=1
Test-Path models\SenseVoiceSmall\model.int8.onnx
Test-Path models\SenseVoiceSmall\tokens.txt
```

成功判定：归档 SHA-256 与清单一致，两个文件都存在。模型许可仍为待确认，不得宣称 Apache-2.0、MIT 或已完成商业再分发授权。

#### 3.1.4 FFmpeg

```bash
ffmpeg -version
```

成功判定：退出码 0 且输出版本信息。Windows/Linux 应使用清单中的固定 LGPL-only 构建；macOS 可使用 Homebrew 或源码构建。失败时检查 `PATH`、`ASR_FFMPEG` 绝对路径和执行权限。

### 3.2 第二阶段：部署 OfflineASR

1. 解压应用包到固定目录。
2. 运行平台静态校验：

```bash
bash scripts/linux-verify-package.sh
```

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\windows-verify-package.ps1
```

成功判定：应用 JAR 含 `BOOT-INF/lib/sherpa-onnx-jvm-2.0.3.jar`，且不含 Native Runtime；包内哈希全部匹配；应用包不含模型、FFmpeg、JRE 或 Native 目录。失败时停止，不得跳过校验启动。

3. 设置环境变量或用默认相对路径：

| 变量 | 默认位置 | 说明 |
|---|---|---|
| `JAVA_HOME` | 无 | Java 11 安装目录；也可使用 PATH 中的 `java` |
| `ASR_SHERPA_NATIVE_PATH` | `external/sherpa-onnx-native` | 两个 Native 库所在目录 |
| `ASR_MODEL_DIR` | `models/SenseVoiceSmall` | 含 `model.int8.onnx` 和 `tokens.txt` |
| `ASR_FFMPEG` | `ffmpeg` | FFmpeg 可执行文件绝对路径 |
| `ASR_HOST` | `127.0.0.1` | 未经授权不得改为 `0.0.0.0` |

4. 前台启动：

```bash
./linux-start.sh
```

```powershell
.\scripts\windows-start.bat
```

macOS 源码运行：

```bash
export ASR_MODEL_DIR="$PWD/models/SenseVoiceSmall"
export ASR_SHERPA_NATIVE_PATH="$PWD/external/sherpa-onnx-native"
bash scripts/mac-start.sh
```

成功判定：进程保持运行、日志无 `UnsatisfiedLinkError`、`LinkageError` 或模型加载失败。

5. health 检查：

```bash
curl --fail http://127.0.0.1:8080/api/asr/health
```

成功判定：HTTP 200，`applicationStatus=READY`、`modelLoaded=true`、`ffmpegAvailable=true`。失败时读取 `modelError`、`ffmpegError` 和应用日志，回到第一阶段修正。

### 3.3 第三阶段：验收

1. 使用已授权真实音频调用 `POST /api/asr`，确认返回非空识别结果。
2. 在目标机 Chrome 验证麦克风权限、设备枚举、设备切换、录音、文件上传和识别。
3. 断开互联网后重启服务并重复真实音频识别，确认仍为 READY。
4. 如客户要求服务化，在获得授权后安装 systemd/WinSW，并按同一清单复验。
5. 每项记录“通过 / 失败 / 未执行”，包含操作系统、CPU、Java、Chrome、模型与 Native 哈希、日志时间和 requestId。

验收边界：构建成功、JAR 静态检查或 macOS 本机推理成功，都不能替代 Windows/Linux 目标机的 JNI、麦克风、断网、服务和性能验收。

## 4. 故障判定

| 失败点 | 典型错误 | 处理 |
|---|---|---|
| Java | `version "1.8` 或 `17` | 安装 Java 11，修正 `JAVA_HOME`/`PATH` |
| Native 目录 | `ASR_SHERPA_NATIVE_PATH 不是目录` | 设置绝对路径，确认目录存在 |
| Native 文件 | `Native Runtime 文件缺失` | 将两个库扁平解压到目录根层，核对平台架构 |
| JNI 加载 | `UnsatisfiedLinkError` | 检查平台、CPU 架构、依赖运行库、文件权限 |
| 模型 | `缺少 model.int8.onnx` / `缺少 tokens.txt` | 重新解压模型并检查 `ASR_MODEL_DIR` |
| FFmpeg | `ffmpegAvailable=false` | 修正 `ASR_FFMPEG` 或 PATH |
| 应用包 | 缺少 Wrapper 或发现 Native | 禁止交付，重新构建并检查 Fat JAR |

## 5. 禁止事项

- 不运行或恢复任何第三方资产自动下载脚本。
- 不把 Native Runtime、模型、FFmpeg、JRE 复制进应用包。
- 不把 Native JAR 作为 Maven 依赖重新加入 Fat JAR。
- 不使用未核验的平台原生库或来源不明的模型。
- 不把静态检查、CI 构建或非目标平台实测写成客户实机通过。
- 未经明确授权，不发送真实语音到外部服务或公开网络。
