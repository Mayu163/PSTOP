[CmdletBinding(PositionalBinding = $false)]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $PstopArguments
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot
$PackagedPstop = Join-Path $RepoRoot 'dist\Pstop.exe'

if (-not (Test-Path -LiteralPath $PackagedPstop)) {
    & (Join-Path $PSScriptRoot 'Build.ps1')
}

& $PackagedPstop @PstopArguments
exit $LASTEXITCODE
