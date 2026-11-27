# OfflineASR

**OfflineASR** 是一个本机运行的离线语音识别服务：使用 Java + Spring Boot、sherpa-onnx 和 SenseVoiceSmall INT8，在本机 CPU 上完成语音转文字，并通过浏览器页面和 JavaScript SDK 提供能力。

- **公开演示：** https://asr.ai-buy.uk/
- **源码：** https://github.com/machunlin/offlineASR
- **运行方式：** 本地部署、离线推理；默认仅监听 127.0.0.1:8080
- **技术栈：** Java 11、Spring Boot 2.7、Maven、sherpa-onnx 2.0.3、SenseVoiceSmall INT8、FFmpeg
- **平台：** macOS ARM64、Windows x64、Linux x64

> 演示站点用于体验网页功能。生产环境请在目标设备本地部署；不要将未启用认证的服务直接暴露到公网。

## 快速开始

1. 准备 Java 11、Maven、FFmpeg，以及 SenseVoiceSmall INT8 模型文件。
2. 将模型文件放入 models/SenseVoiceSmall/，至少包含 model.int8.onnx 和 tokens.txt。
3. 根据操作系统选择启动方式：

| 平台 | 启动/构建入口 |
|---|---|
| macOS ARM64 | bash scripts/mac-start.sh，参考 [macOS 运行指南](docs/05-macOS运行指南.md) |
| Linux x64 | bash scripts/linux-package.sh，然后按 Linux 部署指南部署 |
| Windows x64 | scripts\\windows-package.bat，按 Windows 部署指南准备完整离线包 |

4. 浏览器访问 http://127.0.0.1:8080，允许麦克风权限并进行识别。

**客户现场部署请勿依赖本节的开发环境命令。** 技术支持工程师和 AI Agent 应直接阅读 [AI Agent 部署入口](docs/AI_AGENT_DEPLOYMENT.md)，再按平台执行正式部署指南。

## 功能

- 浏览器麦克风设备枚举、选择、音量检测和录音。
- 上传音频文件并识别；支持格式以 FFmpeg 实际解码能力为准。
- 浏览器 JavaScript SDK，可集成到第三方页面，不依赖 Demo 页面 DOM。
- SenseVoiceSmall INT8 本地 CPU 推理，不调用云端 ASR。
- 健康检查：GET /api/asr/health。
- 识别接口：POST /api/asr，multipart 字段名为 file。
- 日志按天滚动；临时音频文件按配置保留并清理。

## 文档导航

| 读者 | 文档 | 用途 |
|---|---|---|
| 技术支持 / AI Agent | [AI Agent 部署入口](docs/AI_AGENT_DEPLOYMENT.md) | 一句话启动部署任务，自动识别平台并按指南执行 |
| Windows 技术支持 | [Windows 部署手册](docs/03-Windows部署手册.md) | 离线部署、启动、验收和服务配置 |\n| macOS ARM64 | [macOS 运行指南](docs/05-macOS运行指南.md) | Apple Silicon 本机运行和验证 |
| Linux 技术支持 | [Linux 部署手册](docs/04-Linux部署手册.md) | Ubuntu x64 构建与部署 |
| SDK 集成人员 | [JavaScript SDK 接入文档](docs/SDK接入文档.md) | API、事件、参数和集成示例 |
| 现场支持 | [Windows 现场部署与排障](docs/Windows现场部署与排障手册.md) | 麦克风、设备选择、日志和故障定位 |
| 开发人员 | [开发协作规范](docs/开发协作规范.md) | Git、代码和验证约定 |
| 测试人员 | [测试计划与用例](docs/测试计划与用例.md) | 自动化测试和现场验收 |

## 开发与验证

~~~bash
mvn clean test package
node scripts/verify-js-contract.mjs
node scripts/verify-sdk-contract.mjs
~~~

Linux x64 和 Windows x64 必须分别使用对应平台的 sherpa-onnx 原生库构建。切换 Maven Profile 前必须执行 clean，不能复用其他平台的 target 产物。

自动化测试和 CI 不能替代目标设备上的 JNI 加载、真实 Chrome 麦克风、断网启动、服务化运行及性能验收。请如实记录实际执行结果，不得把静态构建描述为目标设备实机通过。

## 安全与隐私

- 默认绑定 127.0.0.1，不提供内置用户认证。
- 不要未经安全评估就设置为 0.0.0.0 或暴露公网。
- 语音文件在本机处理；不要将客户录音、模型文件、密钥或现场日志提交到 Git。
- 公开演示站点与客户本地部署环境相互独立；不要向演示站点上传敏感或个人语音。

## 开源协议

本项目采用 [MIT License](LICENSE)。项目中引用的第三方软件、模型和原生运行库仍受其各自许可证约束，使用和再分发前请核对对应上游许可。
