# ASR 测试音频

本测试集目标为 15 条带人工参考文本的音频样本：保留现有 2 条项目示例音频，新增 13 条 THCHS-30 普通话朗读样本。每条样本均保留原始音频、参考文本、来源、许可、实际时长、采样率和 SHA-256。

## 测试集组成

| 样本 | 数量 | 用途 |
|---|---:|---|
| FireRedASR2S 示例 | 1 | 短句普通话 |
| GLM-ASR 示例 | 1 | 低音量普通话 |
| THCHS-30 | 13 | 不同句长和词汇内容的普通话朗读 |

说明：新增 13 条 THCHS-30 样本目前来自同一说话人 A11，适合先建立普通话朗读场景的 CER 回归基线，但不能代表多说话人、方言或噪声环境的完整准确率。后续应补充不同说话人和真实噪声样本。

## 获取测试音频

仓库提交清单模板和下载脚本，不直接提交新增音频二进制文件。执行脚本后，原始 MP3 会保存在本目录，脚本同时下载上游逐字标注并核对参考文本，生成本地运行清单 `manifest.tsv`。清单记录实际时长、采样率和 SHA-256。

macOS / Linux：

```bash
bash scripts/download-test-data.sh
```

Windows PowerShell：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/download-test-data.ps1
```

依赖：FFmpeg（需要 `ffprobe`）。下载阶段需要网络；下载完成后，音频识别与 CER 测试可离线运行。

## 文件说明

- `manifest-template.tsv`：纳入版本控制的 15 条样本定义、参考文本、来源和许可。
- `manifest.tsv`：下载脚本生成的本地运行清单，含实际时长、采样率和 SHA-256，不提交到 Git。
- 音频文件：保留上游原始编码；现有 WAV 与新增 MP3 由服务端 FFmpeg 统一解码。

## 来源与许可

- `hello_zh.wav`：来自 [FireRedASR2S](https://github.com/FireRedTeam/FireRedASR2S)，参考文本“你好世界”，上游仓库采用 Apache-2.0。
- `example_zh.wav`：来自 [GLM-ASR](https://github.com/zai-org/GLM-ASR)，参考文本“我还能再搞一个，就算是非常小的声音也能识别准确”，上游仓库采用 Apache-2.0。
- 新增 13 条：来自 [THCHS-30](https://www.openslr.org/18/)；OpenSLR SLR18 标注 Apache License 2.0。下载脚本使用公开镜像中的 MP3 文件和对应 `.trn` 标注，并保留原始来源链接。

## Maven 真实识别与 CER 评估

先由开发人员自行准备模型、当前平台对应的 Native Runtime、FFmpeg 和 15 条音频。Native Runtime 必须扁平解压到 `external/sherpa-onnx-native/`，模型放入 `models/SenseVoiceSmall/`。

macOS/Linux：

```bash
export ASR_MODEL_DIR="$PWD/models/SenseVoiceSmall"
export ASR_SHERPA_NATIVE_PATH="$PWD/external/sherpa-onnx-native"
export ASR_FFMPEG="$(command -v ffmpeg)"
mvn -Dmaven.repo.local=.m2 -Dasr.accuracy.enabled=true -Dtest=AsrAccuracyTest test
```

Windows x64：

```powershell
$env:ASR_MODEL_DIR = "$PWD\models\SenseVoiceSmall"
$env:ASR_SHERPA_NATIVE_PATH = "$PWD\external\sherpa-onnx-native"
$env:ASR_FFMPEG = (Get-Command ffmpeg.exe -ErrorAction Stop).Source
mvn -Dmaven.repo.local=.m2 -Dasr.accuracy.enabled=true -Dtest=AsrAccuracyTest test
```

真实推理前检查 Native 目录根层同时存在 ONNX Runtime 和 sherpa-onnx JNI 两个库；检查 Java 为 11、FFmpeg 可执行。

测试会逐条识别清单中的音频，输出参考文本、识别文本、单条 CER、整体 CER、音频时长和耗时。CER 使用字符级编辑距离：

```
CER = (替换数 + 删除数 + 插入数) / 参考文本字符数
```

计算前统一 Unicode 兼容字符、英文大小写，并去除空白和标点；数字及中英文字符保留。普通 `mvn test` 不会自动加载模型执行真实推理，避免无模型的 CI 环境误报失败。
