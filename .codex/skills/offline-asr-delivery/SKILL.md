---
name: offline-asr-delivery
description: Develop, test, package, deploy, or troubleshoot the OfflineASR Java service and its macOS ARM64, Windows x64, and Linux x64 delivery paths.
---

# OfflineASR 项目规则

本 Skill 只适用于本仓库。部署任务必须先阅读 docs/AI_AGENT_DEPLOYMENT.md；该文档是平台部署的统一入口。不要在 Skill 中复制部署步骤或维护第二套部署说明。

## 固定技术约束

- Java 11、Spring Boot 2.7、Maven、sherpa-onnx v2.0.3、SenseVoiceSmall INT8、FFmpeg、CPU 推理。
- sherpa-onnx Java JAR 使用官方 Release 资产；项目通过 lib 引用，Spring Boot 打包必须保留 includeSystemScope=true。
- macOS ARM64、Windows x64、Linux x64 必须使用各自平台的原生 JAR。切换 Maven Profile 前执行 clean，禁止混装平台库。
- 模型文件为 model.int8.onnx 和 tokens.txt；模型启动时加载一次并复用。
- 默认只监听 127.0.0.1。未经用户明确授权，不得开放局域网/公网访问。
- 音频最大时长以当前代码和配置为准；修改前检查后端、SDK、Demo、部署配置是否一致，不得从历史文档推断当前值。
- 不提交模型、录音、PCM、日志、密钥、个人环境配置和构建缓存。

## 浏览器 SDK 边界

- 可复用 SDK：src/main/resources/static/sdk/offline-asr-sdk.js。
- index.html 和 app.js 只负责 Demo 展示；设备枚举、麦克风选择、音量检测、录音生命周期和识别请求由 SDK 负责。
- microphone-test.html 是独立诊断页，不得上传音频或调用 ASR 接口。
- 修改 SDK 公共 API、事件、错误码或默认参数时，同步更新 docs/SDK接入文档.md 和 scripts/verify-sdk-contract.mjs。
- 不手工设置 multipart Content-Type；保留 POST /api/asr 的 file 字段和 X-Request-Id。
- release() 必须释放 MediaStream tracks、AudioContext、MediaRecorder、定时器和设备变更监听器。

## Git 与协作

- 每次提交前先读取远端 develop 最新 HEAD，确认变更基于最新代码；不得覆盖用户改动。
- 日志、提交说明、文档和注释尽量使用中文。
- 未经明确授权，不修改 master、不强制推送、不重写历史、不合并分支。
- 修改脚本时交付完整脚本，不只给局部片段。
- 不得把未执行的测试标记为通过；目标设备、真实麦克风、断网和服务化验证必须明确记录证据边界。

## 验证入口

~~~bash
mvn clean test package
node scripts/verify-js-contract.mjs
node scripts/verify-sdk-contract.mjs
~~~

Linux x64 和 Windows x64 另按 docs/AI_AGENT_DEPLOYMENT.md 执行平台构建和原生库隔离检查。macOS 本机推理通过不能证明 Windows/Linux 目标机 JNI、驱动、服务和性能通过。

## 录音排障

若音频容器可解码但没有声音，检查解码后 PCM 的 RMS 和 Peak。两者均为 0 表示数字静音；优先检查 Chrome 权限、实际输入设备、系统麦克风隐私/静音状态和 MediaStreamTrack 状态。通过 requestId 关联上传与解码日志。
