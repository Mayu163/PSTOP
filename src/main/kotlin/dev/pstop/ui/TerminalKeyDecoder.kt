package dev.pstop.ui

internal enum class TerminalBinding {
    ESCAPE,
    IGNORED,
    UP,
    DOWN,
    LEFT,
    RIGHT,
    PAGE_UP,
    PAGE_DOWN,
}

internal fun decodeTerminalBinding(
    first: Int,
    readNext: () -> Int,
): TerminalBinding? {
    if (first != ESCAPE) return null

    val introducer = readNext()
    if (introducer < 0) return TerminalBinding.ESCAPE
    if (introducer != CSI && introducer != SS3) return TerminalBinding.IGNORED

    return when (readNext()) {
        'A'.code -> TerminalBinding.UP
        'B'.code -> TerminalBinding.DOWN
        'C'.code -> TerminalBinding.RIGHT
        'D'.code -> TerminalBinding.LEFT
        '5'.code -> {
            readNext()
            TerminalBinding.PAGE_UP
        }
        '6'.code -> {
            readNext()
            TerminalBinding.PAGE_DOWN
        }
        else -> TerminalBinding.IGNORED
    }
}

internal fun isImmediateQuitKey(input: Int): Boolean =
    input == CONTROL_C || input == 'q'.code || input == 'Q'.code

private const val CONTROL_C = 3
private const val ESCAPE = 27
private const val CSI = '['.code
private const val SS3 = 'O'.code
