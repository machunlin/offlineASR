#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -z "${JAVA_HOME:-}" ]]; then
    if [[ -x "${HOME}/.sdkman/candidates/java/current/bin/java" ]]; then
        export JAVA_HOME="${HOME}/.sdkman/candidates/java/current"
    elif command -v java >/dev/null 2>&1; then
        JAVA_PATH="$(command -v java)"
        export JAVA_HOME="$(cd "$(dirname "${JAVA_PATH}")/.." && pwd)"
    else
        echo "未找到 Java 11，请设置 JAVA_HOME 或安装 SDKMAN Java" >&2
        exit 1
    fi
fi

if [[ -n "${MAVEN_HOME:-}" && -x "${MAVEN_HOME}/bin/mvn" ]]; then
    MAVEN_BIN="${MAVEN_HOME}/bin/mvn"
elif [[ -x "${HOME}/.sdkman/candidates/maven/current/bin/mvn" ]]; then
    MAVEN_BIN="${HOME}/.sdkman/candidates/maven/current/bin/mvn"
elif command -v mvn >/dev/null 2>&1; then
    MAVEN_BIN="$(command -v mvn)"
else
    echo "未找到 Maven，请设置 MAVEN_HOME 或将 mvn 加入 PATH" >&2
    exit 1
fi

"${JAVA_HOME}/bin/java" -version 2>&1 | grep -Eq 'version "11([.]|")' || {
    echo "要求 Java 11，请检查 JAVA_HOME" >&2
    exit 1
}
test -f "${ROOT}/models/SenseVoiceSmall/model.int8.onnx"
test -f "${ROOT}/models/SenseVoiceSmall/tokens.txt"
command -v "${ASR_FFMPEG:-ffmpeg}" >/dev/null

cd "${ROOT}"
JAVA_HOME="${JAVA_HOME}" "${MAVEN_BIN}" -Pmacos-arm64 -Dmaven.repo.local=.m2 spring-boot:run
