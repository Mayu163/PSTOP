package dev.pstop.ui

data class Theme(
    val reset: String,
    val dim: String,
    val title: String,
    val border: String,
    val primary: String,
    val secondary: String,
    val warning: String,
    val danger: String,
    val download: String,
    val upload: String,
    val selected: String,
) {
    companion object {
        fun nord(enabled: Boolean): Theme = if (enabled) {
            Theme(
                reset = "\u001B[0m",
                dim = "\u001B[38;2;88;98;94m",
                title = "\u001B[1;38;2;238;238;238m",
                border = "\u001B[38;2;77;112;86m",
                primary = "\u001B[38;2;225;230;226m",
                secondary = "\u001B[38;2;105;211;141m",
                warning = "\u001B[38;2;240;194;81m",
                danger = "\u001B[38;2;230;85;95m",
                download = "\u001B[38;2;87;168;255m",
                upload = "\u001B[38;2;210;95;180m",
                selected = "\u001B[1;38;2;15;19;17m\u001B[48;2;137;210;162m",
            )
        } else {
            Theme("", "", "", "", "", "", "", "", "", "", "")
        }
    }
}
