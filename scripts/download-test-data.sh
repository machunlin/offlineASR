#!/usr/bin/env bash
set -euo pipefail

# 中文注释：从项目目录定位测试音频目录，确保脚本可从任意工作目录执行。
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DATA_DIR="$PROJECT_ROOT/src/test/resources/test-data"
TEMPLATE="$DATA_DIR/manifest-template.tsv"
MANIFEST="$DATA_DIR/manifest.tsv"
CACHE_DIR="$DATA_DIR/.download-cache"

[[ -f "$TEMPLATE" ]] || { echo "测试集清单模板不存在：$TEMPLATE" >&2; exit 1; }
command -v curl >/dev/null 2>&1 || { echo "缺少 curl" >&2; exit 1; }
command -v ffprobe >/dev/null 2>&1 || { echo "未找到 ffprobe，请先安装 FFmpeg 并加入 PATH" >&2; exit 1; }

mkdir -p "$DATA_DIR" "$CACHE_DIR"
printf 'id\tfile\treference\tsource_url\tdownload_url\ttranscript_url\tlicense\tdataset\tspeaker\tcategory\tsample_rate_hz\tduration_seconds\tsha256\n' > "$MANIFEST"

while IFS= read -r line; do
    [[ "$line" == id
    audio_path="$DATA_DIR/$file"
    if [[ -n "$download_url" ]]; then
        curl --fail --location --retry 3 --silent --show-error "$download_url" -o "$audio_path"
        transcript_path="$CACHE_DIR/$id.trn"
        curl --fail --location --retry 3 --silent --show-error "$transcript_url" -o "$transcript_path"
        source_text="$(head -n 1 "$transcript_path" | tr -d '[:space:]')"
        expected_text="$(printf '%s' "$reference" | tr -d '[:space:]')"
        [[ "$source_text" == "$expected_text" ]] || { echo "参考文本与上游标注不一致：$id" >&2; exit 1; }
        rm -f "$transcript_path"
    fi

    [[ -f "$audio_path" ]] || { echo "音频文件缺失：$audio_path" >&2; exit 1; }
    sample_rate="$(ffprobe -v error -select_streams a:0 -show_entries stream=sample_rate -of csv=p=0 "$audio_path")"
    duration="$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$audio_path")"
    [[ -n "$sample_rate" && -n "$duration" ]] || { echo "音频元数据读取失败：$file" >&2; exit 1; }
    if command -v shasum >/dev/null 2>&1; then
        sha256="$(shasum -a 256 "$audio_path" | awk '{print $1}')"
    else
        sha256="$(sha256sum "$audio_path" | awk '{print $1}')"
    fi
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%.6f\t%s\n' \
        "$id" "$file" "$reference" "$source_url" "$download_url" "$transcript_url" "$license" "$dataset" "$speaker" "$category" "$sample_rate" "$duration" "$sha256" >> "$MANIFEST"
    printf '已核验 %s：%s 秒，%s Hz\n' "$file" "$duration" "$sample_rate"
done < "$TEMPLATE"

echo "测试集准备完成：15 条样本，清单：$MANIFEST"
\t'* ]] && continue
    # 中文注释：使用非空白分隔符解析，避免 TSV 中连续空列导致字段错位。
    IFS='|' read -r id file reference source_url download_url transcript_url license dataset speaker category <<< "${line//
    audio_path="$DATA_DIR/$file"
    if [[ -n "$download_url" ]]; then
        curl --fail --location --retry 3 --silent --show-error "$download_url" -o "$audio_path"
        transcript_path="$CACHE_DIR/$id.trn"
        curl --fail --location --retry 3 --silent --show-error "$transcript_url" -o "$transcript_path"
        source_text="$(head -n 1 "$transcript_path" | tr -d '[:space:]')"
        expected_text="$(printf '%s' "$reference" | tr -d '[:space:]')"
        [[ "$source_text" == "$expected_text" ]] || { echo "参考文本与上游标注不一致：$id" >&2; exit 1; }
        rm -f "$transcript_path"
    fi

    [[ -f "$audio_path" ]] || { echo "音频文件缺失：$audio_path" >&2; exit 1; }
    sample_rate="$(ffprobe -v error -select_streams a:0 -show_entries stream=sample_rate -of csv=p=0 "$audio_path")"
    duration="$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$audio_path")"
    [[ -n "$sample_rate" && -n "$duration" ]] || { echo "音频元数据读取失败：$file" >&2; exit 1; }
    if command -v shasum >/dev/null 2>&1; then
        sha256="$(shasum -a 256 "$audio_path" | awk '{print $1}')"
    else
        sha256="$(sha256sum "$audio_path" | awk '{print $1}')"
    fi
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%.6f\t%s\n' \
        "$id" "$file" "$reference" "$source_url" "$download_url" "$transcript_url" "$license" "$dataset" "$speaker" "$category" "$sample_rate" "$duration" "$sha256" >> "$MANIFEST"
    printf '已核验 %s：%s 秒，%s Hz\n' "$file" "$duration" "$sample_rate"
done

echo "测试集准备完成：15 条样本，清单：$MANIFEST"
\t'/|}"
    audio_path="$DATA_DIR/$file"
    if [[ -n "$download_url" ]]; then
        curl --fail --location --retry 3 --silent --show-error "$download_url" -o "$audio_path"
        transcript_path="$CACHE_DIR/$id.trn"
        curl --fail --location --retry 3 --silent --show-error "$transcript_url" -o "$transcript_path"
        source_text="$(head -n 1 "$transcript_path" | tr -d '[:space:]')"
        expected_text="$(printf '%s' "$reference" | tr -d '[:space:]')"
        [[ "$source_text" == "$expected_text" ]] || { echo "参考文本与上游标注不一致：$id" >&2; exit 1; }
        rm -f "$transcript_path"
    fi

    [[ -f "$audio_path" ]] || { echo "音频文件缺失：$audio_path" >&2; exit 1; }
    sample_rate="$(ffprobe -v error -select_streams a:0 -show_entries stream=sample_rate -of csv=p=0 "$audio_path")"
    duration="$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$audio_path")"
    [[ -n "$sample_rate" && -n "$duration" ]] || { echo "音频元数据读取失败：$file" >&2; exit 1; }
    if command -v shasum >/dev/null 2>&1; then
        sha256="$(shasum -a 256 "$audio_path" | awk '{print $1}')"
    else
        sha256="$(sha256sum "$audio_path" | awk '{print $1}')"
    fi
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%.6f\t%s\n' \
        "$id" "$file" "$reference" "$source_url" "$download_url" "$transcript_url" "$license" "$dataset" "$speaker" "$category" "$sample_rate" "$duration" "$sha256" >> "$MANIFEST"
    printf '已核验 %s：%s 秒，%s Hz\n' "$file" "$duration" "$sample_rate"
done

echo "测试集准备完成：15 条样本，清单：$MANIFEST"
