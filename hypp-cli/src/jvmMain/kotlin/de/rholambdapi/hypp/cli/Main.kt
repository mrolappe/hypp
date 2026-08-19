package de.rholambdapi.hypp.cli

import de.rholambdapi.hypp.HypDocument
import de.rholambdapi.hypp.OpenOutcome
import de.rholambdapi.hypp.cli.render.ImageIoPngEncoder
import de.rholambdapi.hypp.cli.render.defaultArchiveRenderers
import de.rholambdapi.hypp.cli.render.defaultRenderers
import de.rholambdapi.hypp.cli.render.zip
import kotlin.system.exitProcess

private const val USAGE = "usage: hypp-cli <dump|validate|inspect|extract-images> <file> [options]"

/** The JVM composition root: parses args, does the file I/O, dispatches to [Commands.kt]. */
fun main(args: Array<String>) {
    val command = try {
        parseArgs(args)
    } catch (e: ArgParseException) {
        System.err.println(e.message)
        System.err.println(USAGE)
        exitProcess(2)
    }

    val file = when (command) {
        is Command.Dump -> command.file
        is Command.Validate -> command.file
        is Command.Inspect -> command.file
        is Command.ExtractImages -> command.file
    }

    val bytes = readBytes(file)
    val document = when (val outcome = HypDocument.open(bytes)) {
        is OpenOutcome.Success -> outcome.document
        is OpenOutcome.Failure -> {
            System.err.println("failed to open $file: ${outcome.reason}")
            exitProcess(1)
        }
    }

    val result = when (command) {
        is Command.Dump -> dump(
            document = document,
            format = command.format,
            out = command.out,
            renderers = defaultRenderers(ImageIoPngEncoder),
            archiveRenderers = defaultArchiveRenderers(ImageIoPngEncoder),
            zip = ::zip,
        )
        is Command.Validate -> validate(document, command.strict)
        is Command.Inspect -> inspect(document)
        is Command.ExtractImages -> extractImages(document, command.out, ImageIoPngEncoder)
    }

    if (result.stdout.isNotEmpty()) print(result.stdout)
    if (result.stderr.isNotEmpty()) System.err.print(result.stderr)
    for (file in result.files) writeBytes(file.path, file.bytes)

    exitProcess(result.exitCode)
}
