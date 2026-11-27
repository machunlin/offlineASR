# AI Agent 部署任务入口

本文是自动化部署的唯一入口。AI Agent 应先阅读本文，再按目标操作系统选择对应的正式部署手册。不要凭经验猜测模型下载地址、原生库版本、文件路径或未提供的运行参数。

## 一句话任务指令

将下面这句话交给 AI Agent：

> 请阅读仓库中的 docs/AI_AGENT_DEPLOYMENT.md，识别当前目标机器的操作系统和 CPU 架构，严格按照对应平台的部署手册完成 OfflineASR 部署；执行构建/安装、启动、健康检查和一次真实音频识别验证，最后汇报执行命令、实际结果、日志位置及未通过项目。未经授权不要开放公网/局域网访问、修改防火墙、安装系统服务或覆盖现有配置。

## 1. 执行原则

1. 先检查系统、CPU 架构、Java、Maven（仅构建机需要）、FFmpeg、磁盘和内存。
2. 先确认工作目录、Git 分支和工作区状态；不得覆盖用户已有文件。
3. 生产部署优先使用正式发布包，不要求客户现场安装 Maven、Python 或开发工具。
4. 本项目默认只监听 127.0.0.1:8080，不得擅自改成 0.0.0.0。
5. 模型必须包含 model.int8.onnx 和 tokens.txt；缺少模型时停止并报告，不得下载来源不明的模型。
6. 平台原生库必须匹配操作系统和 CPU 架构。不得混用 macOS、Windows、Linux 原生库。
7. 不得把健康检查成功等同于真实识别成功。
8. 不得把 CI 或静态包检查写成目标机器实机验收通过。
9. 修改系统服务、防火墙、端口、现有配置或重启其他业务前，先说明影响并取得用户明确授权。
10. 不输出密码、密钥、个人录音或完整识别文本到部署报告。

## 2. 平台选择

| 检测结果 | 适用平台 | 正式指南 |
|---|---|---|
| Windows 10/11，x64 | Windows x64 | [Windows 部署手册](03-Windows部署手册.md) |
| Linux，x86_64（当前指南以 Ubuntu 22.04 为基准） | Linux x64 | [Linux 部署手册](04-Linux部署手册.md) |
| macOS，ARM64 | macOS ARM64 | [macOS 运行指南](05-macOS运行指南.md) |

不支持的操作系统或 CPU 架构应停止自动部署并报告，不得套用其他平台的原生库。

## 3. Windows x64

**目标场景：** 应用和 Chrome 浏览器运行在同一台 Windows 主机，使用本机麦克风。

- 生产部署手册：[Windows 部署手册](03-Windows部署手册.md)
- 现场排障手册：[Windows 现场部署与排障](Windows现场部署与排障手册.md)
- 构建机打包入口：scripts\\windows-package.bat
- 目标机启动入口：部署包中的 scripts\\windows-start.bat
- 健康检查：http://127.0.0.1:8080/api/asr/health

执行要求：
1. 确认 Windows x64 原生 JAR 已从官方 sherpa-onnx v2.0.3 Release 获取并完成 SHA-256 校验。
2. 准备 Java 11 x64 Runtime、SenseVoiceSmall INT8 模型、Windows x64 FFmpeg 和所需 VC++ Runtime。
3. 在构建机执行 Windows 打包脚本；脚本若提示缺少原生 JAR，立即停止。
4. 将完整部署目录复制到目标机，先执行发布包校验脚本，再前台启动。
5. 前台运行和真实识别通过后，才可在用户授权下安装 WinSW 服务。
6. 在同一台主机的 Chrome 中选择实际麦克风，完成录音识别和音频文件上传识别。

## 4. Linux x64

**当前基准：** Ubuntu 22.04 LTS、x86_64、Java 11、FFmpeg。

- 正式部署手册：[Linux 部署手册](04-Linux部署手册.md)
- 构建入口：bash scripts/linux-package.sh
- 启动入口：部署目录中的 ./linux-start.sh
- 健康检查：curl --fail http://127.0.0.1:8080/api/asr/health

执行要求：
1. 在可联网的 Linux x64 构建机运行打包脚本；脚本负责获取并校验官方 Linux x64 原生 JAR。
2. 确认生成的 deploy/linux-x64/ 包含应用 JAR、启动脚本和配置。
3. 将模型放入部署目录的 models/SenseVoiceSmall/。
4. 目标机安装/确认 Java 11 和 FFmpeg，按 Linux 手册启动。
5. 健康检查通过后，使用真实音频执行一次 POST /api/asr 识别。
6. 记录模型加载后的进程 RSS、识别峰值内存和可用内存；2 GB 主机尤其不能仅根据 JVM -Xmx 判断总内存。

systemd 服务文件是模板。未经检查运行用户、路径、权限并获得授权，不得直接安装或启用。

## 5. macOS ARM64

macOS ARM64 当前主要用于开发和本机验证：

1. 确认 JDK 11、Maven、FFmpeg 和模型文件。
2. 执行 bash scripts/mac-start.sh。
3. 访问 http://127.0.0.1:8080。
4. 完成健康检查和真实音频识别。

不要将 macOS ARM64 原生库放入 Windows 或 Linux 包。

## 6. 通用验收

部署后至少执行以下检查：

1. 首页可打开：http://127.0.0.1:8080/。
2. 健康检查：GET /api/asr/health，确认 applicationStatus=READY、modelLoaded=true、ffmpegAvailable=true。
3. 使用一段已授权的真实音频调用 POST /api/asr，multipart 字段名为 file，确认 HTTP 成功且返回非空识别文本。
4. 在浏览器允许麦克风后完成真实录音识别；确认选中的输入设备正确、实时音量有变化。
5. 上传一份受支持的音频文件，确认上传识别成功。
6. 检查应用日志、临时文件清理和服务进程状态。
7. 如要求离线验收，断开网络后重启服务，再重复健康检查和真实音频识别。

示例（将路径替换为实际音频文件）：

~~~bash
curl --fail -X POST http://127.0.0.1:8080/api/asr \
  -H 'Accept: application/json' \
  -F 'file=@./sample.wav'
~~~

仅在 health 返回 READY 后再进行识别。不要在报告中粘贴真实客户语音内容。

## 7. 最终报告格式

Agent 完成后只需给出：
- 目标系统 / CPU 架构；
- 部署目录和应用版本；
- 执行过的关键命令；
- 构建、启动和 health 结果；
- 真实音频识别是否通过（只报告通过/失败，不披露语音文本）；
- 麦克风、上传、断网和服务化验收结果；
- 日志位置、资源占用；
- 未完成事项及阻断原因。

每项标记为“通过 / 失败 / 未执行”，不得推测。
