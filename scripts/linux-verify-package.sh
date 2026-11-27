#!/usr/bin/env bash
# 校验 OfflineASR 应用包；外部依赖只检查部署包内不含它们。
set -euo pipefail

ROOT="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
ROOT="$(cd "${ROOT}" && pwd)"
MANIFEST="${ROOT}/checksums.sha256"

sha256_value() {
    local file="$1"
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum -- "${file}" | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "${file}" | awk '{print $1}'
    else
        echo "未找到 sha256sum 或 shasum" >&2
        return 1
    fi
}

required_files=(
    "app/offline-asr.jar"
    "config/application.yml"
    "linux-start.sh"
    "offline-asr.service"
    "LICENSE"
    "THIRD_PARTY_NOTICES.md"
    "docs/08-第三方组件与许可证清单.md"
    "licenses/README.md"
    "licenses/sherpa-onnx-LICENSE"
    "licenses/ONNX-Runtime/LICENSE"
    "licenses/SenseVoiceSmall/MODEL-PACKAGE-README.md"
    "third-party/sbom-linux-x64.cdx.json"
    "third-party/maven-runtime-dependencies.txt"
    "third-party/maven-test-dependencies.txt"
    "third-party/maven-licenses.txt"
)

for relative in "${required_files[@]}"; do
    [[ -f "${ROOT}/${relative}" ]] || { echo "缺少应用包文件：${relative}" >&2; exit 1; }
done

for forbidden in jdk ffmpeg models external/sherpa-onnx-native; do
    [[ ! -e "${ROOT}/${forbidden}" ]] || {
        echo "应用包不应包含外部依赖目录：${forbidden}" >&2
        exit 1
    }
done

python3 - "${ROOT}/app/offline-asr.jar" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as zf:
    names = zf.namelist()
required = 'BOOT-INF/lib/sherpa-onnx-jvm-2.0.3.jar'
if required not in names:
    raise SystemExit(f'应用 JAR 缺少 Java Wrapper：{required}')
foreign = [name for name in names if 'sherpa-onnx-native-lib-' in name]
if foreign:
    raise SystemExit(f'应用 JAR 不应包含外部 Native Runtime：{foreign}')
PY

[[ -f "${MANIFEST}" ]] || {
    echo "缺少 checksums.sha256；请先执行 linux-generate-checksums.sh" >&2
    exit 1
}

listed=""
verified=0
while IFS= read -r line || [[ -n "${line}" ]]; do
    [[ -z "${line}" || "${line}" == \#* ]] && continue
    if [[ ! "${line}" =~ ^([a-fA-F0-9]{64})[[:space:]][[:space:]](.+)$ ]]; then
        echo "哈希清单格式错误：${line}" >&2
        exit 1
    fi
    expected="${BASH_REMATCH[1],,}"
    relative="${BASH_REMATCH[2]}"
    [[ "${relative}" != /* && "${relative}" != *".."* ]] || {
        echo "哈希清单包含非法路径：${relative}" >&2
        exit 1
    }
    [[ ":${listed}:" != *":${relative}:"* ]] || {
        echo "哈希清单存在重复路径：${relative}" >&2
        exit 1
    }
    listed="${listed}:${relative}"
    file="${ROOT}/${relative}"
    [[ -f "${file}" ]] || { echo "哈希清单引用的文件不存在：${relative}" >&2; exit 1; }
    actual="$(sha256_value "${file}")"
    [[ "${actual}" == "${expected}" ]] || { echo "SHA-256 不匹配：${relative}" >&2; exit 1; }
    verified=$((verified + 1))
done < "${MANIFEST}"

(( verified >= 8 )) || { echo "校验文件数量不足：${verified}" >&2; exit 1; }

while IFS= read -r -d '' file; do
    relative="${file#${ROOT}/}"
    [[ "${relative}" == "checksums.sha256" ]] && continue
    if [[ ":${listed}:" != *":${relative}:"* ]]; then
        echo "发现未纳入哈希清单的文件：${relative}" >&2
        exit 1
    fi
done < <(find "${ROOT}" -type f ! -path "${ROOT}/logs/*" ! -path "${ROOT}/temp/*" -print0)

echo "Linux 应用包静态自检通过，已验证 ${verified} 个文件。"
echo "本包不含 Java Runtime、sherpa-onnx Native Runtime、模型或 FFmpeg。"
echo "外部依赖验收需在目标机按 docs/08-第三方组件与许可证清单.md 完成。"
