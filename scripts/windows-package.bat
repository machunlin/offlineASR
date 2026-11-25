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
set "PACKAGE=deploy\windows-x64"
for %%D in ("%PACKAGE%\app" "%PACKAGE%\config" "%PACKAGE%\scripts" "%PACKAGE%\logs" "%PACKAGE%\jdk" "%PACKAGE%\models\SenseVoiceSmall" "%PACKAGE%\ffmpeg\bin") do if not exist %%D mkdir %%D
copy /Y "target\offline-asr-0.1.0-SNAPSHOT.jar" "%PACKAGE%\app\offline-asr.jar" >nul
copy /Y "deploy\windows-x64\config\application.yml" "%PACKAGE%\config\application.yml" >nul
copy /Y "scripts\windows-start.bat" "%PACKAGE%\scripts\windows-start.bat" >nul
copy /Y "scripts\windows-status.bat" "%PACKAGE%\scripts\windows-status.bat" >nul
copy /Y "scripts\windows-install.bat" "%PACKAGE%\scripts\windows-install.bat" >nul
copy /Y "scripts\windows-uninstall-service.bat" "%PACKAGE%\scripts\windows-uninstall-service.bat" >nul
copy /Y "scripts\windows-generate-checksums.ps1" "%PACKAGE%\scripts\windows-generate-checksums.ps1" >nul
copy /Y "scripts\windows-verify-package.ps1" "%PACKAGE%\scripts\windows-verify-package.ps1" >nul
copy /Y "scripts\offline-asr.xml" "%PACKAGE%\scripts\offline-asr.xml" >nul
copy /Y "deploy\windows-x64\README.txt" "%PACKAGE%\README.txt" >nul
echo [INFO] 已生成 Windows x64 部署包骨架: %PACKAGE%
echo [INFO] 请补齐并核验 jdk、models、ffmpeg、offline-asr.exe 后，在 Windows 执行 scripts\windows-generate-checksums.ps1 和 windows-verify-package.ps1。
popd
