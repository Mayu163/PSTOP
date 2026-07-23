package dev.pstop.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TerminalKeyDecoderTest {
    @Test
    fun `decodes navigation keys including ignored horizontal arrows`() {
        assertEquals(TerminalBinding.UP, decode(27, '[', 'A'))
        assertEquals(TerminalBinding.DOWN, decode(27, '[', 'B'))
        assertEquals(TerminalBinding.LEFT, decode(27, '[', 'D'))
        assertEquals(TerminalBinding.RIGHT, decode(27, '[', 'C'))
        assertEquals(TerminalBinding.LEFT, decode(27, 'O', 'D'))
        assertEquals(TerminalBinding.RIGHT, decode(27, 'O', 'C'))
    }

    @Test
    fun `keeps a bare escape distinct from horizontal arrows`() {
        assertEquals(TerminalBinding.ESCAPE, decodeTerminalBinding(27) { -1 })
        assertNull(decodeTerminalBinding('q'.code) { error("must not read") })
    }

    @Test
    fun `ignores unknown escape sequences instead of treating them as quit`() {
        assertEquals(TerminalBinding.IGNORED, decode(27, '[', 'H'))
        assertEquals(TerminalBinding.IGNORED, decode(27, 'O', 'F'))
        assertEquals(TerminalBinding.IGNORED, decode(27, 'x'))
    }

    @Test
    fun `consumes page key terminator`() {
        val input = ArrayDeque(listOf('['.code, '5'.code, '~'.code))

        assertEquals(
            TerminalBinding.PAGE_UP,
            decodeTerminalBinding(27) { input.removeFirst() },
        )
        assertEquals(0, input.size)
    }

    @Test
    fun `recognizes ctrl c and q as immediate quit keys`() {
        assertEquals(true, isImmediateQuitKey(3))
        assertEquals(true, isImmediateQuitKey('q'.code))
        assertEquals(true, isImmediateQuitKey('Q'.code))
        assertEquals(false, isImmediateQuitKey(27))
        assertEquals(false, isImmediateQuitKey('C'.code))
    }

    private fun decode(first: Int, vararg sequence: Char): TerminalBinding? {
        val input = ArrayDeque(sequence.map(Char::code))
        return decodeTerminalBinding(first) { input.removeFirstOrNull() ?: -1 }
    }
}
