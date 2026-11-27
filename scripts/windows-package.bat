@echo off
setlocal EnableExtensions
set "ROOT=%~dp0.."
set "MAVEN=%MAVEN_HOME%\bin\mvn.cmd"
if "%MAVEN_HOME%"=="" set "MAVEN=mvn.cmd"

pushd "%ROOT%"
call "%MAVEN%" -B -Dmaven.repo.local=.m2 clean test package
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
