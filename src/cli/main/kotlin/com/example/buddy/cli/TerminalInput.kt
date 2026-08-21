package com.example.buddy.cli

import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder

sealed interface TerminalReadResult {
    data class Line(val text: String) : TerminalReadResult
    data object Terminate : TerminalReadResult
}

class TerminalInput : AutoCloseable {
    private val terminal: Terminal = TerminalBuilder.builder()
        .system(true)
        .build()

    init {
        terminal.enterRawMode()
    }

    fun readLine(prompt: String): TerminalReadResult {
        terminal.writer().print(prompt)
        terminal.writer().flush()
        val line = StringBuilder()
        while (true) {
            val value = terminal.reader().read()
            if (value < 0 || value == 4) {
                terminal.writer().println()
                terminal.writer().flush()
                return TerminalReadResult.Terminate
            }
            when (value) {
                10, 13 -> {
                    terminal.writer().println()
                    terminal.writer().flush()
                    return TerminalReadResult.Line(line.toString())
                }
                8, 127 -> {
                    if (line.isNotEmpty()) {
                        line.deleteCharAt(line.lastIndex)
                        terminal.writer().print("\b \b")
                        terminal.writer().flush()
                    }
                }
                27 -> {
                    val next = terminal.reader().read()
                    if (next == 27) {
                        terminal.writer().println()
                        terminal.writer().flush()
                        return TerminalReadResult.Terminate
                    }
                    if (next == 4) {
                        terminal.writer().println()
                        terminal.writer().flush()
                        return TerminalReadResult.Terminate
                    }
                    if (next >= 0 && next != 4) {
                        append(next, line)
                    }
                }
                else -> append(value, line)
            }
        }
    }

    private fun append(value: Int, line: StringBuilder) {
        if (value < 32 || value == 127) return
        line.append(value.toChar())
        terminal.writer().print(value.toChar())
        terminal.writer().flush()
    }

    override fun close() {
        terminal.close()
    }
}
