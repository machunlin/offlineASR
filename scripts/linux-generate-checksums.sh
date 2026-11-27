#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
ROOT="$(cd "${ROOT}" && pwd)"
MANIFEST="${ROOT}/checksums.sha256"
TMP_FILE="$(mktemp)"
trap 'rm -f "${TMP_FILE}"' EXIT

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

cd "${ROOT}"
while IFS= read -r -d '' file; do
    relative="${file#./}"
    hash="$(sha256_value "${ROOT}/${relative}")"
    printf '%s  %s\n' "${hash}" "${relative}" >> "${TMP_FILE}"
done < <(find . -type f ! -name 'checksums.sha256' ! -path './logs/*' ! -path './temp/*' -print0 | sort -z)

if [[ ! -s "${TMP_FILE}" ]]; then
    echo "没有找到可纳入校验清单的文件" >&2
    exit 1
fi

{
    echo "# OfflineASR deployment package SHA-256 manifest"
    cat "${TMP_FILE}"
} > "${MANIFEST}"

echo "已生成校验清单：${MANIFEST}"
echo "文件数量：$(wc -l < "${TMP_FILE}" | tr -d ' ')"
