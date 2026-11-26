# Windows 客户现场部署与排障手册

## 1. 部署目标与边界

- Windows 10/11 x64 主机；Chrome 浏览器与识别服务运行在同一台主机。
- 默认监听地址为 `127.0.0.1:8080`，只允许本机访问。
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

## 3. 临时音频文件管理

- 原始上传音频和 FFmpeg 解码后的 PCM 文件统一保存在部署目录的 `temp/`，所有文件位于同一层目录，不为请求创建子目录。
- 文件名格式为 `yyyyMMddHHmmss.扩展名`，例如 `20261126163025.wav` 和 `20261126163026.pcm`。
- 文件在请求结束后继续保留。后台清理任务每 10 分钟扫描一次，仅删除文件系统记录的创建时间距当前时间超过 4 小时的普通文件。
- 清理任务不递归删除子目录，也不会按最后修改时间替代创建时间判断。
- 默认保留时长为 4 小时，可通过 `ASR_TEMP_RETENTION_HOURS` 调整；扫描间隔可通过 `ASR_TEMP_CLEANUP_INTERVAL_MS` 调整。
- Windows 部署时，服务账户必须对 `temp/` 目录拥有读、写和删除权限。

## 4. 现场部署与验收

1. 将完整部署目录复制到客户主机固定路径，例如 `C:\\OfflineASR`。
2. 核对 Java 11 x64、Windows x64 JNI、模型文件和 FFmpeg。
3. 执行 `scripts\\windows-verify-package.ps1` 完成部署包校验。
4. 执行 `scripts\\windows-start.bat` 启动服务。
5. 在同一台主机 Chrome 打开 `http://127.0.0.1:8080/`。
6. 如需单独排查麦克风，打开 `http://127.0.0.1:8080/index-test.html`，点击“启动检测”，对着麦克风说话，观察实时音量条、RMS、Peak 和音轨状态。
7. 在检测页录制 5～10 秒并回放，确认录音文件确实有声音。检测页只在浏览器本地分析和回放，不会上传音频，也不会调用 ASR 接口。
8. 检测通过后回到首页，测试麦克风录音识别和 WAV/MP3 文件上传识别。
9. 检查 `logs/offline-asr.log`，并确认 `temp/` 下原始音频和 PCM 文件命名符合时间戳格式。
10. 验证新文件在 4 小时内保留；可在测试环境将保留时长临时调短，验证过期清理逻辑后恢复为 4 小时。

## 5. 日志与排障

日志位置：`logs/offline-asr.log`，按天滚动并保留 15 天。通过 requestId 关联请求日志，包含上传文件名、上传大小、临时音频路径、采样率、音频时长、音频质量指标、解码耗时、推理耗时和异常堆栈。日志不记录音频内容和完整识别文本。

| 现象 | 检查方式 | 处理 |
|---|---|---|
| 页面无法打开 | 检查服务进程和端口 8080 | 确认服务状态和绑定地址 |
| health 返回 NOT_READY | 查看模型目录和 JNI 初始化错误 | 核对模型、Windows x64 native JAR、Java x64 和 VC++ Runtime |
| 找不到 FFmpeg | 检查 `ffmpeg/bin/ffmpeg.exe` | 重新部署完整 FFmpeg 目录 |
| 麦克风无法启动 | 检查 Chrome 站点权限和 Windows 麦克风隐私设置 | 允许 localhost 使用麦克风 |
| 录音文件能上传但回放无声 | 按 requestId 查看 FFmpeg 解码完成日志中的 RMS 和峰值；若均为 0，说明音频是数字静音 | 检查 Chrome 麦克风权限、输入设备选择、Windows 麦克风隐私和系统静音状态；确认录音时输入音轨未 muted 且 readyState 为 live |
| WebM 可解码但没有识别结果 | 用 FFmpeg 将 WebM 解码，核对 PCM 是否全零 | 容器/Opus 解码成功不代表录到声音；RMS=0 且峰值=0 时应先排查采集端，不要先调整模型或 FFmpeg |
| 上传音频无法识别 | 按 requestId 查看上传和 FFmpeg 解码日志 | 确认格式、文件大小和音频时长 |
| 临时文件未清理 | 检查清理任务日志、文件创建时间和目录权限 | 确认创建时间已超过 4 小时且服务账户有删除权限 |

## 6. 现场验收清单

- [ ] health 返回 READY
- [ ] Chrome 麦克风录音识别正确
- [ ] WAV/MP3 文件上传识别正确
- [ ] 临时文件全部位于同一个 temp 目录
- [ ] 文件名格式为 `yyyyMMddHHmmss.扩展名`
- [ ] 创建时间未超过 4 小时的文件保留
- [ ] 创建时间超过 4 小时的文件被清理任务删除
- [ ] 清理任务不递归删除子目录
- [ ] 断网后重新启动仍可识别

自动化测试不能替代目标客户 Windows 主机上的 JNI、Chrome 麦克风、文件上传、断网启动和性能验收。 静态脚本检查和 GitHub Actions
Windows 构建不等于客户机器实机验收。每个客户现场仍需完成上述麦克风、文件上传、JNI、断网和服务测试，并记录 Windows 版本、CPU、Java
版本、模型哈希和结果。

## 8. 2026-11-26 发布前验收提醒

当前分支已完成 macOS 本机 JAR、HTTP 有效 WAV 和 Windows CI 静态构建验证，但以下项目仍是发布阻断项，详见
`docs/优化调整建议报告.md`：

1. `scripts/mac-start.sh` 当前存在语法错误且没有可执行位，不能作为已验证启动方式。
2. 需求 V2.2 的录音上限仍为 600 秒，当前前端和后端默认值被改为 60 秒；未经正式需求变更不得作为验收基线。
3. Windows 外部配置仍含 `max-duration-seconds: 600`，WinSW 配置未设置 `ASR_MAX_DURATION_SECONDS`，前台脚本却设置
   60，服务与前台行为不一致。
4. 外部配置硬编码 `server.address: 127.0.0.1`，会屏蔽 `ASR_HOST` 覆盖；在明确批准局域网访问前仍应保持本机监听。
5. 新增的 `windows-diagnose.bat` 尚未被 `windows-package.bat` 复制进发布目录；发布包和哈希覆盖必须在 Windows 实机前补齐。
6. Chrome 真麦克风、5 条真实普通话语料、断网重启、WinSW 服务、VC++ Runtime 和目标机性能仍未验证，不得用本手册中的静态检查替代实机结论。
