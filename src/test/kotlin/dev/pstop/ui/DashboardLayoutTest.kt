package dev.pstop.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DashboardLayoutTest {
    @Test
    fun `keeps the reference layout when all panels are visible`() {
        val layout = calculate()

        assertEquals(Rect(0, 0, 130, 14), layout.cpu)
        assertEquals(Rect(0, 13, 29, 17), layout.memory)
        assertEquals(Rect(28, 13, 32, 17), layout.disks)
        assertEquals(Rect(0, 29, 60, 12), layout.network)
        assertEquals(Rect(59, 13, 71, 9), layout.processDetail)
        assertEquals(Rect(59, 21, 71, 20), layout.processes)
    }

    @Test
    fun `remaining left panel expands vertically`() {
        val withoutResources = calculate(visiblePanels = setOf(1, 3, 4))
        assertNull(withoutResources.memory)
        assertNull(withoutResources.disks)
        assertEquals(Rect(0, 13, 60, 28), withoutResources.network)

        val withoutNetwork = calculate(visiblePanels = setOf(1, 2, 4))
        assertEquals(Rect(0, 13, 29, 28), withoutNetwork.memory)
        assertEquals(Rect(28, 13, 32, 28), withoutNetwork.disks)
        assertNull(withoutNetwork.network)
    }

    @Test
    fun `left panels expand across process space`() {
        val layout = calculate(visiblePanels = setOf(1, 2, 3))

        assertEquals(Rect(0, 13, 63, 17), layout.memory)
        assertEquals(Rect(62, 13, 68, 17), layout.disks)
        assertEquals(Rect(0, 29, 130, 12), layout.network)
        assertNull(layout.processDetail)
        assertNull(layout.processes)
    }

    @Test
    fun `process area expands across left space`() {
        val layout = calculate(visiblePanels = setOf(1, 4))

        assertEquals(Rect(0, 13, 130, 9), layout.processDetail)
        assertEquals(Rect(0, 21, 130, 20), layout.processes)
    }

    @Test
    fun `lower panels fill cpu space only when cpu is hidden`() {
        val withCpu = calculate(visiblePanels = setOf(1, 3))
        assertEquals(13, withCpu.network?.y)
        assertEquals(28, withCpu.network?.height)

        val withoutCpu = calculate(visiblePanels = setOf(3))
        assertNull(withoutCpu.cpu)
        assertEquals(Rect(0, 0, 130, 41), withoutCpu.network)
    }

    @Test
    fun `process list fills detail space when details are hidden`() {
        val layout = calculate(showDetails = false)

        assertNull(layout.processDetail)
        assertEquals(Rect(59, 13, 71, 28), layout.processes)
    }

    @Test
    fun `all panel combinations fill their assigned space without leaving stale regions`() {
        for ((width, height) in listOf(72 to 22, 130 to 41, 241 to 80)) {
            for (mask in 0 until 16) {
                val visiblePanels = (1..4).filterTo(mutableSetOf()) { panel ->
                    mask and (1 shl (panel - 1)) != 0
                }
                for (showDetails in listOf(false, true)) {
                    val layout = DashboardLayoutEngine.calculate(
                        width,
                        height,
                        ViewState(
                            visiblePanels = visiblePanels,
                            showDetails = showDetails,
                        ),
                    )
                    val rectangles = listOfNotNull(
                        layout.cpu,
                        layout.memory,
                        layout.disks,
                        layout.network,
                        layout.processDetail,
                        layout.processes,
                    )
                    rectangles.forEach { rect ->
                        assertTrue(rect.width >= 2 && rect.height >= 2)
                        assertTrue(rect.x >= 0 && rect.y >= 0)
                        assertTrue(rect.right < width && rect.bottom < height)
                    }

                    assertEquals(1 in visiblePanels, layout.cpu != null)
                    assertEquals(2 in visiblePanels, layout.memory != null)
                    assertEquals(2 in visiblePanels, layout.disks != null)
                    assertEquals(3 in visiblePanels, layout.network != null)
                    assertEquals(4 in visiblePanels, layout.processes != null)
                    assertEquals(
                        4 in visiblePanels && showDetails,
                        layout.processDetail != null,
                    )

                    val lowerTop = layout.cpu?.bottom ?: 0
                    if (2 in visiblePanels && 3 in visiblePanels) {
                        assertEquals(lowerTop, layout.memory?.y)
                        assertEquals(layout.memory?.bottom, layout.network?.y)
                        assertEquals(height - 1, layout.network?.bottom)
                    } else {
                        layout.memory?.let {
                            assertEquals(lowerTop, it.y)
                            assertEquals(height - 1, it.bottom)
                        }
                        layout.network?.let {
                            assertEquals(lowerTop, it.y)
                            assertEquals(height - 1, it.bottom)
                        }
                    }

                    layout.processes?.let { processes ->
                        assertEquals(height - 1, processes.bottom)
                        if (showDetails) {
                            assertEquals(lowerTop, layout.processDetail?.y)
                            assertEquals(layout.processDetail?.bottom, processes.y)
                        } else {
                            assertEquals(lowerTop, processes.y)
                        }
                    }

                    val leftVisible = 2 in visiblePanels || 3 in visiblePanels
                    val leftRight = layout.network?.right
                        ?: layout.disks?.right
                    if (leftVisible && 4 in visiblePanels) {
                        assertEquals(layout.processes?.x, leftRight)
                    } else if (leftVisible) {
                        assertEquals(width - 1, leftRight)
                    } else if (4 in visiblePanels) {
                        assertEquals(0, layout.processes?.x)
                        assertEquals(width, layout.processes?.width)
                    }
                }
            }
        }
    }

    private fun calculate(
        visiblePanels: Set<Int> = setOf(1, 2, 3, 4),
        showDetails: Boolean = true,
    ): DashboardLayout = DashboardLayoutEngine.calculate(
        width = 130,
        height = 41,
        state = ViewState(
            visiblePanels = visiblePanels,
            showDetails = showDetails,
        ),
    )
}
