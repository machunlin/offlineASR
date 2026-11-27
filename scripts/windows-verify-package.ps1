param(
    [Parameter(Mandatory = $false)]
    [string]$Root = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path -LiteralPath $Root).Path
$requiredFiles = @(
    'app/offline-asr.jar',
    'config/application.yml',
    'scripts/windows-start.bat',
    'scripts/windows-diagnose.bat',
    'scripts/windows-status.bat',
    'scripts/offline-asr.xml',
    'LICENSE',
    'THIRD_PARTY_NOTICES.md',
    'docs/08-第三方组件与许可证清单.md',
    'licenses/README.md',
    'licenses/sherpa-onnx-LICENSE',
    'licenses/ONNX-Runtime/LICENSE',
    'licenses/SenseVoiceSmall/MODEL-PACKAGE-README.md',
    'third-party/sbom-windows-x64.cdx.json',
    'third-party/maven-runtime-dependencies.txt',
    'third-party/maven-test-dependencies.txt',
    'third-party/maven-licenses.txt'
)

foreach ($relativePath in $requiredFiles) {
    if (-not (Test-Path -LiteralPath (Join-Path $Root $relativePath) -PathType Leaf)) {
        throw "缺少应用包文件: $relativePath"
    }
}

foreach ($forbidden in @('jdk', 'ffmpeg', 'models', 'external/sherpa-onnx-native')) {
    if (Test-Path -LiteralPath (Join-Path $Root $forbidden)) {
        throw "应用包不应包含外部依赖目录: $forbidden"
    }
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$jarPath = Join-Path $Root 'app/offline-asr.jar'
$jar = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
try {
    $entries = $jar.Entries | ForEach-Object { $_.FullName }
    if (-not ($entries -contains 'BOOT-INF/lib/sherpa-onnx-jvm-2.0.3.jar')) {
        throw '应用 JAR 缺少 sherpa-onnx Java Wrapper'
    }
    $foreignJars = $entries | Where-Object { $_ -match 'sherpa-onnx-native-lib-' }
    if ($foreignJars) {
        throw "应用 JAR 不应包含外部 Native Runtime: $($foreignJars -join ', ')"
    }
}
finally {
    $jar.Dispose()
}

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
    $segments = $relativePath -split '[\\/]'
    if ([System.IO.Path]::IsPathRooted($relativePath) -or $segments -contains '..') {
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

if ($verifiedCount -lt 8) {
    throw "哈希清单资产数量不足，实际 $verifiedCount 项"
}

$logsPrefix = (Join-Path $Root 'logs').TrimEnd('\') + '\'
$tempPrefix = (Join-Path $Root 'temp').TrimEnd('\') + '\'
$actualFiles = Get-ChildItem -LiteralPath $Root -File -Recurse |
    Where-Object {
        $_.FullName -ne $manifestPath -and
        -not $_.FullName.StartsWith($logsPrefix, [System.StringComparison]::OrdinalIgnoreCase) -and
        -not $_.FullName.StartsWith($tempPrefix, [System.StringComparison]::OrdinalIgnoreCase)
    }
foreach ($file in $actualFiles) {
    $relativePath = $file.FullName.Substring($Root.Length).TrimStart('\', '/')
    if (-not $listed.ContainsKey($relativePath)) {
        throw "发现未纳入哈希清单的文件: $relativePath"
    }
}

Write-Host "Windows 应用包静态自检通过，已验证 $verifiedCount 个文件。"
Write-Host '本包不含 Java Runtime、sherpa-onnx Native Runtime、模型或 FFmpeg。'
Write-Host '外部依赖验收需在目标机按 docs/08-第三方组件与许可证清单.md 完成。'
