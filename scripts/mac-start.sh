#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    :
elif command -v java >/dev/null 2>&1; then
    JAVA_PATH="$(command -v java)"
    export JAVA_HOME="$(cd "$(dirname "${JAVA_PATH}")/.." && pwd)"
else
    echo "[ERROR] 未找到 Java 11；请安装 Java 11 并设置 JAVA_HOME。" >&2
    exit 1
fi

"${JAVA_HOME}/bin/java" -version 2>&1 | grep -Eq 'version "11([.]|")' || {
    echo "[ERROR] 当前 Java 不是 Java 11；请检查 JAVA_HOME/PATH。" >&2
    exit 1
}

MODEL_DIR="${ASR_MODEL_DIR:-${ROOT}/models/SenseVoiceSmall}"
if [[ "${MODEL_DIR}" != /* ]]; then
    MODEL_DIR="${ROOT}/${MODEL_DIR}"
fi
[[ -f "${MODEL_DIR}/model.int8.onnx" ]] || {
    echo "[ERROR] 模型文件缺失：${MODEL_DIR}/model.int8.onnx" >&2
    echo "        请下载 sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09 并解压。" >&2
    exit 1
}
[[ -f "${MODEL_DIR}/tokens.txt" ]] || {
    echo "[ERROR] 模型词表缺失：${MODEL_DIR}/tokens.txt" >&2
    exit 1
}

NATIVE_PATH="${ASR_SHERPA_NATIVE_PATH:-${ROOT}/external/sherpa-onnx-native}"
if [[ "${NATIVE_PATH}" != /* ]]; then
    NATIVE_PATH="${ROOT}/${NATIVE_PATH}"
fi
[[ -d "${NATIVE_PATH}" ]] || {
    echo "[ERROR] sherpa-onnx Native Runtime 目录缺失：${NATIVE_PATH}" >&2
    echo "        请下载 sherpa-onnx 2.0.3 osx-aarch64 Native JAR 并解压。" >&2
    exit 1
}
[[ -f "${NATIVE_PATH}/libonnxruntime.dylib" ]] || {
    echo "[ERROR] 缺少 ${NATIVE_PATH}/libonnxruntime.dylib" >&2
    exit 1
}
[[ -f "${NATIVE_PATH}/libsherpa-onnx-jni.dylib" ]] || {
    echo "[ERROR] 缺少 ${NATIVE_PATH}/libsherpa-onnx-jni.dylib" >&2
    exit 1
}

command -v "${ASR_FFMPEG:-ffmpeg}" >/dev/null 2>&1 || {
    echo "[ERROR] 未找到 FFmpeg；请安装并设置 ASR_FFMPEG 或加入 PATH。" >&2
    exit 1
}

export ASR_MODEL_DIR="${MODEL_DIR}"
export ASR_SHERPA_NATIVE_PATH="${NATIVE_PATH}"

cd "${ROOT}"
if [[ -n "${MAVEN_HOME:-}" && -x "${MAVEN_HOME}/bin/mvn" ]]; then
    MAVEN_BIN="${MAVEN_HOME}/bin/mvn"
elif command -v mvn >/dev/null 2>&1; then
    MAVEN_BIN="$(command -v mvn)"
else
    echo "[ERROR] 未找到 Maven；开发运行需要 Maven，或先用 linux-package 流程生成应用 JAR。" >&2
    exit 1
fi
"${MAVEN_BIN}" -B -Dmaven.repo.local=.m2 spring-boot:run
