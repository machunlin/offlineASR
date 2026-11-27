#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${ROOT}"

if [[ -x "${ROOT}/jdk/bin/java" ]]; then
    JAVA_BIN="${ROOT}/jdk/bin/java"
elif [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    JAVA_BIN="${JAVA_HOME}/bin/java"
elif command -v java >/dev/null 2>&1; then
    JAVA_BIN="$(command -v java)"
else
    echo "未找到 Java 11 Runtime；请将 Java 11 放入 jdk/ 或设置 JAVA_HOME" >&2
    exit 1
fi

"${JAVA_BIN}" -version 2>&1 | grep -Eq 'version "11([.]|")' || {
    echo "要求 Java 11，请检查部署包内 jdk/ 或 JAVA_HOME" >&2
    exit 1
}

test -f app/offline-asr.jar || { echo "缺少应用 JAR：app/offline-asr.jar" >&2; exit 1; }
test -f models/SenseVoiceSmall/model.int8.onnx || { echo "缺少 SenseVoiceSmall INT8 模型" >&2; exit 1; }
test -f models/SenseVoiceSmall/tokens.txt || { echo "缺少 tokens.txt" >&2; exit 1; }

if [[ -x "${ROOT}/ffmpeg/bin/ffmpeg" ]]; then
    FFMPEG_BIN="${ROOT}/ffmpeg/bin/ffmpeg"
elif [[ -n "${ASR_FFMPEG:-}" ]] && command -v "${ASR_FFMPEG}" >/dev/null 2>&1; then
    FFMPEG_BIN="${ASR_FFMPEG}"
elif command -v ffmpeg >/dev/null 2>&1; then
    FFMPEG_BIN="$(command -v ffmpeg)"
else
    echo "未找到 FFmpeg；请将 ffmpeg 放入 ffmpeg/bin/ 或安装到 PATH" >&2
    exit 1
fi

export ASR_MODEL_DIR="${ROOT}/models/SenseVoiceSmall"
export ASR_FFMPEG="${FFMPEG_BIN}"
export ASR_TEMP_DIR="${ROOT}/temp"
mkdir -p "${ROOT}/logs" "${ROOT}/temp"

read -r -a JAVA_OPTS_ARRAY <<< "${JAVA_OPTS:-}"
exec "${JAVA_BIN}" "${JAVA_OPTS_ARRAY[@]}" -Dasr.log.dir="${ROOT}/logs" \
    -jar app/offline-asr.jar \
    --spring.config.additional-location="optional:file:${ROOT}/config/"
