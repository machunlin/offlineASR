param(
    [Parameter(Mandatory = $false)]
    [string]$Label = 'windows-x64',

    [Parameter(Mandatory = $false)]
    [string]$Root = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path -LiteralPath $Root).Path

$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { (Get-Command java.exe -ErrorAction Stop).Source }
$previousErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$javaVersion = (& $java -version 2>&1 | Out-String)
$javaExitCode = $LASTEXITCODE
$ErrorActionPreference = $previousErrorActionPreference
if ($javaExitCode -ne 0 -or $javaVersion -notmatch 'version "11') {
    throw 'Maven 依赖清单生成要求 Java 11，请检查 JAVA_HOME/PATH'
}
$mvn = if ($env:MAVEN_HOME) { Join-Path $env:MAVEN_HOME 'bin/mvn.cmd' } else { (Get-Command mvn.cmd -ErrorAction Stop).Source }

Push-Location $Root
try {
    & $mvn -B '-Dmaven.repo.local=.m2' dependency:tree '-Dscope=runtime' "-DoutputFile=deploy/maven-runtime-dependencies-$Label.txt"
    if ($LASTEXITCODE -ne 0) { throw '运行时 Maven 依赖树生成失败' }
    & $mvn -B '-Dmaven.repo.local=.m2' dependency:tree '-Dscope=test' "-DoutputFile=deploy/maven-test-dependencies-$Label.txt"
    if ($LASTEXITCODE -ne 0) { throw '测试 Maven 依赖树生成失败' }
    & $mvn -B '-Dmaven.repo.local=.m2' 'org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeBom' "-DoutputDirectory=$Root/deploy" "-DoutputName=sbom-$Label.cdx" '-DoutputFormat=json'
    if ($LASTEXITCODE -ne 0) { throw 'CycloneDX SBOM 生成失败' }
    & node "$Root/scripts/normalize-maven-bom.mjs" $Label
    if ($LASTEXITCODE -ne 0) { throw 'SBOM 元数据补齐失败' }
    & node "$Root/scripts/verify-maven-inventory.mjs" $Label
    if ($LASTEXITCODE -ne 0) { throw 'Maven 依赖清单校验失败' }
    & node "$Root/scripts/export-maven-licenses.mjs" $Label
    if ($LASTEXITCODE -ne 0) { throw 'Maven 许可证清单导出失败' }
}
finally {
    Pop-Location
}
