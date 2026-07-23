[CmdletBinding(PositionalBinding = $false)]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $PstopArguments
)

$ErrorActionPreference = 'Stop'
$Java = Join-Path $PSScriptRoot 'runtime\bin\java.exe'
$Libraries = Get-ChildItem -File -Filter '*.jar' -LiteralPath (Join-Path $PSScriptRoot 'lib')
$ClassPath = ($Libraries.FullName -join [IO.Path]::PathSeparator)

& $Java `
    '-Dfile.encoding=UTF-8' `
    '-Dstdout.encoding=UTF-8' `
    '-Dstderr.encoding=UTF-8' `
    -classpath $ClassPath `
    'dev.pstop.MainKt' `
    @PstopArguments
exit $LASTEXITCODE
