[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$RepoRoot = Split-Path -Parent $PSScriptRoot
$ToolingRoot = Join-Path $RepoRoot '.tooling'
$JdkRoot = Join-Path $ToolingRoot 'jdk'
$GradleVersion = '8.10.2'
$GradleRoot = Join-Path $ToolingRoot "gradle-$GradleVersion"
$Downloads = Join-Path $ToolingRoot 'downloads'

New-Item -ItemType Directory -Force -Path $ToolingRoot, $Downloads | Out-Null

if (-not (Test-Path -LiteralPath (Join-Path $JdkRoot 'bin\java.exe'))) {
    Write-Host 'Downloading the portable Eclipse Temurin JDK 21...'
    $JdkArchive = Join-Path $Downloads 'temurin-jdk-21.zip'
    $JdkUrl = 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse'
    Invoke-WebRequest -Uri $JdkUrl -OutFile $JdkArchive

    $JdkExtract = Join-Path $ToolingRoot 'jdk-extract'
    if (Test-Path -LiteralPath $JdkExtract) {
        Remove-Item -Recurse -Force -LiteralPath $JdkExtract
    }
    Expand-Archive -LiteralPath $JdkArchive -DestinationPath $JdkExtract
    $ExtractedJdk = Get-ChildItem -Directory -LiteralPath $JdkExtract | Select-Object -First 1
    Move-Item -LiteralPath $ExtractedJdk.FullName -Destination $JdkRoot
    Remove-Item -Recurse -Force -LiteralPath $JdkExtract
}

if (-not (Test-Path -LiteralPath (Join-Path $GradleRoot 'bin\gradle.bat'))) {
    Write-Host "Downloading Gradle $GradleVersion..."
    $GradleArchive = Join-Path $Downloads "gradle-$GradleVersion-bin.zip"
    $GradleUrl = "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip"
    Invoke-WebRequest -Uri $GradleUrl -OutFile $GradleArchive
    Expand-Archive -LiteralPath $GradleArchive -DestinationPath $ToolingRoot
}

Write-Host 'Portable build toolchain is ready.'
Write-Host "JDK:    $JdkRoot"
Write-Host "Gradle: $GradleRoot"
