package dev.pstop.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AppOptionsTest {
    @Test
    fun `parses supported options`() {
        val options = AppOptions.parse(
            arrayOf("--once", "--no-color", "--refresh", "500", "--process-limit", "50", "--width", "90"),
        )

        assertTrue(options.once)
        assertTrue(options.noColor)
        assertEquals(500, options.refreshMillis)
        assertEquals(50, options.processLimit)
        assertEquals(90, options.width)
    }

    @Test
    fun `rejects unsafe refresh rates`() {
        assertFailsWith<IllegalArgumentException> {
            AppOptions.parse(arrayOf("--refresh", "20"))
        }
    }
}
