package dev.pstop.system

import dev.pstop.core.model.CpuSnapshot
import dev.pstop.core.model.DiskSnapshot
import dev.pstop.core.model.MemorySnapshot
import dev.pstop.core.model.NetworkSnapshot
import dev.pstop.core.model.ProcessSnapshot
import dev.pstop.core.model.SystemSnapshot
import oshi.SystemInfo
import oshi.hardware.NetworkIF
import oshi.software.os.OSProcess
import java.io.File
import java.net.InetAddress
import java.time.Instant

class OshiMetricsCollector(private val processLimit: Int = 200) : MetricsCollector {
    private val systemInfo = SystemInfo()
    private val hardware = systemInfo.hardware
    private val operatingSystem = systemInfo.operatingSystem
    private var previousCpuTicks = hardware.processor.systemCpuLoadTicks
    private var previousCoreTicks = hardware.processor.processorCpuLoadTicks
    private var previousSampleNanos = System.nanoTime()
    private var previousNetwork = emptyMap<String, NetworkCounters>()
    private var previousProcesses = emptyMap<Int, OSProcess>()
    private var previousDiskReadBytes = 0L
    private var previousDiskWriteBytes = 0L

    override fun sample(): SystemSnapshot {
        val nowNanos = System.nanoTime()
        val elapsedSeconds = ((nowNanos - previousSampleNanos) / 1_000_000_000.0).coerceAtLeast(0.001)
        previousSampleNanos = nowNanos

        val processor = hardware.processor
        val currentTicks = processor.systemCpuLoadTicks
        val cpuLoad = processor.getSystemCpuLoadBetweenTicks(previousCpuTicks) * 100.0
        previousCpuTicks = currentTicks
        val currentCoreTicks = processor.processorCpuLoadTicks
        val coreLoads = processor.getProcessorCpuLoadBetweenTicks(previousCoreTicks)
            .map { it * 100.0 }
        previousCoreTicks = currentCoreTicks

        val memory = hardware.memory
        val virtualMemory = memory.virtualMemory
        val totalMemory = memory.total
        val availableMemory = memory.available

        val networkInterfaces = hardware.networkIFs
            .onEach(NetworkIF::updateAttributes)
            .filter { it.bytesRecv > 0L || it.bytesSent > 0L }

        val selectedNetwork = networkInterfaces.maxByOrNull { it.bytesRecv + it.bytesSent }
        val network = selectedNetwork?.let { current ->
            val previous = previousNetwork[current.name]
            val downloadRate = previous?.let {
                ((current.bytesRecv - it.received).coerceAtLeast(0) / elapsedSeconds).toLong()
            } ?: 0L
            val uploadRate = previous?.let {
                ((current.bytesSent - it.sent).coerceAtLeast(0) / elapsedSeconds).toLong()
            } ?: 0L
            NetworkSnapshot(
                name = current.displayName.ifBlank { current.name },
                address = current.iPv4addr.firstOrNull().orEmpty(),
                downloadBytesPerSecond = downloadRate,
                uploadBytesPerSecond = uploadRate,
                receivedBytes = current.bytesRecv,
                sentBytes = current.bytesSent,
            )
        }
        previousNetwork = networkInterfaces.associate {
            it.name to NetworkCounters(it.bytesRecv, it.bytesSent)
        }

        val diskStores = hardware.diskStores.onEach { it.updateAttributes() }
        val currentDiskReadBytes = diskStores.sumOf { it.readBytes }
        val currentDiskWriteBytes = diskStores.sumOf { it.writeBytes }
        val diskReadRate = if (previousDiskReadBytes > 0L) {
            ((currentDiskReadBytes - previousDiskReadBytes).coerceAtLeast(0L) / elapsedSeconds).toLong()
        } else {
            0L
        }
        val diskWriteRate = if (previousDiskWriteBytes > 0L) {
            ((currentDiskWriteBytes - previousDiskWriteBytes).coerceAtLeast(0L) / elapsedSeconds).toLong()
        } else {
            0L
        }
        previousDiskReadBytes = currentDiskReadBytes
        previousDiskWriteBytes = currentDiskWriteBytes

        val osProcesses = operatingSystem.processes.filter { it.processID > 0 }
        val processSnapshots = osProcesses.map { process ->
            val previous = previousProcesses[process.processID]
            ProcessSnapshot(
                pid = process.processID,
                name = process.name.ifBlank { "<unknown>" },
                user = "",
                cpuPercent = process.getProcessCpuLoadBetweenTicks(previous) * 100.0 / processor.logicalProcessorCount,
                memoryBytes = process.residentSetSize,
                threadCount = process.threadCount,
                parentPid = process.parentProcessID,
                state = process.state.name,
                uptimeSeconds = process.upTime / 1_000L,
                command = process.path.ifBlank { process.name },
                readBytes = process.bytesRead,
                writtenBytes = process.bytesWritten,
            )
        }.sortedByDescending(ProcessSnapshot::cpuPercent).take(processLimit)
        previousProcesses = osProcesses.associateBy(OSProcess::getProcessID)

        return SystemSnapshot(
            capturedAt = Instant.now(),
            hostname = runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("Windows"),
            operatingSystem = operatingSystem.toString(),
            cpu = CpuSnapshot(
                model = processor.processorIdentifier.name,
                logicalCores = processor.logicalProcessorCount,
                loadPercent = cpuLoad,
                frequencyHz = processor.maxFreq.coerceAtLeast(0L),
                uptimeSeconds = operatingSystem.systemUptime,
                coreLoadsPercent = coreLoads,
                temperatureCelsius = hardware.sensors.cpuTemperature.takeIf { it > 0.0 },
            ),
            memory = MemorySnapshot(
                totalBytes = totalMemory,
                usedBytes = (totalMemory - availableMemory).coerceAtLeast(0L),
                availableBytes = availableMemory,
                swapTotalBytes = virtualMemory.swapTotal,
                swapUsedBytes = virtualMemory.swapUsed,
            ),
            disks = File.listRoots().orEmpty().map { root ->
                DiskSnapshot(
                    name = root.path.removeSuffix("\\").ifBlank { root.path },
                    mount = root.path,
                    totalBytes = root.totalSpace,
                    usedBytes = (root.totalSpace - root.usableSpace).coerceAtLeast(0L),
                )
            }.filter { it.totalBytes > 0L },
            network = network,
            processes = processSnapshots,
            totalProcesses = osProcesses.size,
            totalThreads = osProcesses.sumOf(OSProcess::getThreadCount),
            diskReadBytesPerSecond = diskReadRate,
            diskWriteBytesPerSecond = diskWriteRate,
        )
    }

    private data class NetworkCounters(val received: Long, val sent: Long)
}
