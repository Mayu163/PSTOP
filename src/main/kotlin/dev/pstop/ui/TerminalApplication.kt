package dev.pstop.ui

import dev.pstop.cli.AppOptions
import dev.pstop.core.model.SystemSnapshot
import dev.pstop.system.MetricsCollector
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.jline.utils.InfoCmp.Capability
import java.util.concurrent.atomic.AtomicBoolean

class TerminalApplication(
    private val collector: MetricsCollector,
    private val options: AppOptions,
) {
    private var state = ViewState()
    private var latestSnapshot: SystemSnapshot? = null
    private val resized = AtomicBoolean(true)

    fun run() {
        TerminalBuilder.builder()
            .system(true)
            .jna(true)
            .build()
            .use { terminal ->
                terminal.handle(Terminal.Signal.WINCH) { resized.set(true) }
                val previousAttributes = terminal.enterRawMode()
                try {
                    terminal.puts(Capability.enter_ca_mode)
                    terminal.puts(Capability.keypad_xmit)
                    terminal.writer().print("\u001B[?25l")
                    terminal.flush()
                    eventLoop(terminal)
                } finally {
                    terminal.writer().print("\u001B[?25h\u001B[0m")
                    terminal.puts(Capability.keypad_local)
                    terminal.puts(Capability.exit_ca_mode)
                    terminal.setAttributes(previousAttributes)
                    terminal.flush()
                }
            }
    }

    private fun eventLoop(terminal: Terminal) {
        val theme = Theme.nord(!options.noColor)
        val renderer = DashboardRenderer(theme)
        var running = true
        var nextSampleAt = 0L

        while (running) {
            val now = System.currentTimeMillis()
            if (!state.paused && now >= nextSampleAt) {
                latestSnapshot = runCatching(collector::sample).getOrElse { exception ->
                    showFatal(terminal, exception)
                    return
                }
                nextSampleAt = now + options.refreshMillis
                resized.set(true)
            }

            if (resized.getAndSet(false)) {
                val snapshot = latestSnapshot
                if (snapshot != null) {
                    val width = terminal.width.takeIf { it > 0 } ?: options.width
                    val height = terminal.height.takeIf { it > 0 } ?: options.height
                    val canvas = renderer.render(snapshot, width, height, state)
                    terminal.writer().print("\u001B[H")
                    terminal.writer().print(canvas.render(theme))
                    terminal.flush()
                }
            }

            val waitMillis = if (state.paused) 100L else (nextSampleAt - System.currentTimeMillis()).coerceIn(10L, 100L)
            val input = terminal.reader().read(waitMillis)
            if (input >= 0) {
                running = handleInput(input, terminal)
                resized.set(true)
            }
        }
    }

    private fun handleInput(input: Int, terminal: Terminal): Boolean {
        when (input) {
            'q'.code, 'Q'.code -> return false
            'h'.code, 'H'.code, '?'.code -> state = state.copy(showHelp = !state.showHelp)
            10, 13 -> state = state.copy(showDetails = !state.showDetails)
            'p'.code, 'P'.code -> state = state.copy(paused = !state.paused)
            's'.code, 'S'.code -> state = state.copy(processSort = state.processSort.next())
            'r'.code, 'R'.code -> state = state.copy(reverseSort = !state.reverseSort)
            '1'.code, '2'.code, '3'.code, '4'.code -> {
                val panel = input - '0'.code
                val visible = state.visiblePanels.toMutableSet()
                if (!visible.add(panel)) visible.remove(panel)
                state = state.copy(visiblePanels = visible)
            }
            else -> {
                val binding = readBinding(input, terminal)
                if (input == 27 && binding == null) return false
                val visibleRows = (terminal.height / 2 - 4).coerceAtLeast(1)
                val processCount = latestSnapshot?.processes?.size ?: 0
                state = when (binding) {
                    "UP" -> moveSelection(-1, visibleRows, processCount)
                    "DOWN" -> moveSelection(1, visibleRows, processCount)
                    "PAGE_UP" -> moveSelection(-visibleRows, visibleRows, processCount)
                    "PAGE_DOWN" -> moveSelection(visibleRows, visibleRows, processCount)
                    else -> state
                }
            }
        }
        return true
    }

    private fun readBinding(first: Int, terminal: Terminal): String? {
        if (first != 27) return null
        val second = terminal.reader().read(10L)
        if (second != '['.code && second != 'O'.code) return null
        return when (terminal.reader().read(10L)) {
            'A'.code -> "UP"
            'B'.code -> "DOWN"
            '5'.code -> {
                terminal.reader().read(10L)
                "PAGE_UP"
            }
            '6'.code -> {
                terminal.reader().read(10L)
                "PAGE_DOWN"
            }
            else -> null
        }
    }

    private fun moveSelection(delta: Int, visibleRows: Int, processCount: Int): ViewState {
        if (processCount == 0) return state
        val selected = (state.selectedProcess + delta).coerceIn(0, processCount - 1)
        val offset = when {
            selected < state.processOffset -> selected
            selected >= state.processOffset + visibleRows -> selected - visibleRows + 1
            else -> state.processOffset
        }
        return state.copy(selectedProcess = selected, processOffset = offset)
    }

    private fun showFatal(terminal: Terminal, exception: Throwable) {
        terminal.writer().print("\u001B[H\u001B[2J")
        terminal.writer().println("Pstop could not collect Windows metrics:")
        terminal.writer().println(exception.message ?: exception::class.simpleName)
        terminal.flush()
    }

    companion object {
        fun renderPlainSnapshot(snapshot: SystemSnapshot, width: Int, height: Int): String {
            val renderer = DashboardRenderer(Theme.nord(false))
            return renderer.render(snapshot, width, height, ViewState()).renderPlain()
        }
    }
}
