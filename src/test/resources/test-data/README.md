# ASR 测试音频

本目录包含可直接用于 `AsrServiceTest` 的中文 WAV 测试音频。

| 文件 | 内容/用途 | 来源 |
|---|---|---|
| `hello_zh.wav` | 短句普通话，参考文本“你好世界”，约 2.32 秒 | [FireRedASR2S](https://github.com/FireRedTeam/FireRedASR2S)，项目示例音频 `assets/hello_zh.wav` |
| `example_zh.wav` | 中文普通话示例录音 | [GLM-ASR](https://github.com/zai-org/GLM-ASR)，项目示例音频 `examples/example_zh.wav` |

## 运行真实识别测试

1. 确保项目模型目录存在：`models/SenseVoiceSmall/model.int8.onnx` 和 `tokens.txt`。
2. 确保当前平台对应的 sherpa-onnx native JAR 已在 Maven Profile 中启用。
3. 确保 FFmpeg 已安装并可通过 PATH 找到，或配置 `asr.ffmpeg-command`。
4. 在 IDEA 中直接运行 `AsrServiceTest`，或执行：

   ```bash
   $MAVEN_HOME/bin/mvn -Dtest=AsrServiceTest test
   ```

真实识别测试会调用 FFmpeg 和 SenseVoice JNI，不使用 Mock；识别文字及解码、推理、总耗时会输出到测试控制台。现有两个 Mock 单元测试仍保留。

## 来源与许可

- `hello_zh.wav`：来自 FireRedASR2S 项目的示例资源；上游仓库采用 Apache-2.0 许可证。上游 README 给出的参考识别文本为“你好世界”。
- `example_zh.wav`：来自 GLM-ASR 项目的中文示例资源；上游仓库采用 Apache-2.0 许可证。

本项目保留上游来源信息，仅将音频用于离线 ASR 功能测试。
