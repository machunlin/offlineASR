param(
    [Parameter(Mandatory = $false)]
    [string]$Root = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
$requiredFiles = @(
    'appoffline-asr.jar',
    'jdkinjava.exe',
    'modelsSenseVoiceSmallmodel.int8.onnx',
    'modelsSenseVoiceSmall	okens.txt',
    'ffmpeginfmpeg.exe',
    'configapplication.yml',
    'scriptswindows-start.bat',
    'scriptswindows-status.bat'
)

foreach ($relativePath in $requiredFiles) {
    $path = Join-Path $Root $relativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "缺少部署资产: $relativePath"
    }
}

$java = Join-Path $Root 'jdkinjava.exe'
$ffmpeg = Join-Path $Root 'ffmpeginfmpeg.exe'
& $java -version
if ($LASTEXITCODE -ne 0) { throw 'Java Runtime 无法执行' }
& $ffmpeg -version
if ($LASTEXITCODE -ne 0) { throw 'FFmpeg 无法执行' }

$manifestPath = Join-Path $Root 'checksums.sha256'
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    throw '缺少 checksums.sha256；部署包必须在发布前生成哈希清单'
}

foreach ($line in Get-Content -LiteralPath $manifestPath) {
    if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) { continue }
    $parts = $line -split 's+', 2
    if ($parts.Length -ne 2) { throw "哈希清单格式错误: $line" }
    $assetPath = Join-Path $Root $parts[1]
    if (-not (Test-Path -LiteralPath $assetPath -PathType Leaf)) {
        throw "哈希清单引用了缺失文件: $($parts[1])"
    }
    $actual = (Get-FileHash -LiteralPath $assetPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $parts[0].ToLowerInvariant()) {
        throw "SHA-256 不匹配: $($parts[1])"
    }
}

Write-Host 'Windows 离线部署包静态自检通过。仍需在本机完成 JNI、麦克风、服务和断网实测。'
