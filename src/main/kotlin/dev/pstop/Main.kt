package dev.pstop

import dev.pstop.cli.AppOptions
import dev.pstop.system.OshiMetricsCollector
import dev.pstop.ui.TerminalApplication
import java.io.PrintWriter
import kotlin.system.exitProcess

private const val VERSION = "0.1.0"

fun main(args: Array<String>) {
    val options = try {
        AppOptions.parse(args)
    } catch (exception: IllegalArgumentException) {
        System.err.println("pstop: ${exception.message}")
        System.err.println(AppOptions.usage())
        exitProcess(2)
    }

    when {
        options.showHelp -> {
            println(AppOptions.usage())
            return
        }
        options.showVersion -> {
            println("Pstop $VERSION")
            return
        }
    }

    val collector = OshiMetricsCollector(options.processLimit)
    if (options.once) {
        val snapshot = collector.sample()
        PrintWriter(System.out, true).use { writer ->
            writer.print(TerminalApplication.renderPlainSnapshot(snapshot, options.width, options.height))
        }
        return
    }

    TerminalApplication(collector, options).run()
}
