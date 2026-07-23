package dev.pstop.cli

data class AppOptions(
    val refreshMillis: Long = 1_500,
    val processLimit: Int = 200,
    val once: Boolean = false,
    val noColor: Boolean = false,
    val width: Int = 120,
    val height: Int = 36,
    val showHelp: Boolean = false,
    val showVersion: Boolean = false,
) {
    companion object {
        fun parse(arguments: Array<String>): AppOptions {
            var options = AppOptions()
            var index = 0
            while (index < arguments.size) {
                when (val argument = arguments[index]) {
                    "-h", "--help" -> options = options.copy(showHelp = true)
                    "-v", "--version" -> options = options.copy(showVersion = true)
                    "--once" -> options = options.copy(once = true)
                    "--no-color" -> options = options.copy(noColor = true)
                    "--refresh" -> {
                        val value = arguments.getOrNull(++index)
                            ?: throw IllegalArgumentException("--refresh requires milliseconds")
                        val refresh = value.toLongOrNull()
                            ?: throw IllegalArgumentException("invalid refresh interval: $value")
                        require(refresh in 250..60_000) {
                            "refresh interval must be between 250 and 60000 milliseconds"
                        }
                        options = options.copy(refreshMillis = refresh)
                    }
                    "--process-limit" -> {
                        val value = arguments.getOrNull(++index)
                            ?: throw IllegalArgumentException("--process-limit requires a count")
                        val limit = value.toIntOrNull()
                            ?: throw IllegalArgumentException("invalid process limit: $value")
                        require(limit in 10..5_000) {
                            "process limit must be between 10 and 5000"
                        }
                        options = options.copy(processLimit = limit)
                    }
                    "--width" -> {
                        val value = arguments.getOrNull(++index)
                            ?: throw IllegalArgumentException("--width requires columns")
                        options = options.copy(width = parseDimension("width", value, 60..400))
                    }
                    "--height" -> {
                        val value = arguments.getOrNull(++index)
                            ?: throw IllegalArgumentException("--height requires rows")
                        options = options.copy(height = parseDimension("height", value, 20..200))
                    }
                    else -> throw IllegalArgumentException("unknown option: $argument")
                }
                index++
            }
            return options
        }

        fun usage(): String = """
            Pstop - a Windows resource monitor for Microsoft Windows Terminal

            Usage: pstop [options]

              -h, --help              Show this help
              -v, --version           Show the version
                  --once              Print one plain-text snapshot and exit
                  --no-color          Disable terminal colors
                  --refresh <ms>      Refresh interval (250-60000, default 1500)
                  --process-limit <n> Number of processes to collect (default 200)
                  --width <columns>   Width for --once output (default 120)
                  --height <rows>     Height for --once output (default 36)

            Interactive keys:
              q/Esc quit   Up/Down select   PgUp/PgDn page   s change sort
              r reverse    p pause          Enter details     h/? help
              1-4 toggle panels
        """.trimIndent()

        private fun parseDimension(name: String, value: String, range: IntRange): Int {
            val dimension = value.toIntOrNull()
                ?: throw IllegalArgumentException("invalid $name: $value")
            require(dimension in range) {
                "$name must be between ${range.first} and ${range.last}"
            }
            return dimension
        }
    }
}
