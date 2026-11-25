@echo off
setlocal EnableExtensions
set "ROOT=%~dp0.."
if not exist "%ROOT%\scripts\offline-asr.exe" (
  echo [ERROR] 未找到 WinSW，无法自动卸载服务。
  exit /b 1
)
"%ROOT%\scripts\offline-asr.exe" stop
"%ROOT%\scripts\offline-asr.exe" uninstall
