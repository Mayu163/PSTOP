package dev.pstop.ui

import dev.pstop.core.model.CpuSnapshot
import dev.pstop.core.model.DiskSnapshot
import dev.pstop.core.model.MemorySnapshot
import dev.pstop.core.model.NetworkSnapshot
import dev.pstop.core.model.ProcessSnapshot
import dev.pstop.core.model.SystemSnapshot
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DashboardRendererTest {
    @Test
    fun `renders all dashboard panels as plain text`() {
        val output = TerminalApplication.renderPlainSnapshot(snapshot(), 100, 30)

        assertContains(output, "1 cpu")
        assertContains(output, "2 mem")
        assertContains(output, "disks")
        assertContains(output, "3 net")
        assertContains(output, "4 proc")
        assertContains(output, "Status:")
        assertContains(output, "COMMAND")
        assertContains(output, "download")
        assertContains(output, "powershell")
        assertEquals(30, output.lines().dropLastWhile(String::isEmpty).size)
        assertTrue(output.lines().all { it.length <= 100 })
    }

    @Test
    fun `matches reference hierarchy at 130 by 41`() {
        val lines = TerminalApplication.renderPlainSnapshot(snapshot(), 130, 41)
            .lines()
            .dropLastWhile(String::isEmpty)

        assertEquals(41, lines.size)
        assertTrue(lines.all { it.length <= 130 })
        assertContains(lines[0], "1 cpu")
        assertContains(lines[0], "22:04:05")
        assertContains(lines[13], "2 mem")
        assertContains(lines[13], "disks")
        assertContains(lines[13], "powershell.exe")
        assertTrue(lines.any { "3 net" in it })
        assertTrue(lines.any { "4 proc" in it })
    }

    @Test
    fun `shows all 32 cpu threads by expanding rows on a large terminal`() {
        val snapshot = snapshotWithCoreCount(32)
        val lines = TerminalApplication.renderPlainSnapshot(snapshot, 240, 80)
            .lines()
            .dropLastWhile(String::isEmpty)

        assertEquals(80, lines.size)
        assertTrue(lines.all { it.length <= 240 })
        repeat(32) { core ->
            assertTrue(
                lines.any { Regex("""C$core\s""").containsMatchIn(it) },
                "Expected CPU thread C$core to be visible",
            )
        }
        val memoryHeaderRow = lines.indexOfFirst { "2 mem" in it }
        assertTrue(memoryHeaderRow >= 37, "CPU panel did not expand for 32 thread rows")
    }

    @Test
    fun `flows cpu threads into columns when vertical room is constrained`() {
        val snapshot = snapshotWithCoreCount(32)
        val lines = TerminalApplication.renderPlainSnapshot(snapshot, 180, 45)
            .lines()
            .dropLastWhile(String::isEmpty)

        repeat(32) { core ->
            assertTrue(
                lines.any { Regex("""C$core\s""").containsMatchIn(it) },
                "Expected CPU thread C$core to be visible",
            )
        }
        assertTrue(
            lines.any {
                Regex("""C0\s""").containsMatchIn(it) &&
                    Regex("""C16\s""").containsMatchIn(it)
            },
            "Expected the 32 thread meters to use multiple columns",
        )
    }

    @Test
    fun `sorts processes by requested field`() {
        val processes = snapshot().processes

        assertEquals(22, DashboardRenderer.sortProcesses(processes, ProcessSort.CPU, false).first().pid)
        assertEquals(22, DashboardRenderer.sortProcesses(processes, ProcessSort.NAME, false).first().pid)
        assertEquals(11, DashboardRenderer.sortProcesses(processes, ProcessSort.NAME, true).first().pid)
    }

    private fun snapshotWithCoreCount(coreCount: Int): SystemSnapshot {
        val base = snapshot()
        return base.copy(
            cpu = base.cpu.copy(
                logicalCores = coreCount,
                coreLoadsPercent = List(coreCount) { index -> (index * 7 % 101).toDouble() },
            ),
        )
    }

    private fun snapshot() = SystemSnapshot(
        capturedAt = Instant.parse("2026-01-02T03:04:05Z"),
        hostname = "WIN11",
        operatingSystem = "Microsoft Windows 11",
        cpu = CpuSnapshot(
            "Test CPU",
            16,
            42.5,
            4_000_000_000,
            3_661,
            coreLoadsPercent = listOf(12.0, 31.0, 7.0, 65.0, 18.0, 44.0, 5.0, 28.0),
            temperatureCelsius = 46.0,
        ),
        memory = MemorySnapshot(16L shl 30, 8L shl 30, 8L shl 30, 4L shl 30, 1L shl 30),
        disks = listOf(DiskSnapshot("C:", "C:\\", 1_000_000, 500_000)),
        network = NetworkSnapshot("Ethernet", "192.168.1.2", 10_000, 2_000, 1_000_000, 500_000),
        processes = listOf(
            ProcessSnapshot(
                11,
                "pstop.exe",
                "user",
                2.0,
                10_000,
                4,
                parentPid = 1,
                state = "RUNNING",
                uptimeSeconds = 120,
                command = "C:\\Pstop\\Pstop.exe",
            ),
            ProcessSnapshot(
                22,
                "powershell.exe",
                "user",
                20.0,
                50_000,
                8,
                parentPid = 1,
                state = "SLEEPING",
                uptimeSeconds = 3_661,
                command = "C:\\Program Files\\PowerShell\\7\\pwsh.exe",
                readBytes = 20_480,
                writtenBytes = 4_096,
            ),
        ),
        totalProcesses = 455,
        totalThreads = 4_321,
        diskReadBytesPerSecond = 102_400,
        diskWriteBytesPerSecond = 51_200,
    )
}
