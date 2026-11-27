#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${ROOT}"

if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    JAVA_BIN="${JAVA_HOME}/bin/java"
elif command -v java >/dev/null 2>&1; then
    JAVA_BIN="$(command -v java)"
else
    echo "[ERROR] 未找到 Java 11；请安装 Java 11 并设置 JAVA_HOME 或将其加入 PATH。参考 docs/08-第三方组件与许可证清单.md" >&2
    exit 1
fi

JAVA_VERSION="$("${JAVA_BIN}" -version 2>&1 | head -1)"
[[ "${JAVA_VERSION}" == *'version "11'* ]] || {
    echo "[ERROR] 当前 Java 不是 Java 11：${JAVA_VERSION}" >&2
    echo "        请安装 Java 11 并检查 JAVA_HOME/PATH。" >&2
    exit 1
}

[[ -f "${ROOT}/app/offline-asr.jar" ]] || {
    echo "[ERROR] 缺少应用 JAR：${ROOT}/app/offline-asr.jar" >&2
    exit 1
}
[[ -f "${ROOT}/config/application.yml" ]] || {
    echo "[ERROR] 缺少配置文件：${ROOT}/config/application.yml" >&2
    exit 1
}

if [[ -n "${ASR_MODEL_DIR:-}" ]]; then
    MODEL_DIR="${ASR_MODEL_DIR}"
else
    MODEL_DIR="${ROOT}/models/SenseVoiceSmall"
fi
if [[ "${MODEL_DIR}" != /* ]]; then
    MODEL_DIR="${ROOT}/${MODEL_DIR}"
fi
[[ -f "${MODEL_DIR}/model.int8.onnx" ]] || {
    echo "[ERROR] 模型文件缺失：${MODEL_DIR}/model.int8.onnx" >&2
    echo "        请下载 sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09 并解压。" >&2
    echo "        详见 docs/08-第三方组件与许可证清单.md。" >&2
    exit 1
}
[[ -f "${MODEL_DIR}/tokens.txt" ]] || {
    echo "[ERROR] 模型词表缺失：${MODEL_DIR}/tokens.txt" >&2
    exit 1
}

if [[ -n "${ASR_SHERPA_NATIVE_PATH:-}" ]]; then
    NATIVE_PATH="${ASR_SHERPA_NATIVE_PATH}"
else
    NATIVE_PATH="${ROOT}/external/sherpa-onnx-native"
fi
if [[ "${NATIVE_PATH}" != /* ]]; then
    NATIVE_PATH="${ROOT}/${NATIVE_PATH}"
fi
[[ -d "${NATIVE_PATH}" ]] || {
    echo "[ERROR] sherpa-onnx Native Runtime 目录缺失：${NATIVE_PATH}" >&2
    echo "        请下载对应平台 sherpa-onnx 2.0.3 Native JAR 并解压。" >&2
    echo "        详见 docs/08-第三方组件与许可证清单.md。" >&2
    exit 1
}

case "$(uname -s)" in
    Darwin)
        ONNX_LIB='libonnxruntime.dylib'
        JNI_LIB='libsherpa-onnx-jni.dylib'
        ;;
    Linux)
        ONNX_LIB='libonnxruntime.so'
        JNI_LIB='libsherpa-onnx-jni.so'
        ;;
    *)
        echo "[ERROR] 不支持的操作系统：$(uname -s)" >&2
        exit 1
        ;;
esac
[[ -f "${NATIVE_PATH}/${ONNX_LIB}" ]] || {
    echo "[ERROR] 缺少 ${NATIVE_PATH}/${ONNX_LIB}" >&2
    exit 1
}
[[ -f "${NATIVE_PATH}/${JNI_LIB}" ]] || {
    echo "[ERROR] 缺少 ${NATIVE_PATH}/${JNI_LIB}" >&2
    exit 1
}

if [[ -n "${ASR_FFMPEG:-}" ]]; then
    if [[ "${ASR_FFMPEG}" == */* ]]; then
        [[ -x "${ASR_FFMPEG}" ]] || {
            echo "[ERROR] ASR_FFMPEG 不可执行：${ASR_FFMPEG}" >&2
            exit 1
        }
    else
        command -v "${ASR_FFMPEG}" >/dev/null 2>&1 || {
            echo "[ERROR] ASR_FFMPEG 不在 PATH：${ASR_FFMPEG}" >&2
            exit 1
        }
    fi
    FFMPEG_BIN="${ASR_FFMPEG}"
elif command -v ffmpeg >/dev/null 2>&1; then
    FFMPEG_BIN="$(command -v ffmpeg)"
else
    echo "[ERROR] 未找到 FFmpeg；请安装 FFmpeg 并设置 ASR_FFMPEG 或加入 PATH。" >&2
    exit 1
fi

export ASR_MODEL_DIR="${MODEL_DIR}"
export ASR_SHERPA_NATIVE_PATH="${NATIVE_PATH}"
export ASR_FFMPEG="${FFMPEG_BIN}"
export ASR_TEMP_DIR="${ROOT}/temp"
mkdir -p "${ROOT}/logs" "${ROOT}/temp"

read -r -a JAVA_OPTS_ARRAY <<< "${JAVA_OPTS:-}"
exec "${JAVA_BIN}" "${JAVA_OPTS_ARRAY[@]}" -Dasr.log.dir="${ROOT}/logs" \
    -jar app/offline-asr.jar \
    --spring.config.additional-location="optional:file:${ROOT}/config/"
