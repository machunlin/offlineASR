@echo off
setlocal EnableExtensions
curl.exe --fail --silent --show-error http://127.0.0.1:8080/api/asr/health
if errorlevel 1 echo [ERROR] 服务未就绪或 127.0.0.1:8080 不可访问。
