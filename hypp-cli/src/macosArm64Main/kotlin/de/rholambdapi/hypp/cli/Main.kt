package de.rholambdapi.hypp.cli

import de.rholambdapi.hypp.HypDocument
import de.rholambdapi.hypp.OpenOutcome
import de.rholambdapi.hypp.cli.render.StoredPngEncoder
import de.rholambdapi.hypp.cli.render.defaultRenderers
import kotlin.system.exitProcess

private const val USAGE = "usage: hypp-cli <dump|validate|inspect|extract-images> <file> [options]"

// macosArm64 composition root — structurally identical to jvmMain's Main.kt, except:
//  - no ImageIoPngEncoder/zip (javax.imageio/java.util.zip are JVM-only): defaultRenderers()
//    takes its StoredPngEncoder default, and archiveRenderers is emptyMap() so `--format epub`
//    is absent (plan decision 6), same as wasmWasi.
//  - unlike wasmWasi, this target needs none of its workarounds: Kotlin/Native's `main(args)`
//    is populated with real argv (confirmed empirically, see doc/progress/phase-19-macos-arm64.md)
//    and `kotlin.system.exitProcess` works normally, same as jvmMain.
fun main(args: Array<String>) {
    val command = try {
        parseArgs(args)
    } catch (e: ArgParseException) {
        printErrorLine(e.message ?: "invalid arguments")
        printErrorLine(USAGE)
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
            printErrorLine("failed to open $file: ${outcome.reason}")
            exitProcess(1)
        }
    }

    val result = when (command) {
        is Command.Dump -> dump(
            document = document,
            format = command.format,
            out = command.out,
            renderers = defaultRenderers(),
            archiveRenderers = emptyMap(),
            zip = { error("epub not supported on macosArm64") },
        )
        is Command.Validate -> validate(document, command.strict)
        is Command.Inspect -> inspect(document)
        is Command.ExtractImages -> extractImages(document, command.out, StoredPngEncoder)
    }

    if (result.stdout.isNotEmpty()) print(result.stdout)
    if (result.stderr.isNotEmpty()) printError(result.stderr)
    for (outFile in result.files) writeBytes(outFile.path, outFile.bytes)

    exitProcess(result.exitCode)
}
