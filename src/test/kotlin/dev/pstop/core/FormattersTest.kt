package dev.pstop.core

import kotlin.test.Test
import kotlin.test.assertEquals

class FormattersTest {
    @Test
    fun `formats binary byte units`() {
        assertEquals("0 B", Formatters.bytes(0))
        assertEquals("1.00 KiB", Formatters.bytes(1024))
        assertEquals("1.50 MiB", Formatters.bytes(1_572_864))
    }

    @Test
    fun `truncates with an ellipsis`() {
        assertEquals("Pstop", Formatters.truncate("Pstop", 8))
        assertEquals("Wind…", Formatters.truncate("Windows", 5))
        assertEquals("", Formatters.truncate("Windows", 0))
    }

    @Test
    fun `formats uptime`() {
        assertEquals("1h 1m", Formatters.duration(3_661))
        assertEquals("2d 3h 4m", Formatters.duration(183_840))
    }

    @Test
    fun `formats cpu frequency`() {
        assertEquals("4.20 GHz", Formatters.frequency(4_200_000_000))
        assertEquals("800 MHz", Formatters.frequency(800_000_000))
    }
}
