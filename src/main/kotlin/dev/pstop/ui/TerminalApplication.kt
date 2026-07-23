package dev.pstop.ui

import dev.pstop.cli.AppOptions
import dev.pstop.core.model.SystemSnapshot
import dev.pstop.system.MetricsCollector
import org.jline.terminal.Terminal
import org.jline.utils.InfoCmp.Capability
import java.util.concurrent.atomic.AtomicBoolean

class TerminalApplication(
    private val collector: MetricsCollector,
    private val options: AppOptions,
) {
    private var state = ViewState()
    private var latestSnapshot: SystemSnapshot? = null
    private val resized = AtomicBoolean(true)
    private val exitRequested = AtomicBoolean(false)
    private val topBoundaryLimiter = ConsecutiveBoundaryLimiter()

    fun run() {
        exitRequested.set(false)
        TerminalBackend.open()
            .use { terminal ->
                terminal.handle(Terminal.Signal.WINCH) { resized.set(true) }
                terminal.handle(Terminal.Signal.INT) { exitRequested.set(true) }
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

        while (running && !exitRequested.get()) {
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
                val result = handleInput(input, terminal)
                running = result.running
                if (result.repaint) resized.set(true)
            }
        }
    }

    private fun handleInput(input: Int, terminal: Terminal): InputResult {
        if (isImmediateQuitKey(input)) return InputResult(running = false)

        when (input) {
            'h'.code, 'H'.code, '?'.code -> {
                topBoundaryLimiter.reset()
                state = state.copy(showHelp = !state.showHelp)
            }
            10, 13 -> {
                topBoundaryLimiter.reset()
                state = state.copy(showDetails = !state.showDetails)
            }
            'p'.code, 'P'.code -> {
                topBoundaryLimiter.reset()
                state = state.copy(paused = !state.paused)
            }
            's'.code, 'S'.code -> {
                topBoundaryLimiter.reset()
                state = state.copy(processSort = state.processSort.next())
            }
            'r'.code, 'R'.code -> {
                topBoundaryLimiter.reset()
                state = state.copy(reverseSort = !state.reverseSort)
            }
            '1'.code, '2'.code, '3'.code, '4'.code -> {
                topBoundaryLimiter.reset()
                state = state.togglePanel(input - '0'.code)
            }
            else -> {
                val binding = readBinding(input, terminal)
                if (binding == TerminalBinding.ESCAPE) return InputResult(running = false)
                val isUpAtTop = binding == TerminalBinding.UP && state.selectedProcess == 0
                if (!topBoundaryLimiter.allowRepaint(isUpAtTop)) return InputResult()

                val visibleRows = (terminal.height / 2 - 4).coerceAtLeast(1)
                val processCount = latestSnapshot?.processes?.size ?: 0
                val previousState = state
                state = when (binding) {
                    TerminalBinding.UP -> moveSelection(-1, visibleRows, processCount)
                    TerminalBinding.DOWN -> moveSelection(1, visibleRows, processCount)
                    TerminalBinding.PAGE_UP -> moveSelection(-visibleRows, visibleRows, processCount)
                    TerminalBinding.PAGE_DOWN -> moveSelection(visibleRows, visibleRows, processCount)
                    TerminalBinding.LEFT,
                    TerminalBinding.RIGHT,
                    TerminalBinding.IGNORED,
                    TerminalBinding.ESCAPE,
                    null,
                    -> state
                }
                return InputResult(repaint = state != previousState || isUpAtTop)
            }
        }
        return InputResult(repaint = true)
    }

    private fun readBinding(first: Int, terminal: Terminal): TerminalBinding? =
        decodeTerminalBinding(first) { terminal.reader().read(10L) }

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
        fun renderPlainSnapshot(
            snapshot: SystemSnapshot,
            width: Int,
            height: Int,
            state: ViewState = ViewState(),
        ): String {
            val renderer = DashboardRenderer(Theme.nord(false))
            return renderer.render(snapshot, width, height, state).renderPlain()
        }
    }

    private data class InputResult(
        val running: Boolean = true,
        val repaint: Boolean = false,
    )
}
