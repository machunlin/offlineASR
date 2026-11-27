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
$javaProperties = (& $java -XshowSettings:properties -version 2>&1 | Out-String)
if ($javaProperties -notmatch 'java.specification.version$s*=$s*11') {
    throw '部署包必须使用 Java 11 Runtime'
}
if ($javaProperties -notmatch 'os.arch$s*=$s*(amd64|x86_64)') {
    throw '部署包必须使用 64 位 x64 Java Runtime'
}
& $ffmpeg -version
if ($LASTEXITCODE -ne 0) { throw 'FFmpeg 无法执行' }

$manifestPath = Join-Path $Root 'checksums.sha256'
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    throw '缺少 checksums.sha256；请先执行 windows-generate-checksums.ps1'
}

$listed = @{}
$verifiedCount = 0
foreach ($line in Get-Content -LiteralPath $manifestPath) {
    if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) { continue }
    if ($line -notmatch '^([a-fA-F0-9]{64})  (.+)$') {
        throw "哈希清单格式错误: $line"
    }

    $expected = $Matches[1].ToLowerInvariant()
    $relativePath = $Matches[2].Replace('/', '\')
    if ([System.IO.Path]::IsPathRooted($relativePath) -or $relativePath -match '(^|\)\.\.(\|$$)') {
        throw "哈希清单包含非法路径: $relativePath"
    }
    if ($listed.ContainsKey($relativePath)) {
        throw "哈希清单存在重复路径: $relativePath"
    }
    $listed[$relativePath] = $true

    $assetPath = Join-Path $Root $relativePath
    if (-not (Test-Path -LiteralPath $assetPath -PathType Leaf)) {
        throw "哈希清单引用了缺失文件: $relativePath"
    }
    $actual = (Get-FileHash -LiteralPath $assetPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $expected) {
        throw "SHA-256 不匹配: $relativePath"
    }
    $verifiedCount++
}

if ($verifiedCount -lt 5) {
    throw "哈希清单资产数量不足，实际 $verifiedCount 项"
}

$actualFiles = Get-ChildItem -LiteralPath $Root -File -Recurse |
    Where-Object { $_.FullName -ne $manifestPath }
foreach ($file in $actualFiles) {
    $relativePath = $file.FullName.Substring($Root.Length).TrimStart('\', '/')
    if (-not $listed.ContainsKey($relativePath)) {
        throw "发现未纳入哈希清单的文件: $relativePath"
    }
}

Write-Host "Windows 离线部署包静态自检通过，已验证 $verifiedCount 个文件。"
Write-Host '注意：该检查不替代 Windows x64 JNI、Chrome 麦克风及断网实机测试。'
