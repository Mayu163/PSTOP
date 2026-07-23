package dev.pstop.ui

class Canvas(val width: Int, val height: Int) {
    private val cells = Array(height) { CharArray(width) { ' ' } }
    private val styles = Array(height) { arrayOfNulls<String>(width) }

    fun text(x: Int, y: Int, value: String, style: String = "") {
        if (y !in 0 until height) return
        value.forEachIndexed { index, character ->
            val column = x + index
            if (column in 0 until width) {
                cells[y][column] = character
                styles[y][column] = style
            }
        }
    }

    fun horizontal(x: Int, y: Int, length: Int, character: Char, style: String = "") {
        repeat(length.coerceAtLeast(0)) { text(x + it, y, character.toString(), style) }
    }

    fun vertical(x: Int, y: Int, length: Int, character: Char, style: String = "") {
        repeat(length.coerceAtLeast(0)) { text(x, y + it, character.toString(), style) }
    }

    fun box(rect: Rect, title: String, theme: Theme, rightTitle: String = "") {
        if (rect.width < 2 || rect.height < 2) return
        horizontal(rect.x + 1, rect.y, rect.width - 2, '─', theme.border)
        horizontal(rect.x + 1, rect.bottom, rect.width - 2, '─', theme.border)
        vertical(rect.x, rect.y + 1, rect.height - 2, '│', theme.border)
        vertical(rect.right, rect.y + 1, rect.height - 2, '│', theme.border)
        text(rect.x, rect.y, "╭", theme.border)
        text(rect.right, rect.y, "╮", theme.border)
        text(rect.x, rect.bottom, "╰", theme.border)
        text(rect.right, rect.bottom, "╯", theme.border)
        val label = " $title "
        text(rect.x + 2, rect.y, label.take((rect.width - 4).coerceAtLeast(0)), theme.title)
        if (rightTitle.isNotBlank() && rect.width >= label.length + rightTitle.length + 6) {
            val rightLabel = " $rightTitle "
            text(rect.right - rightLabel.length - 1, rect.y, rightLabel, theme.title)
        }
    }

    fun render(theme: Theme): String = buildString {
        var activeStyle = ""
        for (row in cells.indices) {
            for (column in cells[row].indices) {
                val style = styles[row][column].orEmpty()
                if (style != activeStyle) {
                    append(theme.reset)
                    append(style)
                    activeStyle = style
                }
                append(cells[row][column])
            }
            append(theme.reset)
            if (row != cells.lastIndex) append('\n')
            activeStyle = ""
        }
    }

    fun renderPlain(): String = cells.joinToString("\n") { String(it).trimEnd() } + "\n"
}

data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
    val right: Int get() = x + width - 1
    val bottom: Int get() = y + height - 1
}
