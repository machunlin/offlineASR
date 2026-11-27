param(
    [Parameter(Mandatory = $false)]
    [string]$Root = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path -LiteralPath $Root).Path
$Package = Join-Path $Root 'dist/windows-x64'
if (Test-Path -LiteralPath $Package) {
    throw "输出目录已存在，为避免覆盖已有文件，请先人工检查并移走: $Package"
}

$jar = Join-Path $Root 'target/offline-asr-0.1.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
    throw '缺少应用 JAR'
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$jarZip = [System.IO.Compression.ZipFile]::OpenRead($jar)
try {
    $jarEntries = $jarZip.Entries | ForEach-Object { $_.FullName }
    if (-not ($jarEntries -contains 'BOOT-INF/lib/sherpa-onnx-jvm-2.0.3.jar')) {
        throw '应用 JAR 缺少 sherpa-onnx Java Wrapper'
    }
    $foreignJars = $jarEntries | Where-Object { $_ -match 'sherpa-onnx-native-lib-' }
    if ($foreignJars) {
        throw "应用 JAR 不应包含外部 Native Runtime: $($foreignJars -join ', ')"
    }
}
finally {
    $jarZip.Dispose()
}

$directories = @('app', 'config', 'logs', 'temp', 'scripts', 'docs', 'third-party', 'licenses')
foreach ($directory in $directories) {
    New-Item -ItemType Directory -Path (Join-Path $Package $directory) -Force | Out-Null
}

Copy-Item -LiteralPath $jar -Destination (Join-Path $Package 'app/offline-asr.jar')
Copy-Item -LiteralPath (Join-Path $Root 'deploy/windows-x64/config/application.yml') -Destination (Join-Path $Package 'config/application.yml')
Copy-Item -LiteralPath (Join-Path $Root 'deploy/windows-x64/README.txt') -Destination (Join-Path $Package 'README.txt')
Copy-Item -LiteralPath (Join-Path $Root 'LICENSE') -Destination (Join-Path $Package 'LICENSE')
Copy-Item -LiteralPath (Join-Path $Root 'THIRD_PARTY_NOTICES.md') -Destination (Join-Path $Package 'THIRD_PARTY_NOTICES.md')
Copy-Item -LiteralPath (Join-Path $Root 'docs/08-第三方组件与许可证清单.md') -Destination (Join-Path $Package 'docs/08-第三方组件与许可证清单.md')
Copy-Item -Path (Join-Path $Root 'deploy/licenses/*') -Destination (Join-Path $Package 'licenses') -Recurse

$scripts = @(
    'windows-start.bat', 'windows-diagnose.bat', 'windows-status.bat', 'windows-install.bat',
    'windows-uninstall-service.bat', 'windows-stop.bat', 'windows-uninstall.bat',
    'windows-generate-checksums.ps1', 'windows-verify-package.ps1'
)
foreach ($script in $scripts) {
    Copy-Item -LiteralPath (Join-Path $Root "scripts/$script") -Destination (Join-Path $Package "scripts/$script")
}
Copy-Item -LiteralPath (Join-Path $Root 'scripts/offline-asr.xml') -Destination (Join-Path $Package 'scripts/offline-asr.xml')
Copy-Item -LiteralPath (Join-Path $Root 'deploy/sbom-windows-x64.cdx.json') -Destination (Join-Path $Package 'third-party/sbom-windows-x64.cdx.json')
Copy-Item -LiteralPath (Join-Path $Root 'deploy/maven-runtime-dependencies-windows-x64.txt') -Destination (Join-Path $Package 'third-party/maven-runtime-dependencies.txt')
Copy-Item -LiteralPath (Join-Path $Root 'deploy/maven-test-dependencies-windows-x64.txt') -Destination (Join-Path $Package 'third-party/maven-test-dependencies.txt')
Copy-Item -LiteralPath (Join-Path $Root 'deploy/maven-licenses-windows-x64.txt') -Destination (Join-Path $Package 'third-party/maven-licenses.txt')

& (Join-Path $Root 'scripts/windows-generate-checksums.ps1') -Root $Package
if ($LASTEXITCODE -ne 0) { throw '应用包 SHA-256 清单生成失败' }
& (Join-Path $Root 'scripts/windows-verify-package.ps1') -Root $Package
if ($LASTEXITCODE -ne 0) { throw '应用包静态校验失败' }

Write-Host "Windows x64 应用包已生成并通过静态校验: $Package"
Write-Host '本包仅含应用文件；Java、sherpa-onnx Native Runtime、模型和 FFmpeg 由目标机自行准备。'
