#!/usr/bin/env bash
# OfflineASR Linux x64 实机验收脚本
# 只读检查运行环境并调用健康检查/识别接口；不安装软件、不重启服务、不修改网络。

set -uo pipefail

ROOT="${ASR_DEPLOY_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
BASE_URL="${ASR_BASE_URL:-http://127.0.0.1:8080}"
AUDIO_FILE="${1:-}"
if [[ ! -d "${ROOT}" ]]; then
  echo "[失败] 部署目录不存在：${ROOT}" >&2
  exit 1
fi

TIMESTAMP="$(date '+%Y%m%d%H%M%S')"
LOG_DIR="${ROOT}/logs"
if ! mkdir -p "${LOG_DIR}"; then
  echo "[失败] 无法创建日志目录：${LOG_DIR}" >&2
  exit 1
fi
LOG_FILE="${LOG_DIR}/acceptance-${TIMESTAMP}.log"
PASS_COUNT=0
FAIL_COUNT=0
WARN_COUNT=0

exec > >(tee -a "${LOG_FILE}") 2>&1

pass() { PASS_COUNT=$((PASS_COUNT + 1)); printf '[通过] %s\n' "$1"; }
fail() { FAIL_COUNT=$((FAIL_COUNT + 1)); printf '[失败] %s\n' "$1"; }
warn() { WARN_COUNT=$((WARN_COUNT + 1)); printf '[警告] %s\n' "$1"; }
section() { printf '\n========== %s ==========\n' "$1"; }
has_command() { command -v "$1" >/dev/null 2>&1; }
check_file() {
  if [[ -f "$1" ]]; then pass "$2：$1"; else fail "$2缺失：$1"; fi
}

section "验收信息"
echo "时间：$(date '+%F %T %Z')"
echo "主机：$(hostname)"
echo "部署目录：${ROOT}"
echo "服务地址：${BASE_URL}"
echo "日志文件：${LOG_FILE}"
uname -a

if [[ ! -d "${ROOT}" ]]; then
  fail "部署目录不存在"
  exit 1
fi
if [[ "$(uname -s)" != Linux || "$(uname -m)" != x86_64 ]]; then
  fail "目标平台必须是 Linux x86_64"
  exit 1
fi
pass "目标平台为 Linux x86_64"

section "一、基础工具"
for cmd in curl python3 file; do
  if has_command "$cmd"; then pass "$cmd：$(command -v "$cmd")"; else fail "缺少命令：$cmd"; fi
done

section "二、Java 11"
if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
  JAVA_BIN="${JAVA_HOME}/bin/java"
elif has_command java; then
  JAVA_BIN="$(command -v java)"
else
  JAVA_BIN=""
fi
if [[ -n "${JAVA_BIN}" ]]; then
  JAVA_VERSION="$("${JAVA_BIN}" -version 2>&1 | sed -n '1p')"
  echo "Java：${JAVA_BIN}"
  echo "版本：${JAVA_VERSION}"
  if [[ "${JAVA_VERSION}" == *'version "11'* ]]; then pass "Java 11 版本正确"; else fail "当前 Java 不是 Java 11"; fi
else
  fail "未找到 Java"
fi

section "三、应用文件"
check_file "${ROOT}/app/offline-asr.jar" "应用 JAR"
check_file "${ROOT}/config/application.yml" "应用配置"
if [[ -f "${ROOT}/linux-start.sh" ]]; then pass "启动脚本存在"; else fail "缺少 linux-start.sh"; fi

if [[ -x "${ROOT}/scripts/linux-verify-package.sh" ]]; then
  if bash "${ROOT}/scripts/linux-verify-package.sh"; then pass "应用包校验通过"; else fail "应用包校验失败"; fi
else
  warn "未找到可执行的应用包校验脚本，跳过 SHA-256 校验"
fi

section "四、SenseVoice 模型"
MODEL_DIR="${ASR_MODEL_DIR:-${ROOT}/models/SenseVoiceSmall}"
check_file "${MODEL_DIR}/model.int8.onnx" "模型文件"
check_file "${MODEL_DIR}/tokens.txt" "模型词表"

section "五、sherpa-onnx Native Runtime"
NATIVE_DIR="${ASR_SHERPA_NATIVE_PATH:-${ROOT}/external/sherpa-onnx-native}"
check_file "${NATIVE_DIR}/libonnxruntime.so" "ONNX Runtime Native"
check_file "${NATIVE_DIR}/libsherpa-onnx-jni.so" "sherpa-onnx JNI"
if has_command file; then
  [[ -f "${NATIVE_DIR}/libonnxruntime.so" ]] && file "${NATIVE_DIR}/libonnxruntime.so"
  [[ -f "${NATIVE_DIR}/libsherpa-onnx-jni.so" ]] && file "${NATIVE_DIR}/libsherpa-onnx-jni.so"
fi

section "六、FFmpeg"
if [[ -n "${ASR_FFMPEG:-}" ]]; then
  if [[ "${ASR_FFMPEG}" == */* ]]; then FFMPEG_BIN="${ASR_FFMPEG}"; else FFMPEG_BIN="$(command -v "${ASR_FFMPEG}" 2>/dev/null || true)"; fi
else
  FFMPEG_BIN="$(command -v ffmpeg 2>/dev/null || true)"
fi
if [[ -n "${FFMPEG_BIN}" && -x "${FFMPEG_BIN}" ]]; then
  "${FFMPEG_BIN}" -version 2>&1 | sed -n '1p'
  pass "FFmpeg 可执行"
else
  fail "FFmpeg 不可用；检查 ASR_FFMPEG 或 PATH"
fi

section "七、服务健康检查"
HEALTH_FILE="$(mktemp)"
HTTP_CODE="$(curl -sS --connect-timeout 5 --max-time 15 -o "${HEALTH_FILE}" -w '%{http_code}' "${BASE_URL%/}/api/asr/health" 2>/dev/null || true)"
echo "HTTP 状态码：${HTTP_CODE:-请求失败}"
cat "${HEALTH_FILE}" 2>/dev/null || true
echo
if [[ "${HTTP_CODE}" == 200 ]]; then
  if python3 - "${HEALTH_FILE}" <<'PY'
import json, sys
try:
    with open(sys.argv[1], encoding="utf-8") as f:
        d = json.load(f)
except Exception as e:
    print("健康检查响应不是有效 JSON：", e)
    sys.exit(1)
print("健康检查 JSON 字段：", json.dumps(d, ensure_ascii=False))
# 兼容 Spring Boot HealthIndicator 常见结构及项目自定义结构。
def find_value(obj, key):
    if isinstance(obj, dict):
        if key in obj:
            return obj[key]
        for value in obj.values():
            found = find_value(value, key)
            if found is not None:
                return found
    elif isinstance(obj, list):
        for value in obj:
            found = find_value(value, key)
            if found is not None:
                return found
    return None
status = find_value(d, "applicationStatus")
loaded = find_value(d, "modelLoaded")
ffmpeg = find_value(d, "ffmpegAvailable")
if status is not None:
    ok = status == "READY" and loaded is True and ffmpeg is True
elif isinstance(d, dict) and d.get("status") == "UP":
    # 若健康端点只返回 Spring Boot UP，仍需依靠真实识别测试确认模型可推理。
    ok = True
else:
    ok = False
sys.exit(0 if ok else 1)
PY
  then pass "健康检查接口响应正常"; else fail "健康检查状态未达到项目验收要求，请核对响应字段和服务日志"; fi
else
  fail "健康检查请求失败"
fi
rm -f "${HEALTH_FILE}"

section "八、真实音频识别"
if [[ -z "${AUDIO_FILE}" ]]; then
  warn "未传入测试音频，跳过真实识别。用法：$0 /path/to/test.wav"
elif [[ ! -f "${AUDIO_FILE}" ]]; then
  fail "测试音频不存在：${AUDIO_FILE}"
else
  RESPONSE_FILE="$(mktemp)"
  HTTP_CODE="$(curl -sS --connect-timeout 5 --max-time 180 -X POST -F "file=@${AUDIO_FILE}" -o "${RESPONSE_FILE}" -w '%{http_code}' "${BASE_URL%/}/api/asr" 2>/dev/null || true)"
  echo "音频：${AUDIO_FILE}"
  echo "HTTP 状态码：${HTTP_CODE:-请求失败}"
  cat "${RESPONSE_FILE}" 2>/dev/null || true
  echo
  if [[ "${HTTP_CODE}" == 200 ]]; then
    if python3 - "${RESPONSE_FILE}" <<'PY'
import json, sys
try:
    with open(sys.argv[1], encoding="utf-8") as f:
        d = json.load(f)
except Exception as e:
    print("识别响应不是有效 JSON：", e)
    sys.exit(1)
for k in ("success", "requestId", "text", "audioDuration", "decodeTime", "inferenceTime", "totalTime"):
    print(f"{k}: {d.get(k)}")
ok = d.get("success") is True and isinstance(d.get("text"), str) and bool(d["text"].strip())
sys.exit(0 if ok else 1)
PY
    then pass "真实音频识别成功且文本非空"; else fail "识别接口未返回有效的非空识别文本"; fi
  else
    fail "真实音频识别 HTTP 请求失败"
  fi
  rm -f "${RESPONSE_FILE}"
fi

section "九、验收汇总"
echo "通过：${PASS_COUNT}"
echo "失败：${FAIL_COUNT}"
echo "警告：${WARN_COUNT}"
echo "日志：${LOG_FILE}"
if [[ "${FAIL_COUNT}" -eq 0 ]]; then
  echo "[结果] 自动检查未发现失败项；警告项及人工验收项仍需确认。"
  exit 0
else
  echo "[结果] 验收未通过，请根据日志处理失败项。"
  exit 1
fi
