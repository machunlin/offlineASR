# Linux x64 部署手册

适用目标：Ubuntu 22.04 LTS、x86_64、Java 11。当前不承诺 Linux ARM64。

## 1. 系统依赖

```bash
sudo apt-get update
sudo apt-get install -y openjdk-11-jre-headless ffmpeg
java -version
ffmpeg -version
```

应确认 Java 主版本为 11，FFmpeg 命令可正常执行。

## 2. 构建部署包

在可联网的 Linux x64 构建机上检出项目分支，执行：

```bash
bash scripts/linux-package.sh
```

脚本下载 sherpa-onnx v2.0.3 官方 Linux x64 native JAR，并校验官方 SHA-256。校验失败会中止打包。随后执行 Maven clean test package，输出目录为 `deploy/linux-x64/`。

## 3. 部署文件

将 `deploy/linux-x64/` 内容复制到目标机，例如：

```bash
sudo mkdir -p /opt/offline-asr
sudo cp -a deploy/linux-x64/. /opt/offline-asr/
sudo chown -R "$USER":"$USER" /opt/offline-asr
```

将模型文件放入：

- `/opt/offline-asr/models/SenseVoiceSmall/model.int8.onnx`
- `/opt/offline-asr/models/SenseVoiceSmall/tokens.txt`

确认应用 JAR、模型文件和配置文件存在。

## 4. 前台启动与验收

```bash
cd /opt/offline-asr
chmod +x linux-start.sh
./linux-start.sh
```

另开终端执行：

```bash
curl --fail http://127.0.0.1:8080/api/asr/health
```

随后使用本机 Chrome 打开 `http://127.0.0.1:8080`，允许麦克风并完成实际语音识别。仅健康检查成功不能代表模型 JNI 已完成真实推理。

## 5. 内存验收

```bash
free -h
ps -eo pid,rss,vsz,%mem,comm,args | grep '[j]ava'
```

至少记录模型加载完成空闲状态、连续识别时峰值 RSS 和系统可用内存。当前 2 GB 内存主机不应直接设置激进的 JVM 堆上限或 systemd MemoryMax；`-Xmx` 不包含全部 native 内存。

## 6. 安全与 systemd

服务默认绑定 `127.0.0.1:8080`。该服务当前没有用户认证，不应直接绑定公网或不可信局域网地址。

`offline-asr.service` 是模板，安装前必须核对 Linux 用户、Java 路径、工作目录和权限。确认后再复制到 `/etc/systemd/system/` 并执行 systemctl daemon-reload。不要在未验证前直接启用。

## 7. 临时文件

音频与 PCM 文件统一保存在 `temp/`，名称为 `yyyyMMddHHmmss.扩展名`。后台每 10 分钟扫描，按文件名时间戳清理超过 4 小时的普通文件，不递归删除子目录。
