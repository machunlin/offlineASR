#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
ROOT="$(cd "${ROOT}" && pwd)"
MANIFEST="${ROOT}/checksums.sha256"
TMP_FILE="$(mktemp)"
trap 'rm -f "${TMP_FILE}"' EXIT

cd "${ROOT}"
while IFS= read -r -d '' file; do
    relative="${file#./}"
    sha256sum -- "${relative}" >> "${TMP_FILE}"
done < <(find . -type f ! -name 'checksums.sha256' -print0 | sort -z)

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
