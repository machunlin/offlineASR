#!/usr/bin/env bash
set -euo pipefail

# 中文注释：下载官方 sherpa-onnx Linux x64 原生库，并校验固定版本的 SHA-256。
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="2.0.3"
ASSET="sherpa-onnx-native-lib-linux-x64-${VERSION}.jar"
URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v${VERSION}/${ASSET}"
EXPECTED_SHA256="42330fbcfe4f771386545397ebf8a8dd2176ff913a254331226c01ad87982e17"
TARGET="${ROOT}/lib/${ASSET}"

mkdir -p "${ROOT}/lib"
command -v curl >/dev/null 2>&1 || { echo "缺少 curl，无法下载 Linux 原生库" >&2; exit 1; }

if [[ -f "${TARGET}" ]]; then
    echo "检测到已有 Linux 原生库，先校验：${TARGET}"
else
    echo "正在下载官方 Linux x64 原生库：${URL}"
    curl --fail --location --retry 3 --connect-timeout 20 "${URL}" -o "${TARGET}.download"
    mv "${TARGET}.download" "${TARGET}"
fi

if command -v sha256sum >/dev/null 2>&1; then
    ACTUAL_SHA256="$(sha256sum "${TARGET}" | awk '{print $1}')"
elif command -v shasum >/dev/null 2>&1; then
    ACTUAL_SHA256="$(shasum -a 256 "${TARGET}" | awk '{print $1}')"
else
    echo "缺少 sha256sum 或 shasum，无法验证文件完整性" >&2
    exit 1
fi

if [[ "${ACTUAL_SHA256}" != "${EXPECTED_SHA256}" ]]; then
    rm -f "${TARGET}"
    echo "Linux 原生库 SHA-256 校验失败，已删除下载文件" >&2
    echo "期望：${EXPECTED_SHA256}" >&2
    echo "实际：${ACTUAL_SHA256}" >&2
    exit 1
fi

echo "${ACTUAL_SHA256}  ${ASSET}" > "${ROOT}/lib/linux-x64-sha256.txt"
echo "Linux x64 原生库校验通过：${TARGET}"
