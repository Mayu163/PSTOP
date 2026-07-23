package dev.pstop.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class ViewStateTest {
    @Test
    fun `each numbered panel key hides and restores only its own panel`() {
        val initial = ViewState()

        for (panel in 1..4) {
            val hidden = initial.togglePanel(panel)
            assertEquals(setOf(1, 2, 3, 4) - panel, hidden.visiblePanels)
            assertEquals(initial, hidden.togglePanel(panel))
        }
    }
}
