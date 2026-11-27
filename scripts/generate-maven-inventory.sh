#!/usr/bin/env bash
# 中文注释：导出应用运行时/测试依赖并生成 CycloneDX SBOM；外部 Native Runtime 不参与构建。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LABEL="${1:-$(uname -s | tr '[:upper:]' '[:lower:]')-$(uname -m)}"

if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    JAVA_BIN="${JAVA_HOME}/bin/java"
elif command -v java >/dev/null 2>&1; then
    JAVA_BIN="$(command -v java)"
else
    echo "未找到 Java 11，请设置 JAVA_HOME 或将 java 加入 PATH" >&2
    exit 1
fi
"${JAVA_BIN}" -version 2>&1 | grep -Eq 'version "11([.]|")' || {
    echo "Maven 依赖清单生成要求 Java 11，请检查 JAVA_HOME/PATH" >&2
    exit 1
}

if [[ -n "${MAVEN_HOME:-}" && -x "${MAVEN_HOME}/bin/mvn" ]]; then
    MAVEN_BIN="${MAVEN_HOME}/bin/mvn"
elif command -v mvn >/dev/null 2>&1; then
    MAVEN_BIN="$(command -v mvn)"
else
    echo "未找到 Maven，请设置 MAVEN_HOME 或将 mvn 加入 PATH" >&2
    exit 1
fi

cd "${ROOT}"
"${MAVEN_BIN}" -B -Dmaven.repo.local=.m2 \
    dependency:tree -Dscope=runtime -DoutputFile="deploy/maven-runtime-dependencies-${LABEL}.txt"
"${MAVEN_BIN}" -B -Dmaven.repo.local=.m2 \
    dependency:tree -Dscope=test -DoutputFile="deploy/maven-test-dependencies-${LABEL}.txt"
"${MAVEN_BIN}" -B -Dmaven.repo.local=.m2 \
    org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeBom \
    -DoutputDirectory="${ROOT}/deploy" \
    -DoutputName="sbom-${LABEL}.cdx" \
    -DoutputFormat=json

node "${ROOT}/scripts/normalize-maven-bom.mjs" "${LABEL}"
node "${ROOT}/scripts/verify-maven-inventory.mjs" "${LABEL}"
node "${ROOT}/scripts/export-maven-licenses.mjs" "${LABEL}"
