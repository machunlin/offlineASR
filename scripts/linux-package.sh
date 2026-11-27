#!/usr/bin/env bash
# 构建并组装 OfflineASR 应用包；不下载、不复制、不分发外部运行依赖。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    JAVA_BIN="${JAVA_HOME}/bin/java"
elif command -v java >/dev/null 2>&1; then
    JAVA_BIN="$(command -v java)"
else
    echo "未找到 Java 11；请先在构建机安装 Java 11。" >&2
    exit 1
fi
"${JAVA_BIN}" -version 2>&1 | grep -Eq 'version "11([.]|")' || {
    echo "构建要求 Java 11；请检查 JAVA_HOME/PATH。" >&2
    exit 1
}

if [[ -n "${MAVEN_HOME:-}" && -x "${MAVEN_HOME}/bin/mvn" ]]; then
    MAVEN_BIN="${MAVEN_HOME}/bin/mvn"
elif command -v mvn >/dev/null 2>&1; then
    MAVEN_BIN="$(command -v mvn)"
else
    echo "未找到 Maven；请设置 MAVEN_HOME 或将其加入 PATH。" >&2
    exit 1
fi

cd "${ROOT}"
"${MAVEN_BIN}" -B -Dmaven.repo.local=.m2 clean test package
bash "${ROOT}/scripts/generate-maven-inventory.sh" linux-x64

PACKAGE_DIR="${ROOT}/dist/linux-x64"
if [[ -e "${PACKAGE_DIR}" ]]; then
    echo "输出目录已存在；请先人工检查并移走：${PACKAGE_DIR}" >&2
    exit 1
fi

mkdir -p "${PACKAGE_DIR}/app" "${PACKAGE_DIR}/config" \
    "${PACKAGE_DIR}/logs" "${PACKAGE_DIR}/temp" \
    "${PACKAGE_DIR}/scripts" "${PACKAGE_DIR}/docs" \
    "${PACKAGE_DIR}/third-party" "${PACKAGE_DIR}/licenses"

cp "${ROOT}/target/offline-asr-0.1.0-SNAPSHOT.jar" "${PACKAGE_DIR}/app/offline-asr.jar"
cp "${ROOT}/deploy/linux-x64/config/application.yml" "${PACKAGE_DIR}/config/application.yml"
cp "${ROOT}/deploy/linux-x64/README.txt" "${PACKAGE_DIR}/README.txt"
cp "${ROOT}/scripts/linux-start.sh" "${PACKAGE_DIR}/linux-start.sh"
cp "${ROOT}/deploy/linux-x64/offline-asr.service" "${PACKAGE_DIR}/offline-asr.service"
cp "${ROOT}/scripts/linux-generate-checksums.sh" "${PACKAGE_DIR}/scripts/linux-generate-checksums.sh"
cp "${ROOT}/scripts/linux-verify-package.sh" "${PACKAGE_DIR}/scripts/linux-verify-package.sh"
cp "${ROOT}/LICENSE" "${PACKAGE_DIR}/LICENSE"
cp "${ROOT}/THIRD_PARTY_NOTICES.md" "${PACKAGE_DIR}/THIRD_PARTY_NOTICES.md"
cp "${ROOT}/docs/08-第三方组件与许可证清单.md" "${PACKAGE_DIR}/docs/08-第三方组件与许可证清单.md"
cp -R "${ROOT}/deploy/licenses/." "${PACKAGE_DIR}/licenses/"
cp "${ROOT}/deploy/sbom-linux-x64.cdx.json" "${PACKAGE_DIR}/third-party/sbom-linux-x64.cdx.json"
cp "${ROOT}/deploy/maven-runtime-dependencies-linux-x64.txt" "${PACKAGE_DIR}/third-party/maven-runtime-dependencies.txt"
cp "${ROOT}/deploy/maven-test-dependencies-linux-x64.txt" "${PACKAGE_DIR}/third-party/maven-test-dependencies.txt"
cp "${ROOT}/deploy/maven-licenses-linux-x64.txt" "${PACKAGE_DIR}/third-party/maven-licenses.txt"
chmod +x "${PACKAGE_DIR}/linux-start.sh" \
    "${PACKAGE_DIR}/scripts/linux-generate-checksums.sh" \
    "${PACKAGE_DIR}/scripts/linux-verify-package.sh"

cd "${PACKAGE_DIR}"
./scripts/linux-generate-checksums.sh
./scripts/linux-verify-package.sh

echo "Linux x64 应用包已生成并通过静态校验：${PACKAGE_DIR}"
echo "本包仅含应用文件；Java、sherpa-onnx Native Runtime、模型和 FFmpeg 由目标机自行准备。"
