# Windows x64 部署手册

面向技术支持工程师。目标场景为应用和 Chrome 浏览器运行在同一台 Windows x64 主机。

## 1. 标准部署包

部署包应包含 app/offline-asr.jar、jdk/bin/java.exe、模型文件、ffmpeg/bin/ffmpeg.exe、config/application.yml、scripts/ 和 checksums.sha256。

Java Runtime、模型、FFmpeg 和 VC++ Runtime 的来源及再分发许可必须由发布人员核验。不得从来源不明的网站下载或替换。

## 2. 构建应用包

在 Windows x64 构建机准备 Java 11、Maven，并将官方 sherpa-onnx v2.0.3 Windows x64 原生 JAR 放入项目 lib/，完成 SHA-256 校验后执行：

~~~bat
scripts\windows-package.bat
~~~

输出目录为 dist\windows-x64\。deploy\windows-x64\ 仅保存模板文件。

## 3. 补齐离线运行资产

将经许可核验的 Java 11 x64 Runtime 放入 dist\windows-x64\jdk\；将模型放入 dist\windows-x64\models\SenseVoiceSmall\；将 Windows x64 FFmpeg 放入 dist\windows-x64\ffmpeg\bin\ffmpeg.exe。

确认目标机所需 Microsoft Visual C++ Redistributable x64 已安装。若计划随包分发，必须先核对其再分发许可。

## 4. 生成并验证完整性清单

在 Windows PowerShell 中执行：

~~~powershell
cd dist\windows-x64
powershell -ExecutionPolicy Bypass -File .\scripts\windows-generate-checksums.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\windows-verify-package.ps1
~~~

清单覆盖部署目录中的全部文件（不包含清单自身）。验证会检查必要资产、Java 11 x64、FFmpeg、SHA-256、重复路径及未纳入清单的文件。

验证通过后，在当前目录执行以下命令生成交付压缩包：

~~~powershell
Compress-Archive -Path .\\* -DestinationPath ..\\OfflineASR-windows-x64.zip
~~~

生成清单后不得修改或替换文件。

## 5. 客户机部署

1. 解压到固定目录，例如 C:\offline-asr。
2. 在 PowerShell 中执行 scripts\windows-verify-package.ps1。
3. 执行 scripts\windows-start.bat 前台启动。
4. 浏览器访问 http://127.0.0.1:8080。
5. 执行 scripts\windows-status.bat，确认 READY、模型加载和 FFmpeg 状态。
6. 使用真实音频完成接口识别、Chrome 麦克风录音和文件上传识别。
7. 断网后重启服务，再完成一次真实音频识别。

前台验收通过后，如客户需要无人值守运行，再按现场授权安装 WinSW 服务。不要将安装服务作为默认步骤。

## 6. 服务与卸载

服务安装、状态和卸载脚本位于 scripts/。安装前检查 WinSW 版本、配置文件、运行用户和目录权限。卸载服务不应删除模型、配置和日志。

## 7. 安全与验证边界

默认仅监听 127.0.0.1:8080，服务没有用户认证，不要直接暴露公网或不可信局域网。

部署包静态校验不替代目标 Windows 主机的 JNI 加载、真实 Chrome 麦克风、断网和性能测试。每项现场验收标记为通过、失败或未执行。
