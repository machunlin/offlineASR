@echo off
setlocal EnableExtensions
set "ROOT=%~dp0.."
set "APP_JAR=%ROOT%\app\offline-asr.jar"
set "JAVA_EXE=%ROOT%\jdk\bin\java.exe"
set "MODEL_DIR=%ROOT%\models\SenseVoiceSmall"
set "FFMPEG_EXE=%ROOT%\ffmpeg\bin\ffmpeg.exe"
set "CONFIG_FILE=%ROOT%\config\application.yml"

if not exist "%APP_JAR%" (
    echo [ERROR] 缺少应用 JAR: %APP_JAR%
    exit /b 1
)
if not exist "%JAVA_EXE%" (
    echo [ERROR] 缺少 Java 11 Runtime: %JAVA_EXE%
    exit /b 1
)
if not exist "%MODEL_DIR%\model.int8.onnx" (
    echo [ERROR] 缺少 INT8 模型文件
    exit /b 1
)
if not exist "%MODEL_DIR%\tokens.txt" (
    echo [ERROR] 缺少 tokens.txt
    exit /b 1
)
if not exist "%FFMPEG_EXE%" (
    echo [ERROR] 缺少 FFmpeg: %FFMPEG_EXE%
    exit /b 1
)
if not exist "%CONFIG_FILE%" (
    echo [ERROR] 缺少配置文件: %CONFIG_FILE%
    exit /b 1
)

if not defined ASR_HOST set "ASR_HOST=127.0.0.1"
if not defined ASR_MAX_DURATION_SECONDS set "ASR_MAX_DURATION_SECONDS=60"
set "ASR_MODEL_DIR=%MODEL_DIR%"
set "ASR_FFMPEG=%FFMPEG_EXE%"
if not exist "%ROOT%\logs" mkdir "%ROOT%\logs"
if not exist "%ROOT%\temp" mkdir "%ROOT%\temp"

pushd "%ROOT%"
"%JAVA_EXE%" -Dasr.log.dir="%ROOT%\logs" -jar "%APP_JAR%" --spring.config.additional-location="file:%CONFIG_FILE%"
set "EXIT_CODE=%ERRORLEVEL%"
popd
exit /b %EXIT_CODE%
