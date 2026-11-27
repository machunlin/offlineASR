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

PACKAGE_DIR="${ROOT}/dist/linux-x64"
if [[ -e "${PACKAGE_DIR}" ]]; then
    echo "输出目录已存在，为避免覆盖已有文件，请先人工检查并移走：${PACKAGE_DIR}" >&2
    exit 1
fi

mkdir -p "${PACKAGE_DIR}/app" "${PACKAGE_DIR}/config" \
    "${PACKAGE_DIR}/logs" "${PACKAGE_DIR}/temp" \
    "${PACKAGE_DIR}/models/SenseVoiceSmall" \
    "${PACKAGE_DIR}/scripts" "${PACKAGE_DIR}/jdk" \
    "${PACKAGE_DIR}/ffmpeg/bin"

cp "${ROOT}/target/offline-asr-0.1.0-SNAPSHOT.jar" "${PACKAGE_DIR}/app/offline-asr.jar"
cp "${ROOT}/deploy/linux-x64/config/application.yml" "${PACKAGE_DIR}/config/application.yml"
cp "${ROOT}/deploy/linux-x64/offline-asr.service" "${PACKAGE_DIR}/offline-asr.service"
cp "${ROOT}/deploy/linux-x64/README.txt" "${PACKAGE_DIR}/README.txt"
cp "${ROOT}/scripts/linux-start.sh" "${PACKAGE_DIR}/linux-start.sh"
cp "${ROOT}/scripts/linux-generate-checksums.sh" "${PACKAGE_DIR}/scripts/linux-generate-checksums.sh"
cp "${ROOT}/scripts/linux-verify-package.sh" "${PACKAGE_DIR}/scripts/linux-verify-package.sh"
chmod +x "${PACKAGE_DIR}/linux-start.sh" \
    "${PACKAGE_DIR}/scripts/linux-generate-checksums.sh" \
    "${PACKAGE_DIR}/scripts/linux-verify-package.sh"

echo "Linux x64 部署包骨架已生成：${PACKAGE_DIR}"
echo "将经许可核验的 Java 11 Runtime 放入 jdk/，模型放入 models/SenseVoiceSmall/，"
echo "并将 Linux x64 FFmpeg 放入 ffmpeg/bin/；也可按部署手册使用系统依赖。"
echo "资产补齐后执行 scripts/linux-generate-checksums.sh 和 scripts/linux-verify-package.sh。"
