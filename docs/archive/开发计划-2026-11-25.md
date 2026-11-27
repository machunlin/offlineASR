# 离线中文语音识别系统 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付可在 macOS ARM64 开发、并可整理为 Windows x64 单机包的 Java 11 + Spring Boot 2.7 离线中文语音识别服务。

**Architecture:** Spring Boot 在启动时由 `AsrEngine` 装载一次 sherpa-onnx 离线模型；`AsrService` 负责单路推理闸门、文件生命周期与阶段耗时；`FfmpegAudioDecoder` 将受控临时文件转为 16 kHz 单声道 Float32 PCM；REST 控制器只负责 HTTP 契约，浏览器页面通过独立 `OfflineASR` 客户端调用 API。官方 JAR 以经过 SHA-256 校验的项目内本地资产加载，模型不进入版本控制。

**Tech Stack:** Java 11、Spring Boot 2.7、Spring MVC、Actuator、Micrometer、JUnit 5、sherpa-onnx v2.0.3 官方 JAR、FFmpeg、原生 HTML/CSS/JavaScript。

**Spec:** `项目设计文档V2.md`、`docs/验证记录.md`

## Global Constraints

- Java 固定为 11；不得引入 Spring Boot 3.x、Python、GPU、云端 ASR、数据库、MQ 或 Docker。
- 使用 SenseVoiceSmall INT8、CPU、默认 2 线程，模型只在 Spring 生命周期创建一次。
- 服务默认只绑定 `127.0.0.1:8080`；录音、文本和绝对路径不得写入日志或响应。
- 上传上限 50 MB、真实解码后音频上限 600 秒、单路推理与所有超时均配置化。
- 每项生产行为遵循失败测试 → 最小实现 → 通过测试；Windows 结果没有实机证据时标记未验证。

---

### Task 1: Maven 骨架与集中配置

**Files:**
- Create: `pom.xml`、`.gitignore`、`src/main/java/com/example/asr/AsrApplication.java`、`src/main/java/com/example/asr/config/AsrProperties.java`、`src/main/resources/application.yml`
- Test: `src/test/java/com/example/asr/config/AsrPropertiesTest.java`

**Interfaces:**
- Produces: `AsrProperties`，提供模型目录、线程数、大小/时长/超时、临时目录、FFmpeg 和并发配置。

- [ ] **Step 1: 编写配置绑定失败测试**：验证默认线程为 2、非正线程拒绝启动。
- [ ] **Step 2: 运行单测并确认因类不存在而失败。**
- [ ] **Step 3: 实现最小 Maven/Spring Boot 骨架和 `@ConfigurationProperties`。**
- [ ] **Step 4: 运行单测和 Maven 测试，确认通过。**

### Task 2: 统一错误契约、受控临时文件与 FFmpeg 解码

**Files:**
- Create: `exception/ApiException.java`、`exception/GlobalExceptionHandler.java`、`dto/ErrorResponse.java`、`service/TemporaryAudioStorage.java`、`service/FfmpegAudioDecoder.java`、`service/DecodedAudio.java`
- Test: `src/test/java/com/example/asr/service/TemporaryAudioStorageTest.java`、`src/test/java/com/example/asr/service/FfmpegAudioDecoderTest.java`

**Interfaces:**
- Consumes: `AsrProperties`。
- Produces: `decode(Path, String)` 返回样本为 Float32 PCM 的 `DecodedAudio`；失败映射为受控错误码。

- [ ] **Step 1: 为成功清理、失败清理、非零 FFmpeg 退出和解码超时写失败测试。**
- [ ] **Step 2: 运行测试确认缺少实现。**
- [ ] **Step 3: 用 `ProcessBuilder` 参数数组实现文件创建、受限 stderr、超时与 finally 清理。**
- [ ] **Step 4: 运行所有该任务测试确认通过。**

### Task 3: 模型生命周期、单路识别与服务层

**Files:**
- Create: `service/AsrEngine.java`、`service/SherpaOnnxAsrEngine.java`、`service/AsrService.java`、`dto/RecognitionResult.java`、`exception/ServiceNotReadyException.java`
- Test: `src/test/java/com/example/asr/service/AsrServiceTest.java`、`src/test/java/com/example/asr/service/SherpaOnnxAsrEngineTest.java`

**Interfaces:**
- Consumes: `DecodedAudio`、`AsrProperties`。
- Produces: `recognize(MultipartFile, String)` 的最终文本、音频时长与阶段耗时。

- [ ] **Step 1: 为未就绪拒绝、单路闸门超时、模型只初始化一次、空文本识别失败编写失败测试。**
- [ ] **Step 2: 运行测试确认失败原因正确。**
- [ ] **Step 3: 实现模型生命周期、信号量闸门与 sherpa-onnx Float32 PCM 调用。**
- [ ] **Step 4: 运行任务测试，并使用经验证模型运行最小实测程序。**

### Task 4: REST API、健康检查与监控

**Files:**
- Create: `controller/AsrController.java`、`controller/AsrHealthController.java`、`dto/RecognitionResponse.java`、`dto/HealthResponse.java`、`monitor/AsrMetrics.java`
- Modify: `application.yml`
- Test: `src/test/java/com/example/asr/controller/AsrControllerTest.java`、`src/test/java/com/example/asr/controller/AsrHealthControllerTest.java`

**Interfaces:**
- Consumes: `AsrService`。
- Produces: `/api/asr` 与 `/api/asr/health` 的 V2.2 JSON 契约。

- [ ] **Step 1: 为成功 JSON、缺少文件 400、大小超限 413、未就绪 503、请求 ID 回传写失败 MockMvc 测试。**
- [ ] **Step 2: 确认测试因端点不存在而失败。**
- [ ] **Step 3: 实现控制器、全局错误处理、计时器和最小 Actuator 暴露规则。**
- [ ] **Step 4: 运行 API 测试和端到端本机健康检查。**

### Task 5: 原生浏览器页面与一次性 JS 接入契约

**Files:**
- Create: `src/main/resources/static/index.html`、`src/main/resources/static/app.js`、`src/main/resources/static/style.css`
- Test: `src/test/java/com/example/asr/web/StaticAssetContractTest.java`

**Interfaces:**
- Produces: `window.OfflineASR.recognize(blob)`、一次 `onSpeechText(text)` 回调及一次 `asr-result` 事件。

- [ ] **Step 1: 为静态资源、`OfflineASR` 暴露及页面关键元素写失败测试。**
- [ ] **Step 2: 运行测试确认失败。**
- [ ] **Step 3: 实现录音状态机、自动停止、取消、请求 ID 过期响应过滤和可理解错误信息。**
- [ ] **Step 4: 运行静态资源测试；浏览器麦克风实测留为人工验收。**

### Task 6: 构建脚本、Windows 包骨架和六份中文交付文档

**Files:**
- Create: `scripts/mac-start.sh`、`scripts/windows-*.bat`、`deploy/windows-x64/README.txt`、`docs/01-项目架构说明.md`、`docs/02-开发环境搭建.md`、`docs/03-Windows部署手册.md`、`docs/04-客户现场调试手册.md`、`docs/05-常见故障排查.md`、`docs/06-版本升级与卸载.md`、`README.md`、`docs/测试报告.md`
- Test: `src/test/java/com/example/asr/PackagingContractTest.java`

**Interfaces:**
- Produces: 可复现的 macOS 启动方式和可由技术支持执行的 Windows 目录/脚本/文档契约。

- [ ] **Step 1: 为各必需脚本、文档和 Windows 包目录写失败测试。**
- [ ] **Step 2: 运行测试确认交付物缺失。**
- [ ] **Step 3: 实现最小安全脚本和文档，明确所有 Windows 实机项目为未验证。**
- [ ] **Step 4: 运行完整 Maven 测试、打包、脚本语法检查与构建产物检查。**
