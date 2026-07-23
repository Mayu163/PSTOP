package dev.pstop.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConsecutiveBoundaryLimiterTest {
    @Test
    fun `allows only three consecutive upward repaints at the top`() {
        val limiter = ConsecutiveBoundaryLimiter()

        repeat(3) {
            assertTrue(limiter.allowRepaint(isUpAtTop = true))
        }
        assertFalse(limiter.allowRepaint(isUpAtTop = true))
        assertFalse(limiter.allowRepaint(isUpAtTop = true))
    }

    @Test
    fun `a different navigation input resets the top limit`() {
        val limiter = ConsecutiveBoundaryLimiter()

        repeat(4) {
            limiter.allowRepaint(isUpAtTop = true)
        }
        assertTrue(limiter.allowRepaint(isUpAtTop = false))
        assertTrue(limiter.allowRepaint(isUpAtTop = true))
    }
}
