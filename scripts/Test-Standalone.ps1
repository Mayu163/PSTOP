[CmdletBinding()]
param(
    [switch] $SkipBuild
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot
$StandaloneExe = Join-Path $RepoRoot 'dist\Pstop.exe'
$TestRoot = Join-Path $RepoRoot 'build\standalone-smoke'
$IsolatedRoot = Join-Path $TestRoot 'isolated'
$CacheRoot = Join-Path $TestRoot 'cache'

if (-not (Test-Path -LiteralPath $StandaloneExe)) {
    if ($SkipBuild) {
        throw "Standalone executable not found: $StandaloneExe"
    }
    & (Join-Path $PSScriptRoot 'Build.ps1')
}

if (Test-Path -LiteralPath $TestRoot) {
    $ResolvedRepo = (Resolve-Path -LiteralPath $RepoRoot).Path
    $ResolvedTest = (Resolve-Path -LiteralPath $TestRoot).Path
    if (-not $ResolvedTest.StartsWith($ResolvedRepo, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to clean a standalone test path outside the repository: $ResolvedTest"
    }
    Remove-Item -Recurse -Force -LiteralPath $ResolvedTest
}

New-Item -ItemType Directory -Force -Path $IsolatedRoot, $CacheRoot | Out-Null
$IsolatedExe = Join-Path $IsolatedRoot 'Pstop.exe'
Copy-Item -LiteralPath $StandaloneExe -Destination $IsolatedExe

$PreviousCache = $env:PSTOP_CACHE_DIR
$PreviousJavaHome = $env:JAVA_HOME
$PreviousPath = $env:PATH
$LocationPushed = $false

try {
    $env:PSTOP_CACHE_DIR = $CacheRoot
    $env:JAVA_HOME = Join-Path $TestRoot 'deliberately-missing-java'
    $env:PATH = Join-Path $env:WINDIR 'System32'
    Push-Location -LiteralPath $IsolatedRoot
    $LocationPushed = $true

    $FirstLaunchTimer = [System.Diagnostics.Stopwatch]::StartNew()
    $VersionOutput = (& $IsolatedExe --version | Out-String).Trim()
    $VersionExitCode = $LASTEXITCODE
    $FirstLaunchTimer.Stop()
    if ($VersionExitCode -ne 0 -or $VersionOutput -ne 'Pstop 0.1.0') {
        throw "Standalone version check failed: exit=$VersionExitCode output='$VersionOutput'"
    }

    $WarmLaunchTimer = [System.Diagnostics.Stopwatch]::StartNew()
    $WarmVersionOutput = (& $IsolatedExe --version | Out-String).Trim()
    $WarmVersionExitCode = $LASTEXITCODE
    $WarmLaunchTimer.Stop()
    if ($WarmVersionExitCode -ne 0 -or $WarmVersionOutput -ne 'Pstop 0.1.0') {
        throw "Warm standalone version check failed: exit=$WarmVersionExitCode output='$WarmVersionOutput'"
    }

    $Snapshot = @(& $IsolatedExe --once --no-color --width 130 --height 41)
    $SnapshotExitCode = $LASTEXITCODE
    if ($SnapshotExitCode -ne 0) {
        throw "Standalone snapshot check failed with exit code $SnapshotExitCode."
    }

    $ConfigBeforeRepair = @(
        Get-ChildItem -Recurse -File -Filter 'Pstop.cfg' -LiteralPath $CacheRoot
    )
    if ($ConfigBeforeRepair.Count -ne 1) {
        throw "Expected exactly one extracted Pstop.cfg, found $($ConfigBeforeRepair.Count)."
    }
    Remove-Item -Force -LiteralPath $ConfigBeforeRepair[0].FullName

    $RepairOutput = (& $IsolatedExe --version | Out-String).Trim()
    $RepairExitCode = $LASTEXITCODE

    $AdjacentFiles = @(Get-ChildItem -Force -LiteralPath $IsolatedRoot)
    $ExtractedConfig = @(Get-ChildItem -Recurse -File -Filter 'Pstop.cfg' -LiteralPath $CacheRoot)
    $ExtractedJvm = @(Get-ChildItem -Recurse -File -Filter 'jvm.dll' -LiteralPath $CacheRoot)
    $ExtractedJar = @(Get-ChildItem -Recurse -File -Filter 'pstop.jar' -LiteralPath $CacheRoot)
    $SystemJavaUnavailable = $null -eq (Get-Command java -ErrorAction SilentlyContinue)

    $Checks = [ordered]@{
        Version = $VersionOutput
        VersionExitCode = $VersionExitCode
        FirstLaunchMilliseconds = $FirstLaunchTimer.ElapsedMilliseconds
        WarmLaunchMilliseconds = $WarmLaunchTimer.ElapsedMilliseconds
        WarmVersionExitCode = $WarmVersionExitCode
        SnapshotExitCode = $SnapshotExitCode
        RepairExitCode = $RepairExitCode
        MissingConfigRecreated = [bool](
            $RepairExitCode -eq 0 -and
            $RepairOutput -eq 'Pstop 0.1.0' -and
            $ExtractedConfig.Count -eq 1
        )
        SnapshotLines = $Snapshot.Count
        AllPanels = [bool](
            ($Snapshot -match '1 cpu') -and
            ($Snapshot -match '2 mem') -and
            ($Snapshot -match '3 net') -and
            ($Snapshot -match '4 proc')
        )
        Utf8Clean = [bool](($Snapshot -match '╭') -and -not ($Snapshot -match '�'))
        IsolatedFolderItems = $AdjacentFiles.Count
        NoAdjacentConfig = -not (Test-Path -LiteralPath (Join-Path $IsolatedRoot 'Pstop.cfg'))
        NoAdjacentAppFolder = -not (Test-Path -LiteralPath (Join-Path $IsolatedRoot 'app'))
        NoAdjacentRuntimeFolder = -not (Test-Path -LiteralPath (Join-Path $IsolatedRoot 'runtime'))
        EmbeddedConfigExtracted = $ExtractedConfig.Count -eq 1
        EmbeddedJvmExtracted = $ExtractedJvm.Count -eq 1
        EmbeddedJarExtracted = $ExtractedJar.Count -eq 1
        SystemJavaUnavailable = $SystemJavaUnavailable
        PowerShell = $PSVersionTable.PSVersion.ToString()
    }

    $Failed = @(
        $Checks.SnapshotExitCode -ne 0
        $Checks.WarmVersionExitCode -ne 0
        $Checks.RepairExitCode -ne 0
        -not $Checks.MissingConfigRecreated
        -not $Checks.AllPanels
        -not $Checks.Utf8Clean
        $Checks.IsolatedFolderItems -ne 1
        -not $Checks.NoAdjacentConfig
        -not $Checks.NoAdjacentAppFolder
        -not $Checks.NoAdjacentRuntimeFolder
        -not $Checks.EmbeddedConfigExtracted
        -not $Checks.EmbeddedJvmExtracted
        -not $Checks.EmbeddedJarExtracted
        -not $Checks.SystemJavaUnavailable
    ) -contains $true

    [pscustomobject]$Checks | Format-List
    if ($Failed) {
        throw 'One or more standalone compatibility checks failed.'
    }
}
finally {
    if ($LocationPushed) {
        Pop-Location
    }
    $env:PSTOP_CACHE_DIR = $PreviousCache
    $env:JAVA_HOME = $PreviousJavaHome
    $env:PATH = $PreviousPath
}
