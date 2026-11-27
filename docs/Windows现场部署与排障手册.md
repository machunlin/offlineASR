# Windows 客户现场部署与排障手册

## 1. 部署目标与边界

- Windows 10/11 x64 主机；Chrome 与识别服务运行在同一台主机。
- 默认监听 `127.0.0.1:8080`，只允许本机访问。
- OfflineASR 应用包只含应用、配置、脚本、校验材料和文档。
- Java 11、sherpa-onnx JNI/ONNX Runtime、SenseVoiceSmall 模型、FFmpeg 和 VC++ Redistributable 由现场自行安装，不在应用包内。
- 项目不提供第三方资产自动下载脚本，不生成包含上述外部依赖的完整离线包。

## 2. 三阶段部署

### 2.1 第一阶段：客户准备运行环境

1. 安装 Java 11 x64，执行 `java -version`，成功判定为输出包含 `version "11`。
2. 下载 sherpa-onnx `2.0.3` Windows x64 Native JAR，校验 SHA-256，并把两个库扁平解压到 `external\sherpa-onnx-native\` 根层：
   - `onnxruntime.dll`
   - `sherpa-onnx-jni.dll`
   - 不得保留 JAR 内 `sherpa-onnx/native/win-x64/` 子目录。
3. 下载 SenseVoiceSmall INT8 `2025-09-09` 模型包，校验 SHA-256，解压到 `models\SenseVoiceSmall\`：
   - `model.int8.onnx`
   - `tokens.txt`
4. 安装 FFmpeg，执行 `ffmpeg -version`，成功判定为退出码 0。
5. 安装 Microsoft Visual C++ Redistributable x64。
6. 固定下载地址、版本、SHA-256 和许可状态见 `docs/08-第三方组件与许可证清单.md`。

标准完整运行目录示意如下。`external/`、`models/` 和 FFmpeg 均由客户准备，不属于应用包：

```text
offline-asr/
  app/offline-asr.jar
  config/application.yml
  scripts/
  licenses/
  third-party/
  docs/
  external/sherpa-onnx-native/
    onnxruntime.dll
    sherpa-onnx-jni.dll
  models/SenseVoiceSmall/
    model.int8.onnx
    tokens.txt
  logs/
  temp/
```

### 2.2 第二阶段：部署 OfflineASR

1. 解压应用包到固定路径，例如 `C:\offline-asr`。
2. 运行 `scripts\windows-verify-package.ps1`。成功判定为应用 JAR 含 Java Wrapper、包内不含 Native Runtime，且所有 SHA-256 匹配。
3. 确认环境变量或默认路径：

| 变量 | 未设置时默认值 | 检查命令 |
|---|---|---|
| `JAVA_HOME` | PATH 中的 `java.exe` | `java -version` |
| `ASR_SHERPA_NATIVE_PATH` | `external\sherpa-onnx-native` | `dir %ASR_SHERPA_NATIVE_PATH%` |
| `ASR_MODEL_DIR` | `models\SenseVoiceSmall` | `dir %ASR_MODEL_DIR%` |
| `ASR_FFMPEG` | PATH 中的 `ffmpeg.exe` | `ffmpeg -version` |
| `ASR_HOST` | `127.0.0.1` | 不得未经授权改为 `0.0.0.0` |

4. 运行 `scripts\windows-start.bat`。成功判定为进程保持运行，日志无 `UnsatisfiedLinkError`、`LinkageError` 或模型加载失败。
5. 打开 `http://127.0.0.1:8080/`，执行健康检查：

```powershell
curl.exe --fail http://127.0.0.1:8080/api/asr/health
```

成功判定为 HTTP 200，且 `applicationStatus=READY`、`modelLoaded=true`、`ffmpegAvailable=true`。

### 2.3 第三阶段：验收

1. 使用已授权真实音频调用 `POST /api/asr`，确认返回非空识别结果。
2. 在 Chrome 中验证麦克风权限、设备枚举、设备切换、录音识别和 WAV/MP3 上传识别。
3. 以 `requestId` 关联上传、FFmpeg 解码和推理日志。
4. 断开互联网后重启服务，重新执行 health 和真实音频识别。
5. 如客户需要 Windows 服务，在获得授权后安装 WinSW，并重新完成 JNI、health、麦克风和断网验收。
6. 静态包检查或 CI 结果不能替代目标 Windows 主机的实机验收。

## 3. 临时音频文件管理

- 原始上传音频和 FFmpeg 解码 PCM 统一保存在部署目录 `temp/`，所有文件位于同一层目录。
- 文件名格式为 `yyyyMMddHHmmss.扩展名`，例如 `20261126163025.wav`、`20261126163026.pcm`。
- 文件在请求结束后继续保留。后台任务每 10 分钟扫描一次，仅删除创建时间超过 4 小时的普通文件。
- 清理任务不递归删除子目录，也不按最后修改时间替代创建时间。
- 可通过 `ASR_TEMP_RETENTION_HOURS` 调整保留时长，通过 `ASR_TEMP_CLEANUP_INTERVAL_MS` 调整扫描间隔。
- 服务账户必须对 `temp/` 有读、写和删除权限。

## 4. 麦克风诊断

1. 在首页选择实际输入设备，例如内置麦克风或蓝牙耳机麦克风；首次使用点击“刷新设备”并允许 Chrome 访问麦克风。
2. 开始录音后观察实时输入音量；正常说话时音量条应有变化。静音音频不会上传。
3. 可打开 `http://127.0.0.1:8080/microphone-test.html`，点击“启动检测”，观察音量条、RMS、Peak 和音轨状态。
4. 在检测页录制 5～10 秒并回放，确认文件确实有声音。检测页只在浏览器本地分析，不上传音频。
5. 蓝牙耳机连接或断开后刷新设备列表；不要只根据系统蓝牙连接状态判断 Chrome 实际输入设备。

## 5. 日志与故障排查

日志位于 `logs/offline-asr.log`，按天滚动并保留 15 天。日志通过 requestId 关联上传、解码和推理信息，但不记录完整识别文本或音频内容。

| 现象 | 检查方式 | 处理 |
|---|---|---|
| 页面无法打开 | 服务进程、8080 端口、`ASR_HOST` | 确认服务已启动且只绑定预期地址 |
| health 为 NOT_READY | 读取 `modelError` 和日志 | 修正 Native 目录、模型、Java 或 FFmpeg |
| `ASR_SHERPA_NATIVE_PATH 不是目录` | 环境变量和实际目录 | 设置绝对路径或使用默认 `external\sherpa-onnx-native` |
| `Native Runtime 文件缺失` | Native 目录根层两个 DLL | 扁平重新解压；不得保留 JAR 内子目录层级 |
| `UnsatisfiedLinkError` | 平台架构、VC++ Runtime、文件权限 | 使用 win-x64 Native JAR 并安装 VC++ Redistributable x64 |
| 缺少 `model.int8.onnx` | `ASR_MODEL_DIR` 和文件 | 重新解压固定模型包 |
| 缺少 `tokens.txt` | 模型目录根层 | 重新解压完整模型包，不改文件名 |
| health 中 `ffmpegAvailable=false` | `ASR_FFMPEG`、PATH、`ffmpeg -version` | 设置绝对路径或修正 PATH |
| 麦克风无法启动 | Chrome 站点权限、Windows 隐私设置 | 允许 localhost 使用麦克风并刷新设备 |
| 录音无声或设备错误 | 当前设备选择、实时音量、音轨状态 | 切换内置/蓝牙麦克风后重试 |
| WebM 可解码但无识别结果 | requestId 对应 RMS/Peak | 两者均为 0 时先排查采集端，不要先改模型 |
| 上传音频无法识别 | 格式、大小、时长、解码日志 | 使用支持格式并检查 FFmpeg 解码 |
| 临时文件未清理 | 清理日志、文件创建时间、目录权限 | 确认超过保留时长且服务账户可删除 |

## 6. 现场验收清单

- [ ] 应用包 SHA-256 校验通过
- [ ] Fat JAR 含 Java Wrapper，不含 Native Runtime
- [ ] `java -version` 显示 Java 11
- [ ] Native 目录根层含两个 DLL
- [ ] 模型目录含 `model.int8.onnx` 和 `tokens.txt`
- [ ] `ffmpeg -version` 成功
- [ ] health 返回 READY
- [ ] 真实音频接口返回非空文本
- [ ] Chrome 可枚举并切换麦克风
- [ ] 录音时实时输入音量有变化
- [ ] 麦克风和文件上传识别成功
- [ ] 临时文件命名和清理符合预期
- [ ] 断网重启后仍可识别
- [ ] 服务化（如需要）复验通过

每项标记“通过 / 失败 / 未执行”，并记录 Windows 版本、CPU、内存、Java、Chrome、模型及 Native 哈希、日志时间和 requestId。不得把 CI 构建、静态检查或其他平台实测结果替代现场验收。
