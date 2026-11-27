@echo off
setlocal EnableExtensions
set "ROOT=%~dp0.."
set "APP_JAR=%ROOT%\app\offline-asr.jar"
set "CONFIG_FILE=%ROOT%\config\application.yml"
set "JAVA_EXE="

if not exist "%APP_JAR%" (
    echo [ERROR] 缺少应用 JAR: %APP_JAR%
    exit /b 1
)

if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" (
    set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
)
if not defined JAVA_EXE for /f "delims=" %%J in ('where java.exe 2^>nul') do if not defined JAVA_EXE set "JAVA_EXE=%%J"
if not defined JAVA_EXE (
    echo [ERROR] 未找到 Java 11；请安装 Java 11 并设置 JAVA_HOME 或加入 PATH。
    exit /b 1
)
"%JAVA_EXE%" -XshowSettings:properties -version 2>&1 | findstr /C:"java.specification.version = 11" >nul
if errorlevel 1 (
    echo [ERROR] 当前 Java 不是 Java 11：%JAVA_EXE%
    exit /b 1
)

if defined ASR_MODEL_DIR (
    set "MODEL_DIR=%ASR_MODEL_DIR%"
) else (
    set "MODEL_DIR=%ROOT%\models\SenseVoiceSmall"
)
if not exist "%MODEL_DIR%\model.int8.onnx" (
    echo [ERROR] 模型文件缺失：%MODEL_DIR%\model.int8.onnx
    echo         请下载 sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09 并解压。
    exit /b 1
)
if not exist "%MODEL_DIR%\tokens.txt" (
    echo [ERROR] 模型词表缺失：%MODEL_DIR%\tokens.txt
    exit /b 1
)

if defined ASR_SHERPA_NATIVE_PATH (
    set "NATIVE_PATH=%ASR_SHERPA_NATIVE_PATH%"
) else (
    set "NATIVE_PATH=%ROOT%\external\sherpa-onnx-native"
)
if not exist "%NATIVE_PATH%\onnxruntime.dll" (
    echo [ERROR] 缺少 %NATIVE_PATH%\onnxruntime.dll
    echo         请下载 sherpa-onnx 2.0.3 win-x64 Native JAR 并解压。
    exit /b 1
)
if not exist "%NATIVE_PATH%\sherpa-onnx-jni.dll" (
    echo [ERROR] 缺少 %NATIVE_PATH%\sherpa-onnx-jni.dll
    exit /b 1
)

if defined ASR_FFMPEG (
    if not exist "%ASR_FFMPEG%" if "%ASR_FFMPEG:\=%"=="%ASR_FFMPEG%" (
        where "%ASR_FFMPEG%" >nul 2>nul
        if errorlevel 1 (
            echo [ERROR] ASR_FFMPEG 不在 PATH：%ASR_FFMPEG%
            exit /b 1
        )
    ) else (
        if not exist "%ASR_FFMPEG%" (
            echo [ERROR] ASR_FFMPEG 不可执行：%ASR_FFMPEG%
            exit /b 1
        )
    )
) else (
    where ffmpeg.exe >nul 2>nul
    if errorlevel 1 (
        echo [ERROR] 未找到 FFmpeg；请安装 FFmpeg 并设置 ASR_FFMPEG 或加入 PATH。
        exit /b 1
    )
)

if not exist "%CONFIG_FILE%" (
    echo [ERROR] 缺少配置文件：%CONFIG_FILE%
    exit /b 1
)

if not defined ASR_HOST set "ASR_HOST=127.0.0.1"
if not defined ASR_MAX_DURATION_SECONDS set "ASR_MAX_DURATION_SECONDS=600"
set "ASR_MODEL_DIR=%MODEL_DIR%"
set "ASR_SHERPA_NATIVE_PATH=%NATIVE_PATH%"
if not exist "%ROOT%\logs" mkdir "%ROOT%\logs"
if not exist "%ROOT%\temp" mkdir "%ROOT%\temp"

pushd "%ROOT%"
"%JAVA_EXE%" -Dasr.log.dir="%ROOT%\logs" -jar "%APP_JAR%" --spring.config.additional-location="file:%CONFIG_FILE%"
set "EXIT_CODE=%ERRORLEVEL%"
popd
exit /b %EXIT_CODE%
