package dev.pstop.ui

import org.jline.terminal.TerminalBuilder
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TerminalBackendTest {
    @Test
    fun `uses an installed non-deprecated terminal provider`() {
        assertTrue(TerminalBackend.PROVIDER !in TerminalBuilder.DEPRECATED_PROVIDERS)
        assertNotNull(
            javaClass.classLoader.getResource(
                "META-INF/services/org/jline/terminal/provider/${TerminalBackend.PROVIDER}",
            ),
        )
        assertNull(
            javaClass.classLoader.getResource(
                "META-INF/services/org/jline/terminal/provider/jna",
            ),
        )
    }
}
