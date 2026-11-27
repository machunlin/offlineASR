param(
    [Parameter(Mandatory = $false)]
    [string]$Root = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path -LiteralPath $Root).Path
$manifestPath = Join-Path $Root 'checksums.sha256'
$logsPrefix = (Join-Path $Root 'logs').TrimEnd('\') + '\'
$tempPrefix = (Join-Path $Root 'temp').TrimEnd('\') + '\'
$files = Get-ChildItem -LiteralPath $Root -File -Recurse |
    Where-Object {
        $_.FullName -ne $manifestPath -and
        -not $_.FullName.StartsWith($logsPrefix, [System.StringComparison]::OrdinalIgnoreCase) -and
        -not $_.FullName.StartsWith($tempPrefix, [System.StringComparison]::OrdinalIgnoreCase)
    } |
    Sort-Object FullName

if ($files.Count -lt 5) {
    throw "部署包文件数量不足，当前仅有 $($files.Count) 个文件。"
}

$lines = @('# OfflineASR deployment package SHA-256 manifest')
foreach ($file in $files) {
    $relativePath = $file.FullName.Substring($Root.Length).TrimStart('\', '/').Replace('\', '/')
    $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    $lines += "$hash  $relativePath"
}

Set-Content -LiteralPath $manifestPath -Value $lines -Encoding UTF8
Write-Host "已生成应用包 SHA-256 清单：$manifestPath"
Write-Host "纳入文件数量：$($files.Count)（不含运行时 logs/、temp/）"
