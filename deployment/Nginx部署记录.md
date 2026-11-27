# OfflineASR Nginx 部署记录

## 当前状态

- 日期：2026-11-27（Asia/Shanghai）
- 服务器：`ubuntu@124.221.243.130`
- 已完成：Nginx 安装、开机启用、HTTP 独立站点反向代理、配置语法检查、服务重载、回环及公网 Host 路由验证
- 未完成：域名 DNS 尚未生效、HTTPS 证书尚未申请、Spring Boot 仍监听 `0.0.0.0:8080`
- 现有服务：`offline-asr.service` 和 `cloudflared-quick-tunnel.service` 均保持运行

## 安装与配置

- Nginx：`nginx/1.18.0 (Ubuntu)`
- 系统包：`nginx 1.18.0-6ubuntu14.24`
- 服务状态：`enabled`、`active`
- 独立站点源文件：`deployment/nginx/offlineasr-http.conf`
- 服务器配置文件：`/etc/nginx/sites-available/offlineasr`
- 服务器启用链接：`/etc/nginx/sites-enabled/offlineasr`
- 安装前目标文件不存在，因此没有覆盖既有文件
- Ubuntu 默认站点 `/etc/nginx/sites-enabled/default` 未修改、未删除
- 上传上限：`60m`
- 连接超时：`30s`
- 发送/读取超时：`660s`
- 上游：`http://127.0.0.1:8080`

本地配置 SHA-256：

```text
5cc35be8847976a948bb4989a59ce43cad439875c804f1ebed9e2ed15345da8c
```

## 验证结果

```text
sudo nginx -t
nginx: configuration file /etc/nginx/nginx.conf syntax is ok
nginx: configuration file /etc/nginx/nginx.conf test is successful
```

服务器本机通过 Host 路由访问：

```text
HTTP/1.1 200
Content-Type: text/html;charset=UTF-8
Content-Length: 3429
```

公网通过 IP 和 `Host: asr.ai-buy.uk` 访问健康检查：

```text
HTTP/1.1 200
Server: nginx/1.18.0 (Ubuntu)
Content-Type: application/json

{"success":true,"applicationStatus":"READY","modelLoaded":true,"modelName":"SenseVoiceSmall INT8","inferenceDevice":"cpu","inferenceThreads":2,"ffmpegAvailable":true}
```

Nginx 重载后首次回环请求曾在旧 worker 切换期间命中默认页；待重载完成后再次验证，回环、`--resolve` 和公网 Host 路由均命中 offlineASR。

## 待处理事项

1. Cloudflare 配置 `asr` 的 A 记录到 `124.221.243.130`，建议使用“仅 DNS”模式；2026-11-27 11:17 查询仍为 `NXDOMAIN`。
2. DNS 生效后验证 `http://asr.ai-buy.uk/`。
3. 安装 Certbot 并申请 `asr.ai-buy.uk` 的 Let's Encrypt 证书，需要先确认证书通知邮箱。
4. 配置 HTTP 到 HTTPS 跳转并完成 Certbot 自动续期验证。
5. 经明确确认后，将 Spring Boot 监听地址从 `0.0.0.0` 收紧至 `127.0.0.1`，并关闭公网 8080 暴露。

## 日志

- `deployment/部署日志/23-nginx-stage1-inventory.log`
- `deployment/部署日志/24-nginx-stage1-details.log`
- `deployment/部署日志/25-nginx-install.log`
- `deployment/部署日志/26-nginx-pre-config-check.log`
- `deployment/部署日志/27-nginx-http-config.log`
- `deployment/部署日志/28-nginx-loopback-test.log`
