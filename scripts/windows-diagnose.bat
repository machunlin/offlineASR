@echo off
setlocal EnableExtensions
set "ROOT=%~dp0.."
set "JAVA_EXE=%ROOT%\jdk\bin\java.exe"
set "FFMPEG_EXE=%ROOT%\ffmpeg\bin\ffmpeg.exe"

echo ===== Operating System =====
ver
echo.
echo ===== Java Runtime =====
if exist "%JAVA_EXE%" ("%JAVA_EXE%" -version) else (echo [ERROR] Java Runtime missing)
echo.
echo ===== FFmpeg =====
if exist "%FFMPEG_EXE%" ("%FFMPEG_EXE%" -version) else (echo [ERROR] FFmpeg missing)
echo.
echo ===== Port 8080 =====
netstat -ano | findstr ":8080"
echo.
echo ===== ASR Health =====
curl.exe --connect-timeout 3 --max-time 5 --fail --silent --show-error http://127.0.0.1:8080/api/asr/health
if errorlevel 1 echo [ERROR] ASR service is not healthy or not listening on 127.0.0.1:8080.
echo.
echo ===== Latest Log =====
if exist "%ROOT%\logs\offline-asr.log" powershell -NoProfile -Command "Get-Content -LiteralPath '%ROOT%\logs\offline-asr.log' -Tail 80" else echo [WARN] Log file not found.
echo.
pause
