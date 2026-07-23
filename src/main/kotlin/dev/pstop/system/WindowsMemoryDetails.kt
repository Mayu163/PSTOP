package dev.pstop.system

import com.sun.jna.platform.win32.Psapi

internal data class WindowsMemoryDetails(
    val pagedPoolBytes: Long,
    val nonPagedPoolBytes: Long,
    val committedBytes: Long,
    val cachedBytes: Long,
)

internal fun queryWindowsMemoryDetails(): WindowsMemoryDetails? = runCatching {
    val information = Psapi.PERFORMANCE_INFORMATION()
    if (!Psapi.INSTANCE.GetPerformanceInfo(information, information.size())) {
        return@runCatching null
    }

    val pageSize = information.PageSize.toLong()
    WindowsMemoryDetails(
        pagedPoolBytes = pagesToBytes(information.KernelPaged.toLong(), pageSize),
        nonPagedPoolBytes = pagesToBytes(information.KernelNonpaged.toLong(), pageSize),
        committedBytes = pagesToBytes(information.CommitTotal.toLong(), pageSize),
        cachedBytes = pagesToBytes(information.SystemCache.toLong(), pageSize),
    )
}.getOrNull()

private fun pagesToBytes(pages: Long, pageSize: Long): Long {
    if (pages <= 0L || pageSize <= 0L) return 0L
    return if (pages > Long.MAX_VALUE / pageSize) Long.MAX_VALUE else pages * pageSize
}
