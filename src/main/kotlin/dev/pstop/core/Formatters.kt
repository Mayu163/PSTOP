package dev.pstop.core

import java.time.Duration
import kotlin.math.abs

object Formatters {
    private val byteUnits = arrayOf("B", "KiB", "MiB", "GiB", "TiB", "PiB")
    private val rateUnits = arrayOf("B/s", "KiB/s", "MiB/s", "GiB/s", "TiB/s")

    fun bytes(value: Long): String = scale(value, byteUnits)

    fun rate(value: Long): String = scale(value, rateUnits)

    fun frequency(value: Long): String {
        val gigahertz = value.coerceAtLeast(0L) / 1_000_000_000.0
        return if (gigahertz >= 1.0) "%.2f GHz".format(gigahertz) else "%.0f MHz".format(gigahertz * 1_000.0)
    }

    fun percent(value: Double): String = "%5.1f%%".format(value.coerceIn(0.0, 999.9))

    fun duration(seconds: Long): String {
        val duration = Duration.ofSeconds(seconds.coerceAtLeast(0))
        val days = duration.toDays()
        val hours = duration.toHoursPart()
        val minutes = duration.toMinutesPart()
        return if (days > 0) "${days}d ${hours}h ${minutes}m" else "${hours}h ${minutes}m"
    }

    fun truncate(value: String, width: Int): String {
        if (width <= 0) return ""
        if (value.length <= width) return value
        if (width == 1) return "…"
        return value.take(width - 1) + "…"
    }

    private fun scale(value: Long, units: Array<String>): String {
        var scaled = abs(value.toDouble())
        var unit = 0
        while (scaled >= 1024.0 && unit < units.lastIndex) {
            scaled /= 1024.0
            unit++
        }
        if (value < 0) scaled = -scaled
        val number = when {
            unit == 0 -> "%.0f".format(scaled)
            scaled >= 100 -> "%.0f".format(scaled)
            scaled >= 10 -> "%.1f".format(scaled)
            else -> "%.2f".format(scaled)
        }
        return "$number ${units[unit]}"
    }
}
