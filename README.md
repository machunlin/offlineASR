# OfflineASR

**OfflineASR** 是一个本机运行的离线语音识别服务：使用 Java + Spring Boot、sherpa-onnx 和 SenseVoiceSmall INT8，在本机 CPU 上完成语音转文字，并通过浏览器页面和 JavaScript SDK 提供能力。

- **公开演示：** https://asr.ai-buy.uk/
- **源码：** https://github.com/machunlin/offlineASR
- **运行方式：** 本地部署、离线推理；默认仅监听 `127.0.0.1:8080`
- **技术栈：** Java 11、Spring Boot 2.7、Maven、sherpa-onnx 2.0.3、SenseVoiceSmall INT8、FFmpeg
- **平台：** macOS ARM64、Windows x64、Linux x64

> 演示站点用于体验网页功能。生产环境请在目标设备本地部署；不要将未启用认证的服务直接暴露到公网。

## 交付边界

项目只负责自身应用构建、部署和运行。以下外部依赖由客户或技术支持工程师自行下载、安装和配置：

- Java 11 Runtime。
- sherpa-onnx 2.0.3 Native JAR（一个平台对应一个 JAR，内含 JNI 与 ONNX Runtime Native；无需另行下载 ONNX Runtime）。
- SenseVoiceSmall INT8 `2025-09-09` 模型。
- FFmpeg。
- 其他系统运行库，例如 Windows Microsoft Visual C++ Redistributable。

sherpa-onnx 的技术边界必须区分：

| 内容 | 交付方式 |
|---|---|
| sherpa-onnx JVM Java API/Wrapper | 随应用 Fat JAR |
| `sherpa-onnx-jni` | 随对应平台 Native JAR 提供，客户下载并解压 |
| ONNX Runtime Native | 已包含在同一个 Native JAR 中，无需单独下载 |

项目不自动下载上述外部依赖，不把它们复制进部署包，也不生成包含全部第三方资产的完整离线包。固定版本、官方地址、直接下载地址、SHA-256、安装步骤和许可状态见 [第三方组件与许可证清单](docs/08-第三方组件与许可证清单.md)。

## 快速开始

### 应用构建

构建机需要 Java 11、Maven 3.8+、Node.js 20。`lib/sherpa-onnx-jvm-2.0.3.jar` 只是 Java Wrapper，会随应用构建进入 Fat JAR；Native Runtime 不参与 Maven 构建。

```bash
mvn -B -Dmaven.repo.local=.m2 clean test package
node scripts/verify-js-contract.mjs
node scripts/verify-sdk-contract.mjs
```

检查 Fat JAR：

```bash
jar tf target/offline-asr-0.1.0-SNAPSHOT.jar | grep -q 'BOOT-INF/lib/sherpa-onnx-jvm-2.0.3.jar'
! jar tf target/offline-asr-0.1.0-SNAPSHOT.jar | grep -Eq 'sherpa-onnx-native-lib-|onnxruntime|sherpa-onnx-jni'
```

Linux 应用包：

```bash
bash scripts/linux-package.sh
```

Windows 应用包：

```powershell
.\scripts\windows-package.bat
```

输出分别为 `dist/linux-x64/` 和 `dist\windows-x64\`，只含应用文件。

### 目标机运行

1. 按 [AI Agent 部署入口](docs/AI_AGENT_DEPLOYMENT.md) 准备 Java 11、Native Runtime、模型和 FFmpeg。
2. 校验应用包。
3. 启动服务并检查 `GET /api/asr/health`。
4. 使用已授权真实音频和 Chrome 麦克风完成验收。

## 功能

- 浏览器麦克风设备枚举、选择、音量检测和录音。
- 上传音频文件并识别；支持格式以 FFmpeg 实际解码能力为准。
- 浏览器 JavaScript SDK，可独立集成到第三方页面。
- SenseVoiceSmall INT8 本地 CPU 推理，不调用云端 ASR。
- 健康检查：`GET /api/asr/health`。
- 识别接口：`POST /api/asr`，multipart 字段名为 `file`。
- 日志按天滚动；临时音频文件按配置保留并清理。

## 文档导航

| 读者 | 文档 | 用途 |
|---|---|---|
| 技术支持 / AI Agent | [AI Agent 部署入口](docs/AI_AGENT_DEPLOYMENT.md) | 三阶段准备、部署与验收的自动执行入口 |
| Windows 技术支持 | [Windows 部署手册](docs/03-Windows部署手册.md) | Windows x64 环境准备、部署和验收 |
| Linux 技术支持 | [Linux 部署手册](docs/04-Linux部署手册.md) | Linux x64 环境准备、部署和验收 |
| macOS ARM64 | [macOS 运行指南](docs/05-macOS运行指南.md) | Apple Silicon 本机运行和验证 |
| 现场支持 | [Windows 现场部署与排障](docs/Windows现场部署与排障手册.md) | 麦克风、Native、模型、FFmpeg 和日志排障 |
| SDK 集成人员 | [JavaScript SDK 接入文档](docs/SDK接入文档.md) | API、事件、参数和集成示例 |
| 发布/合规 | [第三方组件与许可证清单](docs/08-第三方组件与许可证清单.md) | 固定版本、下载、许可与发布门禁 |
| 开发人员 | [开发环境搭建](docs/02-开发环境搭建.md) | 本机构建、测试和依赖清单生成 |
| 发布人员 | [版本发布与客户交付规范](docs/07-版本发布与客户交付规范.md) | 应用包结构、校验、交付和升级 |
| 测试人员 | [测试计划与用例](docs/测试计划与用例.md) | 自动化测试和目标机验收 |

## 安全与隐私

- 默认绑定 `127.0.0.1`，不提供内置用户认证。
- 不要未经安全评估设置为 `0.0.0.0` 或暴露公网。
- 语音在本机处理；不要将客户录音、模型文件、密钥或现场日志提交到 Git。
- 公开演示站点与客户本地部署环境相互独立；不要向演示站点上传敏感或个人语音。

## 开源协议

本项目采用 [MIT License](LICENSE)。第三方软件、模型和原生运行库受各自许可证约束。详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)；SenseVoiceSmall 模型和 FFmpeg 当前仍有待确认的许可边界。
