package dev.pstop.ui

import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder

internal object TerminalBackend {
    const val PROVIDER = "jni"

    fun open(): Terminal = TerminalBuilder.builder()
        .system(true)
        .provider(PROVIDER)
        .build()
}
