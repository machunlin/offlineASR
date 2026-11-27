# macOS ARM64 运行指南

适用环境：Apple Silicon（M 系列）macOS。当前脚本使用项目默认的 macos-arm64 Maven Profile，面向本机运行和验证；不生成可直接交付客户的独立安装包。

## 1. 运行条件

- macOS ARM64
- JDK 11
- Maven（使用本机已安装版本）
- FFmpeg
- SenseVoiceSmall INT8 模型

模型目录必须包含：

- models/SenseVoiceSmall/model.int8.onnx
- models/SenseVoiceSmall/tokens.txt

## 2. 环境检查

~~~bash
uname -m
java -version
mvn -version
ffmpeg -version
~~~

确认 CPU 架构为 arm64、Java 主版本为 11，且 FFmpeg 可执行。若 Maven 未加入 PATH，请设置 MAVEN_HOME。

## 3. 启动

在仓库根目录执行：

~~~bash
bash scripts/mac-start.sh
~~~

脚本会检查 Java 11、Maven、模型文件和 FFmpeg，然后使用 macos-arm64 Profile 启动 Spring Boot。默认访问地址为 http://127.0.0.1:8080。

## 4. 验证

另开终端执行：

~~~bash
curl --fail http://127.0.0.1:8080/api/asr/health
~~~

确认 applicationStatus 为 READY、modelLoaded 为 true、ffmpegAvailable 为 true。随后使用真实音频调用：

~~~bash
curl --fail -X POST http://127.0.0.1:8080/api/asr \
  -H 'Accept: application/json' \
  -F 'file=@./sample.wav'
~~~

确认接口返回成功且识别文本非空。浏览器打开 http://127.0.0.1:8080，另行验证麦克风权限、设备选择和录音识别。

## 5. 停止与日志

在启动脚本所在终端按 Ctrl+C 停止。应用日志位于项目 logs/ 目录；临时音频位于 temp/ 目录，清理规则以当前应用配置为准。

## 6. 边界

- 本指南适用于 Apple Silicon ARM64，不适用于 Intel Mac。
- macOS 本机运行成功不代表 Windows x64 或 Linux x64 原生库验证通过。
- 不要将 macOS 原生 JAR 复制到 Windows 或 Linux 部署包。
- 如需客户现场无人值守服务或独立发布包，必须另行设计并验证对应平台的服务管理和打包方式。
