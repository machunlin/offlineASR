$ErrorActionPreference = "Stop"

# 中文注释：从项目目录定位测试音频目录，确保脚本可从任意工作目录执行。
$projectRoot = Split-Path -Parent $PSScriptRoot
$dataDir = Join-Path $projectRoot "src/test/resources/test-data"
$templatePath = Join-Path $dataDir "manifest-template.tsv"
$manifestPath = Join-Path $dataDir "manifest.tsv"
$cacheDir = Join-Path $dataDir ".download-cache"

if (-not (Test-Path $templatePath)) { throw "测试集清单模板不存在：$templatePath" }
if (-not (Get-Command ffprobe -ErrorAction SilentlyContinue)) {
    throw "未找到 ffprobe，请先安装 FFmpeg 并确保其 bin 目录已加入 PATH。"
}

New-Item -ItemType Directory -Force -Path $dataDir, $cacheDir | Out-Null
$rows = Import-Csv -Path $templatePath -Delimiter "`t" -Encoding UTF8
$outputLines = [System.Collections.Generic.List[string]]::new()
$outputLines.Add("id`tfile	reference	source_url	download_url	transcript_url	license	dataset	speaker	category	sample_rate_hz	duration_seconds	sha256")

foreach ($row in $rows) {
    $audioPath = Join-Path $dataDir $row.file
    if (-not [string]::IsNullOrWhiteSpace($row.download_url)) {
        Invoke-WebRequest -Uri $row.download_url -OutFile $audioPath
        $transcriptPath = Join-Path $cacheDir ($row.id + ".trn")
        Invoke-WebRequest -Uri $row.transcript_url -OutFile $transcriptPath
        $sourceText = (Get-Content -Path $transcriptPath -Encoding UTF8 -TotalCount 1) -replace "\s", ""
        $expectedText = $row.reference -replace "\s", ""
        if ($sourceText -ne $expectedText) {
            throw "参考文本与上游标注不一致：$($row.id)，请检查来源文件。"
        }
        Remove-Item -LiteralPath $transcriptPath -Force
    }

    if (-not (Test-Path $audioPath)) { throw "音频文件缺失：$audioPath" }
    $probeJson = & ffprobe -v error -select_streams a:0 -show_entries stream=sample_rate -show_entries format=duration -of json $audioPath
    if ($LASTEXITCODE -ne 0) { throw "音频元数据读取失败：$($row.file)" }
    $probe = ($probeJson -join "`n") | ConvertFrom-Json
    if (-not $probe.streams -or -not $probe.format.duration) { throw "音频元数据不完整：$($row.file)" }
    $sampleRate = [int]$probe.streams[0].sample_rate
    $duration = [double]::Parse([string]$probe.format.duration, [System.Globalization.CultureInfo]::InvariantCulture)
    $sha256 = (Get-FileHash -Path $audioPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $values = @($row.id, $row.file, $row.reference, $row.source_url, $row.download_url, $row.transcript_url, $row.license, $row.dataset, $row.speaker, $row.category, $sampleRate, $duration.ToString("F6", [System.Globalization.CultureInfo]::InvariantCulture), $sha256)
    $outputLines.Add(($values -join "`t"))
    Write-Host ("已核验 {0}：{1} 秒，{2} Hz" -f $row.file, $duration.ToString("F2"), $sampleRate)
}

[System.IO.File]::WriteAllText($manifestPath, (($outputLines -join "`n") + "`n"), [System.Text.UTF8Encoding]::new($false))
Write-Host "测试集准备完成：15 条样本，清单：$manifestPath"
