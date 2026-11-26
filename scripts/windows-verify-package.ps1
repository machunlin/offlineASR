param(
    [Parameter(Mandatory = $false)]
    [string]$Root = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path -LiteralPath $Root).Path
$requiredFiles = @(
    'app/offline-asr.jar',
    'jdk/bin/java.exe',
    'models/SenseVoiceSmall/model.int8.onnx',
    'models/SenseVoiceSmall/tokens.txt',
    'ffmpeg/bin/ffmpeg.exe',
    'config/application.yml',
    'scripts/windows-start.bat',
    'scripts/windows-status.bat'
)

foreach ($relativePath in $requiredFiles) {
    $path = Join-Path $Root $relativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "缺少部署资产: $relativePath"
    }
}

$java = Join-Path $Root 'jdk/bin/java.exe'
$ffmpeg = Join-Path $Root 'ffmpeg/bin/ffmpeg.exe'
& $java -version
if ($LASTEXITCODE -ne 0) { throw 'Java Runtime 无法执行' }
& $ffmpeg -version
if ($LASTEXITCODE -ne 0) { throw 'FFmpeg 无法执行' }

$manifestPath = Join-Path $Root 'checksums.sha256'
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    throw '缺少 checksums.sha256；部署包必须先执行 windows-generate-checksums.ps1'
}

$verifiedCount = 0
foreach ($line in Get-Content -LiteralPath $manifestPath) {
    if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) { continue }
    $parts = $line -split '\s+', 2
    if ($parts.Length -ne 2 -or $parts[0] -notmatch '^[a-fA-F0-9]{64}$') {
        throw "哈希清单格式错误: $line"
    }
    $assetPath = Join-Path $Root $parts[1]
    if (-not (Test-Path -LiteralPath $assetPath -PathType Leaf)) {
        throw "哈希清单引用了缺失文件: $($parts[1])"
    }
    $actual = (Get-FileHash -LiteralPath $assetPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $parts[0].ToLowerInvariant()) {
        throw "SHA-256 不匹配: $($parts[1])"
    }
    $verifiedCount++
}

if ($verifiedCount -lt 4) {
    throw "哈希清单资产数量不足，期望至少 4 项，实际 $verifiedCount 项"
}

Write-Host "Windows 离线部署包静态自检通过，已验证 $verifiedCount 个文件。"
Write-Host '注意：该检查不替代 Windows x64 JNI、Chrome 麦克风及断网实机测试。'
