#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA_BIN=""
if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    JAVA_BIN="${JAVA_HOME}/bin/java"
elif command -v java >/dev/null 2>&1; then
    JAVA_BIN="$(command -v java)"
else
    echo "未找到 Java，请设置 JAVA_HOME 或将 Java 11 加入 PATH" >&2
    exit 1
fi

"${JAVA_BIN}" -version 2>&1 | grep -Eq 'version "11([.]|")' || {
    echo "要求 Java 11，请检查 JAVA_HOME/PATH" >&2
    exit 1
}

"${ROOT}/scripts/download-linux-native.sh"
if [[ -n "${MAVEN_HOME:-}" && -x "${MAVEN_HOME}/bin/mvn" ]]; then
    MAVEN_BIN="${MAVEN_HOME}/bin/mvn"
elif command -v mvn >/dev/null 2>&1; then
    MAVEN_BIN="$(command -v mvn)"
else
    echo "未找到 Maven，请设置 MAVEN_HOME 或将 mvn 加入 PATH" >&2
    exit 1
fi

cd "${ROOT}"
"${MAVEN_BIN}" -B -Plinux-x64 -Dmaven.repo.local=.m2 clean test package
PACKAGE_DIR="${ROOT}/deploy/linux-x64"
mkdir -p "${PACKAGE_DIR}/app" "${PACKAGE_DIR}/config" "${PACKAGE_DIR}/logs" "${PACKAGE_DIR}/models/SenseVoiceSmall"
cp "${ROOT}/target/offline-asr-0.1.0-SNAPSHOT.jar" "${PACKAGE_DIR}/app/offline-asr.jar"
cp "${ROOT}/deploy/linux-x64/config/application.yml" "${PACKAGE_DIR}/config/application.yml"
cp "${ROOT}/deploy/linux-x64/offline-asr.service" "${PACKAGE_DIR}/offline-asr.service"
cp "${ROOT}/scripts/linux-start.sh" "${PACKAGE_DIR}/linux-start.sh"
echo "Linux x64 部署包骨架已生成：${PACKAGE_DIR}"
echo "请按部署手册放入模型文件，再执行 Linux 现场验收。"
