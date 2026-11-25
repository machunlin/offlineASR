@echo off
setlocal EnableExtensions
set "ROOT=%~dp0.."
set "MAVEN=%MAVEN_HOME%\bin\mvn.cmd"
if "%MAVEN_HOME%"=="" set "MAVEN=mvn.cmd"
if not exist "%ROOT%\lib\sherpa-onnx-native-lib-win-x64-2.0.3.jar" (
  echo [ERROR] 缺少完整且已校验的 Windows x64 原生 JAR，拒绝打包。
  exit /b 1
)
pushd "%ROOT%"
call "%MAVEN%" -Pwindows-x64 -Dmaven.repo.local=.m2 clean package
if errorlevel 1 exit /b %ERRORLEVEL%
if not exist "deploy\windows-x64\app" mkdir "deploy\windows-x64\app"
copy /Y "target\offline-asr-0.1.0-SNAPSHOT.jar" "deploy\windows-x64\app\offline-asr.jar"
echo [INFO] 已生成应用 JAR。请按部署手册补齐 jdk、models、ffmpeg、WinSW 及哈希记录。
popd
