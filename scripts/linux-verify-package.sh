#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
ROOT="$(cd "${ROOT}" && pwd)"
MANIFEST="${ROOT}/checksums.sha256"

required_files=(
    "app/offline-asr.jar"
    "jdk/bin/java"
    "models/SenseVoiceSmall/model.int8.onnx"
    "models/SenseVoiceSmall/tokens.txt"
    "ffmpeg/bin/ffmpeg"
    "config/application.yml"
    "linux-start.sh"
)

for relative in "${required_files[@]}"; do
    [[ -f "${ROOT}/${relative}" ]] || {
        echo "缺少部署资产：${relative}" >&2
        exit 1
    }
done

"${ROOT}/jdk/bin/java" -version 2>&1 | grep -Eq 'version "11([.]|")' || {
    echo "部署包必须包含 Java 11 Runtime" >&2
    exit 1
}
"${ROOT}/ffmpeg/bin/ffmpeg" -version >/dev/null 2>&1 || {
    echo "部署包内 FFmpeg 无法执行" >&2
    exit 1
}

[[ -f "${MANIFEST}" ]] || {
    echo "缺少 checksums.sha256，请先执行 scripts/linux-generate-checksums.sh" >&2
    exit 1
}

declare -A listed=()
verified=0
while IFS= read -r line || [[ -n "${line}" ]]; do
    [[ -z "${line}" || "${line}" == \#* ]] && continue
    if [[ "${line}" =~ ^([a-fA-F0-9]{64})[[:space:]][[:space:]](.+)$ ]]; then
        expected="${BASH_REMATCH[1],,}"
        relative="${BASH_REMATCH[2]}"
    else
        echo "哈希清单格式错误：${line}" >&2
        exit 1
    fi

    [[ "${relative}" != /* && "${relative}" != *".."* ]] || {
        echo "哈希清单包含非法路径：${relative}" >&2
        exit 1
    }
    [[ -z "${listed[${relative}]+x}" ]] || {
        echo "哈希清单存在重复路径：${relative}" >&2
        exit 1
    }
    listed["${relative}"]=1

    file="${ROOT}/${relative}"
    [[ -f "${file}" ]] || {
        echo "哈希清单引用的文件不存在：${relative}" >&2
        exit 1
    }
    actual="$(sha256sum -- "${file}" | awk '{print $1}')"
    [[ "${actual}" == "${expected}" ]] || {
        echo "SHA-256 不匹配：${relative}" >&2
        exit 1
    }
    verified=$((verified + 1))
done < "${MANIFEST}"

(( verified >= 7 )) || {
    echo "校验文件数量不足：${verified}" >&2
    exit 1
}

while IFS= read -r -d '' file; do
    relative="${file#${ROOT}/}"
    [[ "${relative}" == "checksums.sha256" ]] && continue
    [[ -n "${listed[${relative}]+x}" ]] || {
        echo "发现未纳入哈希清单的文件：${relative}" >&2
        exit 1
    }
done < <(find "${ROOT}" -type f -print0)

echo "Linux 离线部署包静态自检通过，已验证 ${verified} 个文件。"
echo "注意：该检查不替代目标 Linux 主机上的 JNI、音频识别及断网实机测试。"
