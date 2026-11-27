@echo off
setlocal EnableExtensions
set "ROOT=%~dp0.."

echo ===== Operating System =====
ver
echo.
echo ===== Java Runtime =====
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" (
    "%JAVA_HOME%\bin\java.exe" -version
) else (
    where java.exe
    java.exe -version
)
echo.
echo ===== sherpa-onnx Native Runtime =====
if defined ASR_SHERPA_NATIVE_PATH (
    set "NATIVE_PATH=%ASR_SHERPA_NATIVE_PATH%"
) else (
    set "NATIVE_PATH=%ROOT%\external\sherpa-onnx-native"
)
if exist "%NATIVE_PATH%\onnxruntime.dll" (dir "%NATIVE_PATH%\onnxruntime.dll") else echo [WARN] onnxruntime.dll not found in %NATIVE_PATH%
if exist "%NATIVE_PATH%\sherpa-onnx-jni.dll" (dir "%NATIVE_PATH%\sherpa-onnx-jni.dll") else echo [WARN] sherpa-onnx-jni.dll not found in %NATIVE_PATH%
echo.
echo ===== Model =====
if defined ASR_MODEL_DIR (set "MODEL_DIR=%ASR_MODEL_DIR%") else set "MODEL_DIR=%ROOT%\models\SenseVoiceSmall"
if exist "%MODEL_DIR%\model.int8.onnx" (dir "%MODEL_DIR%\model.int8.onnx") else echo [WARN] model.int8.onnx not found in %MODEL_DIR%
if exist "%MODEL_DIR%\tokens.txt" (dir "%MODEL_DIR%\tokens.txt") else echo [WARN] tokens.txt not found in %MODEL_DIR%
echo.
echo ===== FFmpeg =====
if defined ASR_FFMPEG (
    "%ASR_FFMPEG%" -version
) else (
    ffmpeg.exe -version
)
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
