# offlineASR 远程服务器 Nginx + HTTPS 引导式安装配置任务

## 一、任务目标

在现有腾讯云 Ubuntu 服务器上安装并配置 Nginx，为 offlineASR 项目配置域名、HTTPS 和反向代理。

要求采用引导式交互安装模式。

Codex 负责：
- 检查服务器环境。
- 提供安装和配置方案。
- 执行经过确认的操作。
- 生成配置文件。
- 验证部署结果。
- 更新项目部署文档。

遇到需要用户决策、授权或可能影响现有服务的操作时，必须暂停并等待用户确认。

不得一次性执行所有命令，不得未经确认修改服务器。

## 二、现有环境

### 服务器信息

| 项目 | 配置 |
|---|---|
| 云服务商 | 腾讯云轻量应用服务器 |
| 操作系统 | Ubuntu 22.04.5 LTS |
| CPU 架构 | x86_64 |
| 内存 | 2GB |
| 公网 IP | 124.221.243.130 |
| 项目 | offlineASR |
| Spring Boot 端口 | 8080 |
| 当前访问地址 | http://124.221.243.130:8080/ |

### 域名信息

| 项目 | 配置 |
|---|---|
| 主域名 | ai-buy.uk |
| 现有网站 | www.ai-buy.uk |
| 新增二级域名 | asr.ai-buy.uk |
| DNS 类型 | A |
| DNS 记录值 | 124.221.243.130 |

正式访问地址：

https://asr.ai-buy.uk/

### 目标技术架构

用户浏览器
    ↓ HTTPS 443
Nginx
    ↓ HTTP 127.0.0.1:8080
Spring Boot offlineASR

HTTP 80 负责跳转 HTTPS，以及 Let's Encrypt HTTP-01 证书验证。

最终公网只开放：
- 80：HTTP
- 443：HTTPS
- SSH：保持现有远程管理配置

8080 不再对公网开放。

## 三、核心交互规则

必须严格遵守以下规则：

### 1. 引导式执行

按照阶段逐步推进，每个阶段完成后：
1. 展示执行内容。
2. 展示实际执行结果。
3. 说明是否通过。
4. 说明下一阶段准备执行什么。
5. 等待用户确认后继续。

### 2. 必须交互确认的操作

以下操作不得自动执行，必须先向用户说明影响并等待明确确认：

- 修改腾讯云安全组。
- 修改服务器防火墙规则。
- 安装或卸载系统软件。
- 修改现有 Nginx 配置。
- 修改 Spring Boot 监听地址。
- 修改 Spring Boot 启动参数。
- 重启 Spring Boot。
- 重启 Nginx。
- 修改公网端口开放策略。
- 申请 SSL 证书。
- 修改域名 DNS 配置。
- 删除或覆盖已有配置文件。
- 任何可能影响其他业务的操作。

特别注意：
仅仅展示执行命令不代表获得用户授权。

### 3. 安全要求

1. 所有配置修改之前必须备份。
2. 不得覆盖其他业务的 Nginx 站点配置。
3. 不得删除默认站点，除非确认其无业务用途并获得用户授权。
4. 不得修改 SSH 端口及 SSH 访问规则。
5. 不得在 Git 仓库提交 SSL 私钥。
6. 不得在日志、报告中输出证书私钥。
7. 不得擅自更改 Spring Boot 业务代码。
8. 不得因为配置失败而反复盲目重试。
9. 不得声称未经验证的功能已经成功。
10. 不得使用 Docker 部署 Nginx 或 Certbot。

## 四、具体实施阶段

### 阶段一：服务器环境检查

首先只执行只读检查，不做任何修改。

检查项目：

1. Ubuntu 版本和 CPU 架构。
2. 当前内存及磁盘使用情况。
3. Nginx 是否已安装及版本。
4. Certbot 是否已安装及版本。
5. 80、443、8080 端口占用情况。
6. Spring Boot 运行状态。
7. Spring Boot 实际监听地址。
8. 当前 Nginx 配置目录和站点。
9. 是否存在其他业务网站。
10. 腾讯云安全组配置（如果当前权限能够查询）。
11. DNS 是否已经解析到 124.221.243.130。
12. 当前服务器防火墙状态。

建议执行：

```bash
cat /etc/os-release
uname -m
free -h
df -h

nginx -v
certbot --version

sudo ss -lntp

ps -ef | grep -i '[o]fflineasr'

dig +short asr.ai-buy.uk

sudo ufw status verbose

systemctl list-units --type=service --state=running
```

根据实际情况补充检查。

注意：
部分命令可能因为未安装而失败，这不代表整个检查失败。

完成后输出环境检查报告，并指出：
- 已满足的条件。
- 尚未满足的条件。
- 可能存在的风险。
- 下一阶段操作。

等待我确认后进入阶段二。

### 阶段二：DNS 和安全组确认

检查：

```bash
dig +short asr.ai-buy.uk
```

预期：

```text
124.221.243.130
```

如果 DNS 尚未生效：

1. 告诉我需要在 DNS 服务商后台新增的记录。
2. 等待我确认已经配置。
3. 再次检查 DNS。
4. 未正确解析时，不允许申请证书。

腾讯云安全组要求：

| 协议 | 端口 | 来源 |
|---|---|---|
| TCP | 80 | 0.0.0.0/0 |
| TCP | 443 | 0.0.0.0/0 |

SSH 原有规则保持不变。

如果无法直接检查或修改腾讯云安全组，提供清晰的控制台操作指导。

如果需要修改安全组，必须等待我确认。

阶段完成后等待我确认。

### 阶段三：安装 Nginx

如果 Nginx 尚未安装，先展示安装计划并等待确认。

使用 Ubuntu apt：

```bash
sudo apt update
sudo apt install -y nginx
```

配置开机启动：

```bash
sudo systemctl enable nginx
sudo systemctl start nginx
```

检查：

```bash
nginx -v
sudo nginx -t
sudo systemctl status nginx
```

如果已经安装，则不重复安装，先检查现有配置。

注意：
- 不修改全局 nginx.conf。
- 不覆盖已有站点。
- 不删除 default 配置。
- 使用独立站点配置。

完成后等待我确认。

### 阶段四：配置 HTTP 反向代理

创建独立配置：

```text
/etc/nginx/sites-available/offlineasr
```

启用配置：

```text
/etc/nginx/sites-enabled/offlineasr
```

配置目标：

域名：asr.ai-buy.uk

代理地址：

http://127.0.0.1:8080

配置要求：

1. 保留原始 Host。
2. 传递 X-Real-IP。
3. 传递 X-Forwarded-For。
4. 传递 X-Forwarded-Proto。
5. 上传限制设置为 60MB。
6. 连接超时 30 秒。
7. 发送和读取超时设置为 660 秒。
8. 不擅自增加 SSE、WebSocket 配置。
9. 不引入不必要的缓存策略。

初始阶段只配置 HTTP，不配置 HTTPS。

创建文件前：
- 检查目标文件是否已存在。
- 备份已有文件。
- 不覆盖其他业务配置。

启用配置之前：

```bash
sudo nginx -t
```

通过后再等待我确认是否重新加载 Nginx。

重新加载：

```bash
sudo systemctl reload nginx
```

验证：

```bash
curl -I http://asr.ai-buy.uk/
```

确认 Nginx 能正确代理到 Spring Boot。

如果返回 502，先检查 Spring Boot，不要直接进入证书申请阶段。

完成后等待我确认。

### 阶段五：安装 Certbot

采用 Ubuntu apt 软件源安装：

```bash
sudo apt update

sudo apt install -y certbot python3-certbot-nginx
```

检查：

```bash
certbot --version
```

如果已经安装，检查版本和现有证书，不要重复安装。

安装前向我展示安装计划，等待确认。

完成后等待我确认。

### 阶段六：申请 Let's Encrypt 免费 SSL 证书

证书申请条件：

1. asr.ai-buy.uk 已解析到 124.221.243.130。
2. 服务器公网 80 端口能够访问。
3. Nginx 正常运行。
4. Nginx HTTP 站点配置正常。
5. 域名没有被其他业务错误占用。

申请命令：

```bash
sudo certbot --nginx -d asr.ai-buy.uk
```

申请过程中：

- 使用我提供的有效邮箱。
- 遵守 Let's Encrypt 服务条款。
- 选择 HTTP 自动跳转 HTTPS。
- 不申请其他无关域名。
- 不使用未经确认的邮箱。

如果需要邮箱，先询问我，不得自行填写虚构邮箱。

证书申请成功后检查：

```bash
sudo certbot certificates
```

证书路径预期为：

```text
/etc/letsencrypt/live/asr.ai-buy.uk/fullchain.pem

/etc/letsencrypt/live/asr.ai-buy.uk/privkey.pem
```

绝对禁止输出或泄露私钥内容。

如果证书申请失败：
- 保存错误信息。
- 分析 DNS、端口、Nginx 和域名验证问题。
- 给出解决方案。
- 等待我确认后重试。

完成后等待我确认。

### 阶段七：检查最终 Nginx 配置

最终应当具备：

HTTP：

```nginx
server {
    listen 80;
    server_name asr.ai-buy.uk;

    return 301 https://$host$request_uri;
}
```

HTTPS：

```nginx
server {
    listen 443 ssl;
    server_name asr.ai-buy.uk;

    ssl_certificate /etc/letsencrypt/live/asr.ai-buy.uk/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/asr.ai-buy.uk/privkey.pem;

    ssl_protocols TLSv1.2 TLSv1.3;

    client_max_body_size 60m;

    location / {
        proxy_pass http://127.0.0.1:8080;

        proxy_http_version 1.1;

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        proxy_connect_timeout 30s;
        proxy_send_timeout 660s;
        proxy_read_timeout 660s;

        proxy_buffering on;
    }
}
```

以上是目标配置结构，不允许在不检查 Certbot 生成配置的情况下直接覆盖。

整理配置时：
1. 备份当前文件。
2. 保留 Certbot 管理所需内容。
3. 避免重复 server_name 和 listen 定义。
4. 避免影响其他站点。
5. 执行 nginx -t。
6. 通过后等待我确认再 reload。

### 阶段八：证书自动续期

检查 Certbot 定时任务：

```bash
systemctl list-timers | grep certbot

systemctl status certbot.timer
```

如果未启用，展示操作计划并等待确认。

启用：

```bash
sudo systemctl enable --now certbot.timer
```

模拟续期：

```bash
sudo certbot renew --dry-run
```

必须确认模拟续期成功。

必要时配置续期后的 Nginx reload hook：

```bash
sudo mkdir -p /etc/letsencrypt/renewal-hooks/deploy
```

创建脚本：

```bash
sudo nano /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh
```

内容：

```bash
#!/bin/bash

/usr/sbin/nginx -t && /usr/bin/systemctl reload nginx
```

设置权限：

```bash
sudo chmod 755 /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh
```

创建前先检查是否已有等效钩子，避免重复配置。

完成后等待我确认。

### 阶段九：服务器安全调整

目标：

- 公网开放 80、443。
- 8080 不允许公网访问。
- Spring Boot 尽量只监听 127.0.0.1。
- SSH 规则保持不变。

先检查：

```bash
sudo ss -lntp
```

如果 Spring Boot 当前监听 0.0.0.0:8080：

1. 说明调整监听地址的影响。
2. 检查应用配置及启动方式。
3. 检查是否存在其他本机服务依赖。
4. 提供修改方案。
5. 等待我确认。

修改 Spring Boot 监听地址或腾讯云安全组时，必须获得我的明确确认。

禁止因为安全加固导致现有服务不可用。

### 阶段十：完整验收

#### 1. HTTP 跳转测试

```bash
curl -I http://asr.ai-buy.uk/
```

预期跳转：

```text
301
Location: https://asr.ai-buy.uk/
```

#### 2. HTTPS 页面测试

```bash
curl -I https://asr.ai-buy.uk/
```

预期返回正常 HTTP 响应。

#### 3. 证书测试

```bash
sudo certbot certificates
```

#### 4. SSL 链测试

```bash
echo | openssl s_client \
-connect asr.ai-buy.uk:443 \
-servername asr.ai-buy.uk 2>/dev/null \
| openssl x509 -noout -subject -issuer -dates
```

#### 5. 服务状态

```bash
sudo systemctl status nginx

sudo ss -lntp
```

#### 6. 浏览器麦克风 API 测试

访问：

https://asr.ai-buy.uk/

Chrome Console 执行：

```javascript
console.table({
    页面地址: location.href,
    安全上下文: window.isSecureContext,
    mediaDevices: typeof navigator.mediaDevices,
    getUserMedia: typeof navigator.mediaDevices?.getUserMedia
});
```

预期：

- 页面协议为 HTTPS。
- isSecureContext 为 true。
- mediaDevices 为 object。
- getUserMedia 为 function。

#### 7. 业务功能测试

逐项检查：

1. 首页正常加载。
2. 麦克风设备列表正常获取。
3. 麦克风授权正常。
4. 音量检测正常。
5. 开始录音正常。
6. 结束录音正常。
7. 音频文件生成正常。
8. 音频上传正常。
9. 语音识别正常。
10. 识别结果正常显示。
11. 清空结果正常。
12. 浏览器 Console 没有新增错误。

注意：HTTPS 成功不代表所有业务功能成功。如果存在前端 API 硬编码 HTTP 地址，需要单独定位和报告，未经授权不得修改业务代码。

## 五、项目文档更新

检查现有部署文档，更新相关内容。

至少包括：

1. Ubuntu 安装 Nginx。
2. Nginx 配置文件。
3. 域名 DNS 配置。
4. 腾讯云安全组配置。
5. Certbot 安装。
6. SSL 证书申请。
7. 证书续期。
8. 服务启动和重启。
9. 日志查看。
10. 常见故障处理。
11. HTTPS 功能验收。

文档要求：

- 使用中文。
- 提供完整命令。
- 提供完整配置文件。
- 面向客户现场技术支持人员。
- 保留原有 Windows、macOS、Linux 部署说明。
- 不记录 SSL 私钥。
- 不记录真实服务器密码和其他敏感信息。

## 六、Git 操作要求

如果涉及项目文档修改：

1. 先检查当前分支和工作区状态。
2. 提交前必须先拉取最新代码，避免冲突。
3. 不覆盖用户已有修改。
4. 不提交证书、私钥、服务器密码等敏感信息。
5. 中文编写提交说明。
6. 未经授权不执行 push、merge。

## 七、最终交付报告

完成后输出：

### 1. 环境检查报告
- 操作系统。
- Nginx 版本。
- Certbot 版本。
- 端口状态。
- Spring Boot 状态。

### 2. 部署结果
- 域名。
- HTTPS 访问地址。
- Nginx 配置文件路径。
- SSL 证书路径。
- 证书有效期。

### 3. 自动续期
- 定时任务状态。
- dry-run 验证结果。

### 4. 安全状态
- 公网开放端口。
- Spring Boot 监听地址。
- SSH 访问状态。

### 5. 功能验收
逐项报告 12 项业务功能的实际测试结果。

### 6. 文件修改
- 修改的文件。
- 新增的文件。
- 配置备份位置。
- 文档更新内容。

### 7. 未解决问题
明确指出尚未完成的事项和原因。

## 八、执行开始指令

现在开始阶段一：服务器环境检查。

只允许执行只读检查命令，不允许修改任何服务器配置。

检查结束后向我展示完整检查报告，等待我确认，再进入下一阶段。

整个任务必须采用交互式分阶段执行模式，不得跳过确认步骤。