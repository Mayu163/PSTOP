package dev.pstop.ui

import dev.pstop.core.Formatters
import dev.pstop.core.History
import dev.pstop.core.model.DiskSnapshot
import dev.pstop.core.model.ProcessSnapshot
import dev.pstop.core.model.SystemSnapshot
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class ProcessSort(val label: String) {
    CPU("cpu lazy"),
    MEMORY("memory"),
    PID("pid"),
    NAME("program");

    fun next(): ProcessSort = entries[(ordinal + 1) % entries.size]
}

data class ViewState(
    val selectedProcess: Int = 0,
    val processOffset: Int = 0,
    val processSort: ProcessSort = ProcessSort.CPU,
    val reverseSort: Boolean = false,
    val paused: Boolean = false,
    val showHelp: Boolean = false,
    val showDetails: Boolean = true,
    val visiblePanels: Set<Int> = setOf(1, 2, 3, 4),
)

class DashboardRenderer(private val theme: Theme) {
    private val cpuHistory = History(HISTORY_CAPACITY)
    private val networkDownloadHistory = RateHistory(HISTORY_CAPACITY)
    private val networkUploadHistory = RateHistory(HISTORY_CAPACITY)
    private val clock = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun render(snapshot: SystemSnapshot, width: Int, height: Int, state: ViewState): Canvas {
        val canvas = Canvas(width.coerceAtLeast(1), height.coerceAtLeast(1))
        if (width < MINIMUM_WIDTH || height < MINIMUM_HEIGHT) {
            drawSizeWarning(canvas, width, height)
            return canvas
        }

        cpuHistory.add(snapshot.cpu.loadPercent)
        networkDownloadHistory.add(snapshot.network?.downloadBytesPerSecond ?: 0L)
        networkUploadHistory.add(snapshot.network?.uploadBytesPerSecond ?: 0L)

        val cpuHeight = (height * CPU_HEIGHT_RATIO).roundToInt()
            .coerceIn(MINIMUM_CPU_HEIGHT, height - MINIMUM_LOWER_HEIGHT)
        val lowerHeight = height - cpuHeight
        val leftWidth = (width * LEFT_WIDTH_RATIO).roundToInt()
            .coerceIn(MINIMUM_LEFT_WIDTH, width - MINIMUM_RIGHT_WIDTH)
        val rightWidth = width - leftWidth
        val leftTopHeight = (lowerHeight * LEFT_TOP_HEIGHT_RATIO).roundToInt()
            .coerceIn(MINIMUM_LEFT_TOP_HEIGHT, lowerHeight - MINIMUM_NETWORK_HEIGHT + 1)
        val memoryWidth = (leftWidth * MEMORY_WIDTH_RATIO).roundToInt()
            .coerceIn(MINIMUM_MEMORY_WIDTH, leftWidth - MINIMUM_DISK_WIDTH)
        val detailHeight = if (state.showDetails) {
            (lowerHeight * DETAIL_HEIGHT_RATIO).roundToInt()
                .coerceIn(MINIMUM_DETAIL_HEIGHT, lowerHeight - MINIMUM_PROCESS_HEIGHT + 1)
        } else {
            1
        }

        val cpuRect = Rect(0, 0, width, cpuHeight)
        val memoryRect = Rect(0, cpuHeight - 1, memoryWidth, leftTopHeight + 1)
        val diskRect = Rect(memoryWidth - 1, cpuHeight - 1, leftWidth - memoryWidth + 2, leftTopHeight + 1)
        val networkRect = Rect(
            0,
            cpuHeight + leftTopHeight - 1,
            leftWidth + 1,
            lowerHeight - leftTopHeight + 1,
        )
        val detailRect = Rect(leftWidth, cpuHeight - 1, rightWidth, detailHeight + 1)
        val processRect = Rect(
            leftWidth,
            cpuHeight + detailHeight - 1,
            rightWidth,
            lowerHeight - detailHeight + 1,
        )

        if (1 in state.visiblePanels) drawCpu(canvas, cpuRect, snapshot, state)
        if (2 in state.visiblePanels) {
            drawMemory(canvas, memoryRect, snapshot)
            drawDisks(canvas, diskRect, snapshot)
        }
        if (3 in state.visiblePanels) drawNetwork(canvas, networkRect, snapshot)
        if (4 in state.visiblePanels) {
            if (state.showDetails) drawProcessDetail(canvas, detailRect, snapshot, state)
            drawProcesses(canvas, processRect, snapshot, state)
        }

        if (state.showHelp) drawHelp(canvas)
        return canvas
    }

    private fun drawSizeWarning(canvas: Canvas, width: Int, height: Int) {
            canvas.text(1, 1, "Pstop needs at least $MINIMUM_WIDTH columns × $MINIMUM_HEIGHT rows.", theme.warning)
        canvas.text(1, 3, "Current terminal: $width × $height", theme.primary)
        canvas.text(1, 5, "Enlarge the Windows Terminal pane or reduce its font size.", theme.dim)
    }

    private fun drawCpu(canvas: Canvas, rect: Rect, snapshot: SystemSnapshot, state: ViewState) {
        val time = clock.format(snapshot.capturedAt.atZone(ZoneId.systemDefault()))
        val refreshState = if (state.paused) "paused" else "live"
        canvas.box(rect, "1 cpu   menu", theme, "$refreshState +")
        drawCenteredBorderLabel(canvas, rect, time)

        val coreRows = (rect.height - CPU_CORE_VERTICAL_CHROME)
            .coerceIn(1, MAXIMUM_CORE_ROWS_PER_COLUMN)
        val coreLoads = snapshot.cpu.coreLoadsPercent
        val requestedCoreColumns = if (coreLoads.isEmpty()) {
            1
        } else {
            ceil(coreLoads.size / coreRows.toDouble()).toInt().coerceAtLeast(1)
        }
        val requestedSidebarWidth =
            requestedCoreColumns * MINIMUM_CORE_COLUMN_WIDTH +
                (requestedCoreColumns - 1) * CORE_COLUMN_GAP +
                CPU_SIDEBAR_HORIZONTAL_CHROME
        val maximumSidebarWidth =
            (rect.width - MINIMUM_CPU_GRAPH_WIDTH - CPU_GRAPH_FRAME_ALLOWANCE)
                .coerceAtLeast(MINIMUM_CPU_SIDEBAR_WIDTH)
        val proportionalSidebarWidth = (rect.width * CPU_SIDEBAR_RATIO).roundToInt()
            .coerceAtLeast(MINIMUM_CPU_SIDEBAR_WIDTH)
        val sidebarWidth = maxOf(proportionalSidebarWidth, requestedSidebarWidth)
            .coerceAtMost(maximumSidebarWidth)
        val sidebarX = rect.right - sidebarWidth
        canvas.box(
            Rect(sidebarX, rect.y + 1, sidebarWidth, rect.height - 2),
            Formatters.truncate(snapshot.cpu.model, sidebarWidth - 4),
            theme,
        )

        val graphRect = Rect(
            rect.x + 2,
            rect.y + 1,
            (sidebarX - rect.x - 4).coerceAtLeast(1),
            rect.height - 3,
        )
        drawBrailleGraph(canvas, graphRect, cpuHistory.values(), 100.0, theme.secondary)
        canvas.text(
            rect.x + 2,
            rect.bottom - 1,
            "up ${Formatters.duration(snapshot.cpu.uptimeSeconds)}",
            theme.dim,
        )
        canvas.text(
            (sidebarX - 24).coerceAtLeast(rect.x + 2),
            rect.bottom - 1,
            "${snapshot.totalProcesses} proc  ${snapshot.totalThreads} threads",
            theme.dim,
        )

        val innerX = sidebarX + 2
        val innerWidth = sidebarWidth - 4
        statLine(
            canvas,
            innerX,
            rect.y + 2,
            innerWidth,
            "CPU",
            Formatters.percent(snapshot.cpu.loadPercent).trim(),
            theme.primary,
        )
        drawBar(
            canvas,
            innerX + 4,
            rect.y + 2,
            (innerWidth - 10).coerceAtLeast(3),
            snapshot.cpu.loadPercent,
            loadStyle(snapshot.cpu.loadPercent),
        )
        val frequencyAndTemp = buildString {
            append(Formatters.frequency(snapshot.cpu.frequencyHz))
            snapshot.cpu.temperatureCelsius?.let { append("  %.0f°C".format(it)) }
        }
        canvas.text(innerX, rect.y + 3, Formatters.truncate(frequencyAndTemp, innerWidth), theme.warning)

        if (coreLoads.isEmpty()) {
            canvas.text(
                innerX,
                rect.y + 5,
                Formatters.truncate("${snapshot.cpu.logicalCores} logical processors", innerWidth),
                theme.dim,
            )
        } else {
            val availableCoreColumns =
                ((innerWidth + CORE_COLUMN_GAP) /
                    (MINIMUM_CORE_COLUMN_WIDTH + CORE_COLUMN_GAP))
                    .coerceAtLeast(1)
            val coreColumns = minOf(requestedCoreColumns, availableCoreColumns)
            val rowsUsed = minOf(
                coreRows,
                ceil(coreLoads.size / coreColumns.toDouble()).toInt(),
            )
            val coreColumnWidth =
                (innerWidth - (coreColumns - 1) * CORE_COLUMN_GAP) / coreColumns

            repeat(coreColumns) { column ->
                repeat(rowsUsed) { row ->
                    val coreIndex = column * rowsUsed + row
                    if (coreIndex >= coreLoads.size) return@repeat

                    val load = coreLoads[coreIndex]
                    val x = innerX + column * (coreColumnWidth + CORE_COLUMN_GAP)
                    val y = rect.y + 4 + row
                    val label = "C$coreIndex".padEnd(CORE_LABEL_WIDTH)
                    canvas.text(x, y, label, theme.primary)
                    val meterWidth =
                        (coreColumnWidth - CORE_METER_NON_BAR_WIDTH).coerceAtLeast(3)
                    drawBar(
                        canvas,
                        x + CORE_LABEL_WIDTH,
                        y,
                        meterWidth,
                        load,
                        loadStyle(load),
                        '▪',
                        '·',
                    )
                    canvas.text(
                        x + coreColumnWidth - CORE_PERCENT_WIDTH,
                        y,
                        "%3.0f%%".format(load.coerceIn(0.0, 999.0)),
                        loadStyle(load),
                    )
                }
            }
        }
    }

    private fun drawMemory(canvas: Canvas, rect: Rect, snapshot: SystemSnapshot) {
        val memory = snapshot.memory
        val usedPercent = percent(memory.usedBytes, memory.totalBytes)
        canvas.box(rect, "2 mem", theme)

        val x = rect.x + 2
        val width = rect.width - 4
        var y = rect.y + 1
        statLine(canvas, x, y++, width, "Total:", Formatters.bytes(memory.totalBytes), theme.primary)
        statLine(canvas, x, y++, width, "Used:", Formatters.bytes(memory.usedBytes), theme.primary)
        if (y < rect.bottom) {
            canvas.text(x, y, "${usedPercent.roundToInt()}%", theme.danger)
            drawBar(canvas, x + 5, y++, (width - 5).coerceAtLeast(3), usedPercent, loadStyle(usedPercent))
        }
        if (y < rect.bottom) {
            statLine(canvas, x, y++, width, "Paged Pool:", Formatters.bytes(memory.pagedPoolBytes), theme.warning)
        }
        if (y < rect.bottom) {
            statLine(
                canvas,
                x,
                y++,
                width,
                "Non-paged Pool:",
                Formatters.bytes(memory.nonPagedPoolBytes),
                theme.warning,
            )
        }
        if (y < rect.bottom) {
            statLine(
                canvas,
                x,
                y++,
                width,
                "Committed:",
                Formatters.bytes(memory.committedBytes),
                theme.primary,
            )
        }
        if (y < rect.bottom) {
            statLine(canvas, x, y, width, "Cached:", Formatters.bytes(memory.cachedBytes), theme.download)
        }
    }

    private fun drawDisks(canvas: Canvas, rect: Rect, snapshot: SystemSnapshot) {
        val io = "io ↓${Formatters.rate(snapshot.diskReadBytesPerSecond)} ↑${Formatters.rate(snapshot.diskWriteBytesPerSecond)}"
        canvas.box(rect, "disks", theme, Formatters.truncate(io, (rect.width / 2).coerceAtLeast(2)))
        val x = rect.x + 2
        val width = rect.width - 4
        var y = rect.y + 1
        snapshot.disks.forEach { disk ->
            if (y >= rect.bottom) return@forEach
            val usedPercent = percent(disk.usedBytes, disk.totalBytes)
            statLine(
                canvas,
                x,
                y++,
                width,
                Formatters.truncate(disk.mount, (width / 2).coerceAtLeast(2)),
                Formatters.bytes(disk.totalBytes),
                theme.primary,
            )
            if (y < rect.bottom) {
                canvas.text(x, y, "Used ${usedPercent.roundToInt().toString().padStart(2)}%", theme.dim)
                val barWidth = (width - 17).coerceAtLeast(3)
                drawBar(canvas, x + 9, y, barWidth, usedPercent, loadStyle(usedPercent), '■', '▪')
                canvas.text(
                    x + width - Formatters.bytes(disk.usedBytes).length,
                    y++,
                    Formatters.bytes(disk.usedBytes),
                    theme.primary,
                )
            }
            if (y < rect.bottom) {
                statLine(canvas, x, y++, width, "Free", Formatters.bytes(disk.availableBytes), theme.secondary)
            }
        }
        if (snapshot.disks.isEmpty()) {
            canvas.text(x, y, "No mounted drives", theme.dim)
        }
    }

    private fun drawNetwork(canvas: Canvas, rect: Rect, snapshot: SystemSnapshot) {
        val network = snapshot.network
        val networkLabel = network?.address?.ifBlank { network.name }.orEmpty()
        canvas.box(rect, "3 net  ${Formatters.truncate(networkLabel, 22)}", theme, "sync  auto")
        if (network == null) {
            canvas.text(rect.x + 2, rect.y + 2, "No active network interface", theme.dim)
            return
        }

        val contentWidth = rect.width - 4
        val statsWidth = (contentWidth * NETWORK_STATS_RATIO).roundToInt()
            .coerceIn(MINIMUM_NETWORK_STATS_WIDTH, (contentWidth - 12).coerceAtLeast(MINIMUM_NETWORK_STATS_WIDTH))
        val graphWidth = (contentWidth - statsWidth - 1).coerceAtLeast(8)
        val graphRect = Rect(rect.x + 2, rect.y + 1, graphWidth, rect.height - 2)
        drawMirroredNetworkGraph(
            canvas,
            graphRect,
            networkDownloadHistory.values(),
            networkUploadHistory.values(),
        )

        val x = graphRect.right + 2
        val width = statsWidth
        var y = rect.y + 1
        canvas.text(x, y++, "▼ download", theme.download)
        if (y < rect.bottom) canvas.text(x, y++, "▼ ${Formatters.rate(network.downloadBytesPerSecond)}", theme.primary)
        if (y < rect.bottom) {
            statLine(canvas, x, y++, width, "Top:", Formatters.rate(networkDownloadHistory.peak()), theme.dim)
        }
        if (y < rect.bottom) {
            statLine(canvas, x, y++, width, "Total:", Formatters.bytes(network.receivedBytes), theme.primary)
        }
        if (y < rect.bottom) canvas.text(x, y++, "▲ upload", theme.upload)
        if (y < rect.bottom) canvas.text(x, y++, "▲ ${Formatters.rate(network.uploadBytesPerSecond)}", theme.primary)
        if (y < rect.bottom) {
            statLine(canvas, x, y++, width, "Top:", Formatters.rate(networkUploadHistory.peak()), theme.dim)
        }
        if (y < rect.bottom) {
            statLine(canvas, x, y, width, "Total:", Formatters.bytes(network.sentBytes), theme.primary)
        }
    }

    private fun drawProcessDetail(
        canvas: Canvas,
        rect: Rect,
        snapshot: SystemSnapshot,
        state: ViewState,
    ) {
        val sorted = sortProcesses(snapshot.processes, state.processSort, state.reverseSort)
        val selected = sorted.getOrNull(state.selectedProcess)
        val title = selected?.let { "${it.pid}  ${Formatters.truncate(it.name, 22)}" } ?: "process detail"
        canvas.box(rect, title, theme, "enter hide")
        if (selected == null) {
            canvas.text(rect.x + 2, rect.y + 2, "No process selected", theme.dim)
            return
        }

        val x = rect.x + 2
        val width = rect.width - 4
        var y = rect.y + 1
        val stateText = selected.state.lowercase().replaceFirstChar(Char::uppercase)
        val status = "Status: $stateText   Elapsed: ${Formatters.duration(selected.uptimeSeconds)}   Parent: ${selected.parentPid}"
        canvas.text(x, y++, Formatters.truncate(status, width), theme.primary)
        if (y < rect.bottom) {
            val memoryPercent = percent(selected.memoryBytes, snapshot.memory.totalBytes)
            val resources = "CPU: ${Formatters.percent(selected.cpuPercent).trim()}   " +
                "Memory: ${Formatters.bytes(selected.memoryBytes)} (${memoryPercent.roundToInt()}%)   " +
                "Threads: ${selected.threadCount}"
            canvas.text(x, y++, Formatters.truncate(resources, width), theme.primary)
        }
        if (y < rect.bottom) {
            val io = "I/O read: ${Formatters.bytes(selected.readBytes)}   write: ${Formatters.bytes(selected.writtenBytes)}"
            canvas.text(x, y++, Formatters.truncate(io, width), theme.dim)
        }
        if (y < rect.bottom) {
            canvas.text(x, y, Formatters.truncate(selected.command.ifBlank { selected.name }, width), theme.secondary)
        }
    }

    private fun drawProcesses(
        canvas: Canvas,
        rect: Rect,
        snapshot: SystemSnapshot,
        state: ViewState,
    ) {
        val direction = if (state.reverseSort) "↑" else "↓"
        canvas.box(
            rect,
            "4 proc  ${snapshot.totalProcesses}",
            theme,
            "$direction ${state.processSort.label}  reverse",
        )
        val contentWidth = rect.width - 4
        canvas.text(rect.x + 2, rect.y + 1, formatProcessHeader(contentWidth), theme.title)

        val sorted = sortProcesses(snapshot.processes, state.processSort, state.reverseSort)
        val availableRows = (rect.height - 4).coerceAtLeast(0)
        sorted.drop(state.processOffset).take(availableRows).forEachIndexed { row, process ->
            val absoluteIndex = state.processOffset + row
            val style = when {
                absoluteIndex == state.selectedProcess -> theme.selected
                process.cpuPercent >= 25.0 -> theme.warning
                process.cpuPercent <= 0.05 -> theme.dim
                else -> theme.primary
            }
            canvas.text(
                rect.x + 2,
                rect.y + 2 + row,
                formatProcess(process, contentWidth),
                style,
            )
        }

        val footerY = rect.bottom - 1
        val footer = "↑↓ select  enter detail  s sort  r rev  p ${if (state.paused) "resume" else "pause"}  h help  q quit"
        canvas.text(rect.x + 2, footerY, Formatters.truncate(footer, contentWidth), theme.dim)
        val count = "${(state.selectedProcess + 1).coerceAtMost(sorted.size)}/${snapshot.totalProcesses}"
        canvas.text(rect.right - count.length - 2, rect.bottom, count, theme.title)
    }

    private fun drawHelp(canvas: Canvas) {
        val width = 58.coerceAtMost(canvas.width - 4)
        val height = 15.coerceAtMost(canvas.height - 4)
        val rect = Rect((canvas.width - width) / 2, (canvas.height - height) / 2, width, height)
        canvas.box(rect, "HELP", theme, "h close")
        val help = listOf(
            "q / Esc       Quit Pstop",
            "Up / Down     Select a process",
            "Page Up/Down  Move one process page",
            "Enter         Toggle selected-process details",
            "s             Cycle CPU, memory, PID and name sorting",
            "r             Reverse process sorting",
            "p             Pause or resume metric sampling",
            "1 2 3 4       Toggle dashboard panels",
            "h / ?         Close this help",
            "",
            "Pstop is read-only and never terminates processes.",
        )
        help.take(height - 2).forEachIndexed { index, line ->
            canvas.text(rect.x + 2, rect.y + 1 + index, Formatters.truncate(line, rect.width - 4), theme.primary)
        }
    }

    private fun drawBrailleGraph(
        canvas: Canvas,
        rect: Rect,
        values: List<Double>,
        maximum: Double,
        style: String,
        zeroAtBottom: Boolean = true,
    ) {
        if (rect.width <= 0 || rect.height <= 0) return
        repeat(rect.height) { row ->
            if (row % 3 == 2) canvas.horizontal(rect.x, rect.y + row, rect.width, '·', theme.dim)
        }

        val masks = Array(rect.height) { IntArray(rect.width) }
        val visible = values.takeLast(rect.width * 2)
        val pixelOffset = rect.width * 2 - visible.size
        val maximumPixel = (rect.height * 4 - 1).coerceAtLeast(1)
        visible.forEachIndexed { index, value ->
            val pixelX = pixelOffset + index
            val characterX = pixelX / 2
            val rightHalf = pixelX % 2 == 1
            val level = ((value.coerceIn(0.0, maximum) / maximum) * maximumPixel)
                .roundToInt()
                .coerceIn(0, maximumPixel)
            val characterRow = level / 4
            val subRow = level % 4
            val y = if (zeroAtBottom) rect.bottom - characterRow else rect.y + characterRow
            val dotRow = if (zeroAtBottom) 3 - subRow else subRow
            val bit = if (rightHalf) RIGHT_BRAILLE_BITS[dotRow] else LEFT_BRAILLE_BITS[dotRow]
            masks[y - rect.y][characterX] = masks[y - rect.y][characterX] or bit
        }
        masks.forEachIndexed { row, columns ->
            columns.forEachIndexed { column, mask ->
                if (mask != 0) {
                    canvas.text(
                        rect.x + column,
                        rect.y + row,
                        (BRAILLE_BASE + mask).toChar().toString(),
                        style,
                    )
                }
            }
        }
    }

    private fun drawMirroredNetworkGraph(
        canvas: Canvas,
        rect: Rect,
        download: List<Long>,
        upload: List<Long>,
    ) {
        if (rect.width <= 0 || rect.height < 3) return
        val centerY = rect.y + rect.height / 2
        canvas.horizontal(rect.x, centerY, rect.width, '·', theme.dim)
        val maximum = maxOf(download.maxOrNull() ?: 0L, upload.maxOrNull() ?: 0L, 1L)
        val upperHeight = (centerY - rect.y).coerceAtLeast(1)
        val lowerHeight = (rect.bottom - centerY).coerceAtLeast(1)
        drawBrailleGraph(
            canvas,
            Rect(rect.x, rect.y, rect.width, upperHeight),
            download.map(Long::toDouble),
            maximum.toDouble(),
            theme.download,
        )
        drawBrailleGraph(
            canvas,
            Rect(rect.x, centerY + 1, rect.width, lowerHeight),
            upload.map(Long::toDouble),
            maximum.toDouble(),
            theme.upload,
            zeroAtBottom = false,
        )
    }

    private fun drawBar(
        canvas: Canvas,
        x: Int,
        y: Int,
        width: Int,
        percent: Double,
        style: String,
        filledCharacter: Char = '■',
        emptyCharacter: Char = '·',
    ) {
        if (width <= 0) return
        val filled = (width * percent.coerceIn(0.0, 100.0) / 100.0).roundToInt()
        canvas.text(x, y, filledCharacter.toString().repeat(filled), style)
        canvas.text(x + filled, y, emptyCharacter.toString().repeat(width - filled), theme.dim)
    }

    private fun statLine(
        canvas: Canvas,
        x: Int,
        y: Int,
        width: Int,
        label: String,
        value: String,
        style: String,
    ) {
        if (width <= 0) return
        val safeValue = Formatters.truncate(value, width)
        val labelWidth = (width - safeValue.length - 1).coerceAtLeast(0)
        val safeLabel = Formatters.truncate(label, labelWidth)
        canvas.text(x, y, safeLabel, style)
        canvas.text(x + width - safeValue.length, y, safeValue, style)
    }

    private fun drawCenteredBorderLabel(canvas: Canvas, rect: Rect, value: String) {
        val label = " $value "
        if (label.length < rect.width - 10) {
            canvas.text(rect.x + (rect.width - label.length) / 2, rect.y, label, theme.title)
        }
    }

    private fun loadStyle(value: Double): String = when {
        value >= 85.0 -> theme.danger
        value >= 65.0 -> theme.warning
        else -> theme.secondary
    }

    companion object {
        private const val HISTORY_CAPACITY = 400
        private const val MINIMUM_WIDTH = 72
        private const val MINIMUM_HEIGHT = 22
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
        private const val MINIMUM_CPU_SIDEBAR_WIDTH = 23
        private const val MINIMUM_CPU_GRAPH_WIDTH = 24
        private const val CPU_GRAPH_FRAME_ALLOWANCE = 5
        private const val CPU_CORE_VERTICAL_CHROME = 6
        private const val CPU_SIDEBAR_HORIZONTAL_CHROME = 4
        private const val MINIMUM_CORE_COLUMN_WIDTH = 14
        private const val CORE_COLUMN_GAP = 1
        private const val MAXIMUM_CORE_ROWS_PER_COLUMN = 8
        private const val CORE_LABEL_WIDTH = 4
        private const val CORE_PERCENT_WIDTH = 5
        private const val CORE_METER_NON_BAR_WIDTH = 11
        private const val MINIMUM_NETWORK_STATS_WIDTH = 16
        private const val CPU_HEIGHT_RATIO = 0.33
        private const val LEFT_WIDTH_RATIO = 0.45
        private const val MEMORY_WIDTH_RATIO = 0.49
        private const val LEFT_TOP_HEIGHT_RATIO = 0.58
        private const val DETAIL_HEIGHT_RATIO = 0.31
        private const val CPU_SIDEBAR_RATIO = 0.23
        private const val NETWORK_STATS_RATIO = 0.42
        private const val BRAILLE_BASE = 0x2800
        private val LEFT_BRAILLE_BITS = intArrayOf(0x01, 0x02, 0x04, 0x40)
        private val RIGHT_BRAILLE_BITS = intArrayOf(0x08, 0x10, 0x20, 0x80)

        fun sortProcesses(
            processes: List<ProcessSnapshot>,
            sort: ProcessSort,
            reversed: Boolean,
        ): List<ProcessSnapshot> {
            val comparator = when (sort) {
                ProcessSort.CPU -> compareByDescending<ProcessSnapshot> { it.cpuPercent }
                ProcessSort.MEMORY -> compareByDescending { it.memoryBytes }
                ProcessSort.PID -> compareBy { it.pid }
                ProcessSort.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            }
            return processes.sortedWith(if (reversed) comparator.reversed() else comparator)
        }

        fun formatProcessHeader(width: Int): String {
            if (width < 44) {
                val nameWidth = (width - 24).coerceAtLeast(5)
                return "PID".padStart(6) + " " + "PROGRAM".padEnd(nameWidth) + " CPU%      MEM"
            }
            val columns = processColumns(width)
            return buildString {
                append("PID".padStart(7))
                append(" ")
                append("PROGRAM".padEnd(columns.nameWidth))
                append(" ")
                append("COMMAND".padEnd(columns.commandWidth))
                append(" ")
                append("THR".padStart(4))
                append(" ")
                append("MEM".padStart(9))
                append(" ")
                append("CPU%".padStart(6))
            }.take(width).padEnd(width)
        }

        fun formatProcess(process: ProcessSnapshot, width: Int): String {
            if (width < 44) {
                val nameWidth = (width - 24).coerceAtLeast(5)
                return buildString {
                    append(process.pid.toString().padStart(6))
                    append(" ")
                    append(Formatters.truncate(process.name, nameWidth).padEnd(nameWidth))
                    append(" ")
                    append("%5.1f".format(process.cpuPercent.coerceIn(0.0, 999.9)))
                    append(" ")
                    append(Formatters.bytes(process.memoryBytes).padStart(9))
                }.take(width).padEnd(width)
            }
            val columns = processColumns(width)
            val command = process.command.ifBlank { process.name }
            return buildString {
                append(process.pid.toString().padStart(7))
                append(" ")
                append(Formatters.truncate(process.name, columns.nameWidth).padEnd(columns.nameWidth))
                append(" ")
                append(Formatters.truncate(command, columns.commandWidth).padEnd(columns.commandWidth))
                append(" ")
                append(process.threadCount.toString().padStart(4))
                append(" ")
                append(Formatters.bytes(process.memoryBytes).padStart(9))
                append(" ")
                append("%5.1f".format(process.cpuPercent.coerceIn(0.0, 999.9)))
            }.take(width).padEnd(width)
        }

        private fun processColumns(width: Int): ProcessColumns {
            val nameWidth = (width * 0.23).roundToInt().coerceIn(10, 18)
            val commandWidth = (width - 32 - nameWidth).coerceAtLeast(5)
            return ProcessColumns(nameWidth, commandWidth)
        }

        private fun percent(part: Long, total: Long): Double =
            if (total <= 0L) 0.0 else part * 100.0 / total
    }

    private data class ProcessColumns(val nameWidth: Int, val commandWidth: Int)

    private class RateHistory(private val capacity: Int) {
        private val values = ArrayDeque<Long>(capacity)

        fun add(value: Long) {
            if (values.size == capacity) values.removeFirst()
            values.addLast(value.coerceAtLeast(0L))
        }

        fun values(): List<Long> = values.toList()

        fun peak(): Long = values.maxOrNull() ?: 0L
    }
}
