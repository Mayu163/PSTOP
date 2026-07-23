package dev.pstop.core

class History(private val capacity: Int) {
    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    private val values = ArrayDeque<Double>(capacity)

    fun add(value: Double) {
        if (values.size == capacity) {
            values.removeFirst()
        }
        values.addLast(value.coerceIn(0.0, 100.0))
    }

    fun values(): List<Double> = values.toList()
}
