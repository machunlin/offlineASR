#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${ROOT}"

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
test -f app/offline-asr.jar || { echo "缺少应用 JAR：app/offline-asr.jar" >&2; exit 1; }
test -f models/SenseVoiceSmall/model.int8.onnx || { echo "缺少 SenseVoiceSmall INT8 模型" >&2; exit 1; }
test -f models/SenseVoiceSmall/tokens.txt || { echo "缺少 tokens.txt" >&2; exit 1; }
command -v "${ASR_FFMPEG:-ffmpeg}" >/dev/null 2>&1 || { echo "未找到 FFmpeg" >&2; exit 1; }

mkdir -p logs temp
exec "${JAVA_BIN}" ${JAVA_OPTS:-} -jar app/offline-asr.jar \
    --spring.config.additional-location="optional:file:./config/"
