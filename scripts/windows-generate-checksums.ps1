param(
    [Parameter(Mandatory = $false)]
    [string]$Root = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path -LiteralPath $Root).Path
$manifestPath = Join-Path $Root 'checksums.sha256'
$assets = @(
    'app/offline-asr.jar',
    'models/SenseVoiceSmall/model.int8.onnx',
    'models/SenseVoiceSmall/tokens.txt',
    'ffmpeg/bin/ffmpeg.exe'
)

$lines = @('# SHA-256 generated for the offline ASR deployment package')
foreach ($relativePath in $assets) {
    $path = Join-Path $Root $relativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "无法生成清单，缺少资产: $relativePath"
    }
    $hash = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    $lines += "$hash  $relativePath"
}

Set-Content -LiteralPath $manifestPath -Value $lines -Encoding UTF8
Write-Host "已生成发布哈希清单: $manifestPath"
