package de.rholambdapi.hypp.cli

import de.rholambdapi.hypp.HypDocument
import de.rholambdapi.hypp.OpenOutcome
import de.rholambdapi.hypp.cli.render.StoredPngEncoder
import de.rholambdapi.hypp.cli.render.defaultRenderers

private const val USAGE = "usage: hypp-cli <dump|validate|inspect|extract-images> <file> [options]"

// wasmWasi composition root — structurally the same as jvmMain's Main.kt, except:
//  - no ImageIoPngEncoder/zip (javax.imageio/java.util.zip are JVM-only): defaultRenderers()
//    takes its StoredPngEncoder default, and archiveRenderers is emptyMap() so `--format epub`
//    is absent from this target's registry (plan decision 6) — Commands.kt's dump() already
//    falls through to `renderers[format] ?: error(...)` when archiveRenderers has no match.
//  - exitProcess() doesn't exist on this target; wasmWasi's `main` return value/exception model
//    is used instead (see Io.wasmWasi.kt's WasiIoException for I/O failures, and the explicit
//    `wasiExit` proc_exit call below for command-driven exit codes).
//  - `main`'s own `args` parameter is always empty on this Kotlin version's wasmWasi target
//    (confirmed empirically, see doc/progress/phase-18-wasm-wasi.md) — `wasiCliArgs()` reads the
//    real command-line arguments straight from WASI's args_get() instead.
fun main(args: Array<String>) {
    val command = try {
        parseArgs(wasiCliArgs().toTypedArray())
    } catch (e: ArgParseException) {
        printErrorLine(e.message ?: "invalid arguments")
        printErrorLine(USAGE)
        wasiExit(2)
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
            wasiExit(1)
        }
    }
    val result = when (command) {
        is Command.Dump -> dump(
            document, command.format, command.out, defaultRenderers(), emptyMap(),
            zip = { error("epub not supported on wasmWasi") },
            reflowParagraphs = command.reflow,
        )
        is Command.Validate -> validate(document, command.strict)
        is Command.Inspect -> inspect(document)
        is Command.ExtractImages -> extractImages(document, command.out, StoredPngEncoder)
    }
    if (result.stdout.isNotEmpty()) print(result.stdout)
    if (result.stderr.isNotEmpty()) printErrorLine(result.stderr)
    for (outFile in result.files) writeBytes(outFile.path, outFile.bytes)
    wasiExit(result.exitCode)
}
