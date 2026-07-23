[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'Invoke-Gradle.ps1') clean check packageRelease packageExe packageStandaloneExe
& (Join-Path $PSScriptRoot 'Test-Standalone.ps1') -SkipBuild

Write-Host ''
Write-Host 'Pstop single-file executable: dist\Pstop.exe'
Write-Host 'Pstop PowerShell package: dist\pstop\pstop.ps1'
Write-Host 'Pstop Windows executable: dist\exe\Pstop\Pstop.exe'
