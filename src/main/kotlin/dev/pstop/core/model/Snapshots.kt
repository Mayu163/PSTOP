package dev.pstop.core.model

import java.time.Instant

data class CpuSnapshot(
    val model: String,
    val logicalCores: Int,
    val loadPercent: Double,
    val frequencyHz: Long,
    val uptimeSeconds: Long,
    val coreLoadsPercent: List<Double> = emptyList(),
    val temperatureCelsius: Double? = null,
)

data class MemorySnapshot(
    val totalBytes: Long,
    val usedBytes: Long,
    val availableBytes: Long,
    val swapTotalBytes: Long,
    val swapUsedBytes: Long,
    val pagedPoolBytes: Long = 0L,
    val nonPagedPoolBytes: Long = 0L,
    val committedBytes: Long = 0L,
    val cachedBytes: Long = 0L,
)

data class DiskSnapshot(
    val name: String,
    val mount: String,
    val totalBytes: Long,
    val usedBytes: Long,
    val availableBytes: Long = (totalBytes - usedBytes).coerceAtLeast(0L),
)

data class NetworkSnapshot(
    val name: String,
    val address: String,
    val downloadBytesPerSecond: Long,
    val uploadBytesPerSecond: Long,
    val receivedBytes: Long,
    val sentBytes: Long,
)

data class ProcessSnapshot(
    val pid: Int,
    val name: String,
    val user: String,
    val cpuPercent: Double,
    val memoryBytes: Long,
    val threadCount: Int,
    val parentPid: Int = 0,
    val state: String = "UNKNOWN",
    val uptimeSeconds: Long = 0,
    val command: String = "",
    val readBytes: Long = 0,
    val writtenBytes: Long = 0,
)

data class SystemSnapshot(
    val capturedAt: Instant,
    val hostname: String,
    val operatingSystem: String,
    val cpu: CpuSnapshot,
    val memory: MemorySnapshot,
    val disks: List<DiskSnapshot>,
    val network: NetworkSnapshot?,
    val processes: List<ProcessSnapshot>,
    val totalProcesses: Int = processes.size,
    val totalThreads: Int = processes.sumOf(ProcessSnapshot::threadCount),
    val diskReadBytesPerSecond: Long = 0,
    val diskWriteBytesPerSecond: Long = 0,
)
