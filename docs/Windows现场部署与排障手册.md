# Windows 客户现场部署与排障手册

## 1. 部署目标与边界

- Windows 10/11 x64 主机；Chrome 浏览器与识别服务运行在同一台主机。
- 默认监听地址为 `127.0.0.1:8080`，只允许本机访问。不要为了浏览器访问而改成 `0.0.0.0`。
- 应用使用 Java 11 x64、sherpa-onnx v2.0.3 Windows x64 JNI、SenseVoiceSmall INT8 模型和 FFmpeg。
- 运行时不依赖网络；首次部署前需将完整部署包复制到客户主机。

## 2. 标准目录

```text
offline-asr/
  app/offline-asr.jar
  jdk/bin/java.exe
  models/SenseVoiceSmall/model.int8.onnx
  models/SenseVoiceSmall/tokens.txt
  ffmpeg/bin/ffmpeg.exe
  config/application.yml
  scripts/
  logs/
  temp/
```

部署包中的 Java 必须是 64 位 Java 11 Runtime；Windows 原生 JAR 必须是官方 sherpa-onnx v2.0.3 Windows x64 版本。不能把 macOS ARM64 原生库放进 Windows 包。

## 3. 现场部署步骤

1. 将完整部署目录复制到本机固定路径，例如 `C:\\OfflineASR`。目录路径建议只使用英文、数字和短横线。
2. 确认杀毒软件没有隔离 JNI DLL、FFmpeg 或模型文件。
3. 在 PowerShell 中进入部署目录，执行：

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\\scripts\\windows-verify-package.ps1
   ```

   此步骤检查必需文件、Java/FFmpeg 可执行性及 SHA-256。若部署包首次组装，需要先由发布人员生成 `checksums.sha256`，不能在客户现场用未知文件重新生成后再把它当作可信发布校验。
4. 前台启动：

   ```bat
   scripts\\windows-start.bat
   ```

5. 在同一台主机的 Chrome 打开 `http://127.0.0.1:8080/`，检查页面状态。
6. 点击开始录音，说一段 5–10 秒的普通话，点击结束录音，确认结果正确；再连续执行第二次，确认按钮恢复。
7. 选择一段 WAV 或 MP3 音频文件，点击“上传并识别”，确认页面显示识别文本、音频时长和耗时。
8. 访问 `http://127.0.0.1:8080/api/asr/health`，要求 HTTP 200 且 `applicationStatus=READY`。
9. 确认 `logs/offline-asr.log` 正常写入，停止前台进程按 Ctrl+C。

## 4. 服务化运行

必须先完成前台验收。若客户要求开机自启，再按组织的软件部署规范使用 WinSW 注册 Windows 服务。服务包装器需与部署包一同经过安全审核和哈希校验；不要在客户现场临时下载未知来源的 EXE。

服务配置的工作目录必须指向部署根目录，标准输出和标准错误应重定向到 `logs`。服务启动后再次检查 health、日志和 Chrome 麦克风录音。

## 5. 网络访问控制

默认配置：

```yaml
server:
  address: ${ASR_HOST:127.0.0.1}
  port: ${ASR_PORT:8080}
```

- 默认不需要改配置，Chrome 通过 localhost 访问本机服务。
- 只有业务明确要求其他设备访问时才设置 `ASR_HOST=0.0.0.0`，并同步配置 Windows 防火墙白名单、访问控制和风险审批。
- 不建议将本机 ASR 服务直接暴露到公网或办公网。

## 6. 日志与排障

日志位置：`logs/offline-asr.log`；按天滚动，压缩归档，最多保留 15 天。每次请求通过 requestId 关联日志，包含原始文件名、上传大小、临时输入文件路径、PCM 输出路径、采样率、采样数、音频时长、RMS、峰值、削波样本数、解码耗时、推理耗时及异常堆栈。日志不记录音频内容和完整识别文本。请求临时目录在请求结束时清理，日志中的路径用于定位请求处理过程。

| 现象 | 检查方式 | 处理 |
|---|---|---|
| 页面无法打开 | 执行 `scripts/windows-status.bat`；检查端口 8080 | 确认启动窗口/服务状态、端口占用和绑定地址 |
| health 返回 NOT_READY | 查看日志中的模型目录、JNI 初始化错误 | 核对模型文件、Windows x64 native JAR、Java x64 和 VC++ Runtime |
| 找不到 FFmpeg | 检查 `ffmpeg/bin/ffmpeg.exe` 并执行 `-version` | 重新部署完整 FFmpeg 目录 |
| 麦克风无法启动 | Chrome 地址栏站点权限、Windows 麦克风隐私设置 | 允许 localhost 使用麦克风，检查系统输入设备 |
| 上传音频无法识别 | 按 requestId 查看文件路径、上传字节数、FFmpeg 解码日志 | 确认文件格式可解码、大小不超过 50 MB、时长不超过 60 秒 |
| 识别结果过短或为空 | 查看 audioSeconds、sampleCount、RMS、peak、clippedSamples 和推理耗时 | RMS 接近 0 表示音频可能接近静音；峰值/削波异常时检查输入增益；音频正常时再分析模型推理 |
| 60 秒后自动停止 | 页面倒计时和服务端 max-duration-seconds | 属于预期限制；超过时长的请求由服务端拒绝 |
| 服务启动即退出 | 检查日志和 Windows 事件查看器 | 核对 Java 版本、DLL 依赖、目录权限和配置文件 |

## 7. 现场验收清单

- [ ] Windows x64、Java 11 x64、FFmpeg 版本核验通过
- [ ] 发布 SHA-256 校验通过
- [ ] health 返回 READY
- [ ] Chrome 允许麦克风，5–10 秒普通话识别正确
- [ ] 连续录音至少 3 次均可正常开始和结束
- [ ] 上传 WAV/MP3 文件可显示识别文本和耗时
- [ ] 60 秒倒计时到期后自动停止
- [ ] 超过 60 秒的服务端请求被拒绝
- [ ] 断开互联网后重新启动，仍可识别
- [ ] 日志按天写入，滚动与 15 天保留策略生效
- [ ] 服务停止、启动和重启流程通过（如部署为 Windows 服务）

静态脚本检查和 GitHub Actions Windows 构建不等于客户机器实机验收。每个客户现场仍需完成上述麦克风、文件上传、JNI、断网和服务测试，并记录 Windows 版本、CPU、Java 版本、模型哈希和结果。
