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
call "%MAVEN%" -Pwindows-x64 -Dmaven.repo.local=.m2 clean test package
if errorlevel 1 (
  set "BUILD_ERROR=%ERRORLEVEL%"
  goto :build_failed
)

set "PACKAGE=dist\windows-x64"
if exist "%PACKAGE%" (
  echo [ERROR] 输出目录已存在，为避免覆盖已有文件，请先人工检查并移走：%PACKAGE%
  popd
  exit /b 1
)
for %%D in ("%PACKAGE%\app" "%PACKAGE%\config" "%PACKAGE%\scripts" "%PACKAGE%\logs" "%PACKAGE%\temp" "%PACKAGE%\jdk" "%PACKAGE%\models\SenseVoiceSmall" "%PACKAGE%\ffmpeg\bin") do mkdir "%%~D"

copy /Y "target\offline-asr-0.1.0-SNAPSHOT.jar" "%PACKAGE%\app\offline-asr.jar" >nul
copy /Y "deploy\windows-x64\config\application.yml" "%PACKAGE%\config\application.yml" >nul
copy /Y "scripts\windows-start.bat" "%PACKAGE%\scripts\windows-start.bat" >nul
copy /Y "scripts\windows-status.bat" "%PACKAGE%\scripts\windows-status.bat" >nul
copy /Y "scripts\windows-install.bat" "%PACKAGE%\scripts\windows-install.bat" >nul
copy /Y "scripts\windows-uninstall-service.bat" "%PACKAGE%\scripts\windows-uninstall-service.bat" >nul
copy /Y "scripts\windows-stop.bat" "%PACKAGE%\scripts\windows-stop.bat" >nul
copy /Y "scripts\windows-uninstall.bat" "%PACKAGE%\scripts\windows-uninstall.bat" >nul
copy /Y "scripts\windows-generate-checksums.ps1" "%PACKAGE%\scripts\windows-generate-checksums.ps1" >nul
copy /Y "scripts\windows-verify-package.ps1" "%PACKAGE%\scripts\windows-verify-package.ps1" >nul
copy /Y "scripts\offline-asr.xml" "%PACKAGE%\scripts\offline-asr.xml" >nul
copy /Y "deploy\windows-x64\README.txt" "%PACKAGE%\README.txt" >nul

for %%F in ("%PACKAGE%\app\offline-asr.jar" "%PACKAGE%\config\application.yml" "%PACKAGE%\scripts\windows-start.bat" "%PACKAGE%\scripts\windows-status.bat" "%PACKAGE%\scripts\windows-generate-checksums.ps1" "%PACKAGE%\scripts\windows-verify-package.ps1") do (
  if not exist "%%~F" goto :copy_failed
)
echo [INFO] 已生成 Windows x64 部署包骨架: %PACKAGE%
echo [INFO] 补齐 jdk、models、ffmpeg 后，在 PowerShell 中生成并验证 checksums.sha256。
popd
exit /b 0

:build_failed
popd
exit /b %BUILD_ERROR%

:copy_failed
echo [ERROR] 复制部署包文件失败，请检查磁盘空间和目录权限。
popd
exit /b 1
