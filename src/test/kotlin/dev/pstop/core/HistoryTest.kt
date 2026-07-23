package dev.pstop.core

import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryTest {
    @Test
    fun `keeps only the newest bounded values`() {
        val history = History(3)
        history.add(10.0)
        history.add(20.0)
        history.add(30.0)
        history.add(40.0)

        assertEquals(listOf(20.0, 30.0, 40.0), history.values())
    }

    @Test
    fun `clamps percentages`() {
        val history = History(2)
        history.add(-1.0)
        history.add(120.0)

        assertEquals(listOf(0.0, 100.0), history.values())
    }
}
