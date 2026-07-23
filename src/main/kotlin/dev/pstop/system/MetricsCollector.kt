package dev.pstop.system

import dev.pstop.core.model.SystemSnapshot

fun interface MetricsCollector {
    fun sample(): SystemSnapshot
}
