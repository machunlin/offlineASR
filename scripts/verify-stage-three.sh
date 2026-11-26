#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA_HOME="${JAVA_HOME:-}"
if [[ -z "${JAVA_HOME}" && -x "${HOME}/.sdkman/candidates/java/current/bin/java" ]]; then
    JAVA_HOME="${HOME}/.sdkman/candidates/java/current"
fi
if [[ -z "${JAVA_HOME}" || ! -x "${JAVA_HOME}/bin/java" ]]; then
    echo "未找到 Java 11，请设置 JAVA_HOME 或安装 SDKMAN Java" >&2
    exit 1
fi

if [[ -n "${MAVEN_HOME:-}" && -x "${MAVEN_HOME}/bin/mvn" ]]; then
    MAVEN_BIN="${MAVEN_HOME}/bin/mvn"
elif command -v mvn >/dev/null 2>&1; then
    MAVEN_BIN="$(command -v mvn)"
else
    echo "未找到 Maven，请设置 MAVEN_HOME 或将 mvn 加入 PATH" >&2
    exit 1
fi

command -v node >/dev/null
"${JAVA_HOME}/bin/java" -version 2>&1 | grep -Eq 'version "11([.]|")' || {
    echo "当前 Java 版本不是 11" >&2
    exit 1
}

cd "${ROOT}"
export JAVA_HOME
"${MAVEN_BIN}" -B -Dmaven.repo.local=.m2 -Pmacos-arm64 clean test package
node scripts/verify-js-contract.mjs
"${JAVA_HOME}/bin/jar" tf target/offline-asr-0.1.0-SNAPSHOT.jar | grep -q 'BOOT-INF/lib/sherpa-onnx-native-lib-osx-aarch64-2.0.3.jar'

scripts/download-linux-native.sh
"${MAVEN_BIN}" -B -Dmaven.repo.local=.m2 -Plinux-x64 clean package -DskipTests
"${JAVA_HOME}/bin/jar" tf target/offline-asr-0.1.0-SNAPSHOT.jar | grep -q 'BOOT-INF/lib/sherpa-onnx-native-lib-linux-x64-2.0.3.jar'
if "${JAVA_HOME}/bin/jar" tf target/offline-asr-0.1.0-SNAPSHOT.jar | grep -Eq 'osx-aarch64|win-x64'; then
    echo "Linux 包含了其他平台的原生库" >&2
    exit 1
fi

"${MAVEN_BIN}" -B -Dmaven.repo.local=.m2 -Pwindows-x64 clean package -DskipTests
"${JAVA_HOME}/bin/jar" tf target/offline-asr-0.1.0-SNAPSHOT.jar | grep -q 'BOOT-INF/lib/sherpa-onnx-native-lib-win-x64-2.0.3.jar'
if "${JAVA_HOME}/bin/jar" tf target/offline-asr-0.1.0-SNAPSHOT.jar | grep -Eq 'osx-aarch64|linux-x64'; then
    echo "Windows 包含了其他平台的原生库" >&2
    exit 1
fi

echo "三平台构建与静态打包检查：PASS"
