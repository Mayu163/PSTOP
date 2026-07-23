[CmdletBinding(PositionalBinding = $false)]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $GradleArguments
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot
$ToolingRoot = Join-Path $RepoRoot '.tooling'
$JavaHome = Join-Path $ToolingRoot 'jdk'
$GradleHome = Join-Path $ToolingRoot 'gradle-8.10.2'

if (
    -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe')) -or
    -not (Test-Path -LiteralPath (Join-Path $GradleHome 'bin\gradle.bat'))
) {
    & (Join-Path $PSScriptRoot 'Bootstrap.ps1')
}

$env:JAVA_HOME = $JavaHome
$env:GRADLE_USER_HOME = Join-Path $RepoRoot '.gradle'
$Gradle = Join-Path $GradleHome 'bin\gradle.bat'

Push-Location $RepoRoot
try {
    & $Gradle @GradleArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle exited with code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}
