@echo off

setlocal EnableExtensions
set "ROOT=%~dp0.."
if not exist "%ROOT%\scripts\offline-asr.exe" (
  echo [ERROR] 未提供离线服务包装器。请先按部署手册完成前台验收，再将已校验的 WinSW 重命名为 offline-asr.exe 放入 scripts 目录。
  exit /b 1
)
"%ROOT%\scripts\offline-asr.exe" install
if errorlevel 1 exit /b %ERRORLEVEL%
"%ROOT%\scripts\offline-asr.exe" start
