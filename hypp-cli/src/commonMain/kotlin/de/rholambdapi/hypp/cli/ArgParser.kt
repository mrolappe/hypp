package de.rholambdapi.hypp.cli

sealed interface Command {
    data class Dump(val file: String, val format: String, val out: String?, val reflow: Boolean = false) : Command
    data class Validate(val file: String, val strict: Boolean) : Command
    data class Inspect(val file: String) : Command
    data class ExtractImages(val file: String, val out: String) : Command
}

class ArgParseException(message: String) : RuntimeException(message)

fun parseArgs(args: Array<String>): Command {
    if (args.isEmpty()) {
        throw ArgParseException("Missing command. Valid commands: dump, validate, inspect, extract-images")
    }

    val commandName = args[0]
    val remaining = args.drop(1).toMutableList()

    return when (commandName) {
        "dump" -> parseDump(remaining)
        "validate" -> parseValidate(remaining)
        "inspect" -> parseInspect(remaining)
        "extract-images" -> parseExtractImages(remaining)
        else -> throw ArgParseException("Unknown command: $commandName. Valid commands: dump, validate, inspect, extract-images")
    }
}

private fun parseDump(args: List<String>): Command.Dump {
    if (args.isEmpty()) {
        throw ArgParseException("dump: <file> is required")
    }

    val file = args[0]
    var format = "html"
    var out: String? = null
    var reflow = false

    var i = 1
    while (i < args.size) {
        when (args[i]) {
            "--reflow" -> reflow = true
            "--format" -> {
                i++
                if (i >= args.size) {
                    throw ArgParseException("dump: --format requires a value")
                }
                format = args[i]
                val validFormats = setOf("html", "markdown", "asciidoc", "org", "ansi", "epub")
                if (format !in validFormats) {
                    throw ArgParseException("dump: invalid format '$format'. Valid formats: html, markdown, asciidoc, org, ansi, epub")
                }
            }
            "--out" -> {
                i++
                if (i >= args.size) {
                    throw ArgParseException("dump: --out requires a value")
                }
                out = args[i]
            }
            else -> throw ArgParseException("dump: unrecognized flag '${args[i]}'")
        }
        i++
    }

    return Command.Dump(file, format, out, reflow)
}

private fun parseValidate(args: List<String>): Command.Validate {
    if (args.isEmpty()) {
        throw ArgParseException("validate: <file> is required")
    }

    val file = args[0]
    var strict = false

    var i = 1
    while (i < args.size) {
        when (args[i]) {
            "--strict" -> strict = true
            else -> throw ArgParseException("validate: unrecognized flag '${args[i]}'")
        }
        i++
    }

    return Command.Validate(file, strict)
}

private fun parseInspect(args: List<String>): Command.Inspect {
    if (args.isEmpty()) {
        throw ArgParseException("inspect: <file> is required")
    }

    if (args.size > 1) {
        throw ArgParseException("inspect: unrecognized flag '${args[1]}'")
    }

    return Command.Inspect(args[0])
}

private fun parseExtractImages(args: List<String>): Command.ExtractImages {
    if (args.isEmpty()) {
        throw ArgParseException("extract-images: <file> is required")
    }

    val file = args[0]
    var out = "."

    var i = 1
    while (i < args.size) {
        when (args[i]) {
            "--out" -> {
                i++
                if (i >= args.size) {
                    throw ArgParseException("extract-images: --out requires a value")
                }
                out = args[i]
            }
            else -> throw ArgParseException("extract-images: unrecognized flag '${args[i]}'")
        }
        i++
    }

    return Command.ExtractImages(file, out)
}
