@echo off
setlocal EnableExtensions
set "ROOT=%~dp0.."
set "MAVEN_CMD="
if defined MAVEN_HOME if exist "%MAVEN_HOME%\bin\mvn.cmd" set "MAVEN_CMD=%MAVEN_HOME%\bin\mvn.cmd"
if not defined MAVEN_CMD for /f "delims=" %%M in ('where mvn.cmd 2^>nul') do if not defined MAVEN_CMD set "MAVEN_CMD=%%M"
if not defined MAVEN_CMD (
    echo [ERROR] 未找到 mvn.cmd；请安装 Maven 或设置 MAVEN_HOME。
    exit /b 1
)

echo [INFO] Maven: %MAVEN_CMD%
pushd "%ROOT%"
call "%MAVEN_CMD%" -B -Dmaven.repo.local=.m2 clean test package
if errorlevel 1 (
    echo [ERROR] Maven 构建失败。
    popd
    exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\scripts\generate-maven-inventory.ps1" -Root "%ROOT%" -Label windows-x64
if errorlevel 1 (
    echo [ERROR] Maven 依赖清单或 SBOM 生成失败。
    popd
    exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\scripts\windows-assemble-package.ps1" -Root "%ROOT%"
if errorlevel 1 (
    echo [ERROR] Windows 应用包组装或校验失败。
    popd
    exit /b 1
)
popd
exit /b 0
