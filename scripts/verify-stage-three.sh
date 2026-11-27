#!/usr/bin/env bash
# 应用构建与三平台外部 Native Runtime 隔离静态检查；不下载、不加载外部依赖。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -z "${JAVA_HOME:-}" ]]; then
    if command -v java >/dev/null 2>&1; then
        export JAVA_HOME="$(cd "$(dirname "$(command -v java)")/.." && pwd)"
    else
        echo "未找到 Java 11，请设置 JAVA_HOME" >&2
        exit 1
    fi
fi
"${JAVA_HOME}/bin/java" -version 2>&1 | grep -Eq 'version "11([.]|")' || {
    echo "当前 Java 不是 11" >&2
    exit 1
}
if [[ -n "${MAVEN_HOME:-}" && -x "${MAVEN_HOME}/bin/mvn" ]]; then
    MAVEN_BIN="${MAVEN_HOME}/bin/mvn"
else
    MAVEN_BIN="$(command -v mvn)"
fi

cd "${ROOT}"
"${MAVEN_BIN}" -B -Dmaven.repo.local=.m2 clean package -DskipTests
jar tf target/offline-asr-0.1.0-SNAPSHOT.jar | grep -q 'BOOT-INF/lib/sherpa-onnx-jvm-2.0.3.jar'
if jar tf target/offline-asr-0.1.0-SNAPSHOT.jar | grep -Eq 'sherpa-onnx-native-lib-'; then
    echo "应用 JAR 包含外部 Native Runtime" >&2
    exit 1
fi

grep -q 'libonnxruntime.dylib' scripts/mac-start.sh
grep -q 'libsherpa-onnx-jni.dylib' scripts/mac-start.sh
grep -q 'libonnxruntime.so' scripts/linux-start.sh
grep -q 'libsherpa-onnx-jni.so' scripts/linux-start.sh
grep -q 'onnxruntime.dll' scripts/windows-start.bat
grep -q 'sherpa-onnx-jni.dll' scripts/windows-start.bat

node scripts/verify-js-contract.mjs >/dev/null
node scripts/verify-sdk-contract.mjs >/dev/null
echo "应用构建、JAR 隔离和三平台 Native 文件名检查：PASS"
