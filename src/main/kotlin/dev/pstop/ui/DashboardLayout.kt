package dev.pstop.ui

import kotlin.math.roundToInt

internal data class DashboardLayout(
    val cpu: Rect?,
    val memory: Rect?,
    val disks: Rect?,
    val network: Rect?,
    val processDetail: Rect?,
    val processes: Rect?,
)

internal object DashboardLayoutEngine {
    fun calculate(
        width: Int,
        height: Int,
        state: ViewState,
    ): DashboardLayout {
        val cpuVisible = PANEL_CPU in state.visiblePanels
        val resourcesVisible = PANEL_RESOURCES in state.visiblePanels
        val networkVisible = PANEL_NETWORK in state.visiblePanels
        val processesVisible = PANEL_PROCESSES in state.visiblePanels

        val cpuHeight = if (cpuVisible) {
            (height * CPU_HEIGHT_RATIO).roundToInt()
                .coerceIn(MINIMUM_CPU_HEIGHT, height - MINIMUM_LOWER_HEIGHT)
        } else {
            0
        }
        val lowerTop = if (cpuVisible) cpuHeight - 1 else 0
        val lowerSpan = height - 1 - lowerTop
        val cpu = if (cpuVisible) Rect(0, 0, width, cpuHeight) else null

        val leftVisible = resourcesVisible || networkVisible
        val splitX = if (leftVisible && processesVisible) {
            (width * LEFT_WIDTH_RATIO).roundToInt()
                .coerceIn(MINIMUM_LEFT_WIDTH, width - MINIMUM_RIGHT_WIDTH)
        } else {
            null
        }
        val leftRight = splitX ?: width - 1
        val processLeft = splitX ?: 0

        val leftTopSpan = if (resourcesVisible && networkVisible) {
            (lowerSpan * LEFT_TOP_HEIGHT_RATIO).roundToInt()
                .coerceIn(
                    MINIMUM_LEFT_TOP_HEIGHT,
                    lowerSpan - MINIMUM_NETWORK_HEIGHT + 1,
                )
        } else {
            lowerSpan
        }
        val resourcesBottom = lowerTop + leftTopSpan
        val resourcesHeight = leftTopSpan + 1

        val memoryWidth = if (resourcesVisible) {
            (leftRight * MEMORY_WIDTH_RATIO).roundToInt()
                .coerceIn(MINIMUM_MEMORY_WIDTH, leftRight - MINIMUM_DISK_WIDTH)
        } else {
            0
        }
        val memory = if (resourcesVisible) {
            Rect(0, lowerTop, memoryWidth, resourcesHeight)
        } else {
            null
        }
        val disks = if (resourcesVisible) {
            Rect(
                memoryWidth - 1,
                lowerTop,
                leftRight - memoryWidth + 2,
                resourcesHeight,
            )
        } else {
            null
        }
        val network = if (networkVisible) {
            val networkTop = if (resourcesVisible) resourcesBottom else lowerTop
            Rect(
                0,
                networkTop,
                leftRight + 1,
                height - networkTop,
            )
        } else {
            null
        }

        val detailSpan = if (processesVisible && state.showDetails) {
            (lowerSpan * DETAIL_HEIGHT_RATIO).roundToInt()
                .coerceIn(
                    MINIMUM_DETAIL_HEIGHT,
                    lowerSpan - MINIMUM_PROCESS_HEIGHT + 1,
                )
        } else {
            0
        }
        val processWidth = width - processLeft
        val processDetail = if (processesVisible && state.showDetails) {
            Rect(processLeft, lowerTop, processWidth, detailSpan + 1)
        } else {
            null
        }
        val processTop = lowerTop + detailSpan
        val processes = if (processesVisible) {
            Rect(processLeft, processTop, processWidth, height - processTop)
        } else {
            null
        }

        return DashboardLayout(
            cpu = cpu,
            memory = memory,
            disks = disks,
            network = network,
            processDetail = processDetail,
            processes = processes,
        )
    }

    private const val PANEL_CPU = 1
    private const val PANEL_RESOURCES = 2
    private const val PANEL_NETWORK = 3
    private const val PANEL_PROCESSES = 4

    private const val MINIMUM_CPU_HEIGHT = 8
    private const val MINIMUM_LOWER_HEIGHT = 14
    private const val MINIMUM_LEFT_WIDTH = 33
    private const val MINIMUM_RIGHT_WIDTH = 38
    private const val MINIMUM_MEMORY_WIDTH = 16
    private const val MINIMUM_DISK_WIDTH = 17
    private const val MINIMUM_LEFT_TOP_HEIGHT = 8
    private const val MINIMUM_NETWORK_HEIGHT = 6
    private const val MINIMUM_DETAIL_HEIGHT = 5
    private const val MINIMUM_PROCESS_HEIGHT = 8

    private const val CPU_HEIGHT_RATIO = 0.33
    private const val LEFT_WIDTH_RATIO = 0.45
    private const val MEMORY_WIDTH_RATIO = 0.49
    private const val LEFT_TOP_HEIGHT_RATIO = 0.58
    private const val DETAIL_HEIGHT_RATIO = 0.31
}
