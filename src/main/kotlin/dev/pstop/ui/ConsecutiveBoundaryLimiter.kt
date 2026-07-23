package dev.pstop.ui

internal class ConsecutiveBoundaryLimiter(
    private val maximumAllowedAttempts: Int = 3,
) {
    private var consecutiveAttempts = 0

    init {
        require(maximumAllowedAttempts >= 0)
    }

    fun allowRepaint(isUpAtTop: Boolean): Boolean {
        if (!isUpAtTop) {
            consecutiveAttempts = 0
            return true
        }
        if (consecutiveAttempts >= maximumAllowedAttempts) return false

        consecutiveAttempts++
        return true
    }

    fun reset() {
        consecutiveAttempts = 0
    }
}
