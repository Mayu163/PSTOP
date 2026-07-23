# PSTOP — a PowerShell top

PSTOP (Pstop) is a fast, keyboard-driven resource monitor designed for Microsoft
Windows 11 Terminal. It follows the familiar btop-style dashboard model while
using a Windows-focused Kotlin implementation.

Pstop currently displays:

- a full-width CPU history that preserves the reference dashboard ratio, with
  up to eight core meters per column and additional columns for larger CPUs;
- detailed Windows memory totals for used, paged pool, non-paged pool,
  committed, and cached memory, plus compact mounted-drive usage and aggregate
  disk I/O;
- mirrored download/upload histories, current rates, peaks, totals, interface,
  and address;
- a selected-process detail card with state, elapsed time, parent PID, memory,
  threads, cumulative I/O, and executable path;
- a dense sortable and scrollable process table with command paths;
- a responsive true-color terminal UI with resize handling and a plain-text
  snapshot mode for scripts and diagnostics.

Pstop is intentionally read-only. It does not terminate processes or mutate
Windows services.

## Requirements

- Windows 11
- Microsoft Windows Terminal
- PowerShell 7 or newer
- Internet access for the first build only

No system-wide Java or Gradle installation is required.

## Build and test

From PowerShell 7 in the repository root:

```powershell
./scripts/Build.ps1
```

The first build downloads a portable Eclipse Temurin JDK 21 and Gradle into
`.tooling/`, then resolves dependencies into `.gradle/`. The finished
applications are created at:

```text
dist/Pstop.exe
dist/pstop/pstop.ps1
dist/exe/Pstop/Pstop.exe
```

`dist/Pstop.exe` is the primary portable release. It is a true one-file
distribution that embeds Pstop's application files, configuration, and
minimized Java runtime. It does not depend on system-wide Java and can be copied
to another Windows 11 machine by itself.

On first launch, the executable verifies and extracts its embedded application
image into a versioned cache under `%LOCALAPPDATA%\Pstop\runtime`. Later
launches reuse that cache. A changed Pstop payload automatically gets a new
cache directory, so different releases cannot accidentally share runtime
files. If a required cached CFG, JAR, JVM, or executable is missing, Pstop
repairs the cache from its embedded payload. `PSTOP_CACHE_DIR` can override the
cache location for controlled deployments and automated tests. The payload
identity is calculated during the build and embedded as a small resource, so
startup does not rescan the complete application payload.

The other two outputs are inspectable folder distributions. In particular,
`dist/exe/Pstop/Pstop.exe` is the launcher generated directly by `jpackage`;
it must remain beside its generated `app` and `runtime` directories because its
`app\Pstop.cfg` points to the packaged JAR and runtime.

Run only the test suite with:

```powershell
./scripts/Test.ps1
```

Run the isolated one-file compatibility test with:

```powershell
./scripts/Test-Standalone.ps1
```

That test copies only `Pstop.exe` into an empty directory, hides system Java,
uses a fresh cache, validates the version command and a complete dashboard
snapshot, deletes the cached CFG, and verifies automatic repair.

## Run

```powershell
./scripts/Run.ps1
```

Run the compiled Windows executable directly:

```powershell
./dist/Pstop.exe
```

The inspectable packaged applications can also be run from their complete
folders:

```powershell
./dist/pstop/pstop.ps1
./dist/exe/Pstop/Pstop.exe
```

Useful non-interactive checks:

```powershell
./scripts/Run.ps1 --version
./scripts/Run.ps1 --help
./scripts/Run.ps1 --once --no-color --width 120 --height 36
```

Interactive keys:

| Key | Action |
| --- | --- |
| `q`, `Esc`, `Ctrl+C` | Quit cleanly |
| `Up`, `Down` | Select a process |
| `Left`, `Right` | Ignored to prevent accidental actions |
| `Page Up`, `Page Down` | Move one page |
| `Enter` | Toggle selected-process details |
| `s` | Cycle CPU, memory, PID, and name sorting |
| `r` | Reverse process sorting |
| `p` | Pause/resume metric sampling |
| `1` | Toggle the CPU panel |
| `2` | Toggle the memory and disks panels |
| `3` | Toggle the network panel |
| `4` | Toggle the process area |
| `h`, `?` | Toggle help |

When a panel is hidden, the remaining non-CPU panels automatically expand to
use the available terminal space. The CPU panel keeps its reference proportion.

## Repository layout

```text
Pstop/
├── launcher/                One-file Windows launcher source and manifest
├── scripts/                 PowerShell 7 bootstrap/build/test/run entry points
├── src/
│   ├── main/kotlin/dev/pstop
│   │   ├── cli/             Command-line parsing
│   │   ├── core/            Models, histories, formatting
│   │   ├── system/          OSHI-backed Windows metric collection
│   │   └── ui/              Canvas, dashboard, terminal event loop
│   └── test/kotlin/dev/pstop Unit and rendering tests
├── build.gradle.kts         Reproducible Gradle build
├── settings.gradle.kts
├── LICENSE
└── NOTICE.md
```

Generated folders (`.tooling`, `.gradle`, `build`, and `dist`) are ignored by
source control but remain inside the repository workspace.

## Inspiration and licensing

Pstop is independent code inspired by
[aristocratos/btop](https://github.com/aristocratos/btop). See
[NOTICE.md](NOTICE.md). Pstop itself is licensed under the MIT License.
