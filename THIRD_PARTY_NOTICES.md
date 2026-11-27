# OfflineASR 第三方组件与再分发说明

核验日期：2026-11-27

本文件是工程核验说明，不构成法律意见。OfflineASR 自身源码采用 [MIT License](LICENSE)。应用包只包含应用 JAR、配置、脚本和文档；外部运行环境由客户或技术支持工程师自行安装。

## 交付边界

| 组件 | 交付方式 | 说明 |
|---|---|---|
| sherpa-onnx JVM API / Java Wrapper | 随应用 Fat JAR | 仅包含 Java API，不包含 JNI 或 ONNX Runtime |
| sherpa-onnx Native Runtime / ONNX Runtime | 客户自行安装 | 使用官方 2.0.3 Native JAR，解压到 `external/sherpa-onnx-native/` |
| Java 11 Runtime | 客户自行安装 | 推荐 Eclipse Temurin；项目不打包 JDK/JRE |
| SenseVoiceSmall INT8 | 客户自行下载 | 官方 `2025-09-09` 模型包，放入 `models/SenseVoiceSmall/` |
| FFmpeg | 客户自行安装 | Windows/Linux 可使用固定 LGPL-only 构建；macOS 使用本机安装版本 |

详细下载地址、SHA-256、安装命令和验收步骤见 `docs/08-第三方组件与许可证清单.md`。

## sherpa-onnx / ONNX Runtime

- sherpa-onnx 项目：https://github.com/k2-fsa/sherpa-onnx
- sherpa-onnx 2.0.3：Apache-2.0；许可文本见 `deploy/licenses/sherpa-onnx-LICENSE`。
- ONNX Runtime 1.28.2：MIT，完整第三方声明见 `deploy/licenses/ONNX-Runtime/ThirdPartyNotices.txt`。
- Native JAR 同时包含 JNI 库和 ONNX Runtime；本项目不在应用 JAR 中打包该 Native JAR。

## SenseVoiceSmall 模型

- 固定模型包：`sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09`
- SHA-256：`7305f7905bfcf77fa0b39388a313f3da35c68d971661a65475b56fb2162c8e63`
- 模型包 README 指向 `https://huggingface.co/ASLP-lab/WSYue-ASR/tree/main/sensevoice_small_yue`
- 许可状态：待确认。模型卡显示的许可证、原始 SenseVoiceSmall 许可和二次转换模型之间的适用范围尚未取得最终许可方确认。
- 不得将模型标注为 MIT 或 Apache-2.0 后宣布商业分发授权已完成。
- `deploy/licenses/SenseVoiceSmall/` 保留模型包 README、模型卡和 FunASR 模型许可文本。

## FFmpeg

- FFmpeg 官方项目：https://ffmpeg.org/
- Windows/Linux 固定第三方 LGPL-only 构建入口见 `docs/08-第三方组件与许可证清单.md`。
- FFmpeg 许可文本见 `deploy/licenses/FFmpeg/COPYING.LGPLv3`。
- FFmpeg-Builds 项目许可文本见 `deploy/licenses/FFmpeg/FFmpeg-Builds-LICENSE`。
- 静态 LGPL 构建的源码提供、修改说明、重链接和安装信息义务仍需法律审核。

## Java Runtime

- 推荐 Eclipse Temurin 11 JRE。
- Temurin 二进制适用 GPL-2.0-only with Classpath Exception。
- 客户应保留发行包中的 `legal/` 目录和第三方声明；项目不复制或修改 Java Runtime。

## Maven 依赖

- Spring Boot 2.7.18 及其解析依赖随应用 Fat JAR 分发。
- 运行时/测试依赖树和 CycloneDX SBOM 可通过 `scripts/generate-maven-inventory.sh` 或 `scripts/generate-maven-inventory.ps1` 重新生成。
- 客户运行环境不需要 Maven、JDK 构建工具或开发机 `.m2` 目录。
