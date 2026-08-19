// Proves the wasmWasi executable's file I/O (Io.wasmWasi.kt's hand-rolled WASI bindings) actually
// works end to end under Node — not just that it compiles. Kotlin's own generated loader
// (build/.../hypp-cli.mjs) constructs `new WASI({ args: argv, env })` with NO `preopens`, so it
// grants no filesystem access at all; this script is a from-scratch WASI host (same shape as
// Node's own docs) that preopens the CLI's project root at guest path "." so relative paths in
// the CLI's own arguments resolve, matching what Io.wasmWasi.kt's `findPreopenedDirFd()` expects.
// Run via the `wasmWasiSmokeTest` Gradle task, which passes the built module's absolute path as
// argv[2] and runs this with cwd already set to the hypp-cli project root.
import { WASI } from "node:wasi";
import { readFileSync, mkdirSync, existsSync } from "node:fs";

const wasmPath = process.argv[2];
if (!wasmPath) {
  console.error("usage: node cliRunner.mjs <path-to-hypp-cli.wasm>");
  process.exit(1);
}

const FIXTURE = "src/commonTest/resources/corpus/textattr.hyp";
const OUT_DIR = "build/wasmWasiSmokeTest";
mkdirSync(OUT_DIR, { recursive: true });

const wasmBuffer = readFileSync(wasmPath);
const wasmModule = new WebAssembly.Module(wasmBuffer);

// argv[0] is the WASI convention "program name" that Kotlin/Wasm's entry point drops before
// handing the rest to `fun main(args: Array<String>)` — verified empirically by this script:
// if that assumption were wrong, `cliArgs[0]` below would be seen as "hypp-cli", not "dump"/
// "extract-images", and ArgParser would reject it as an unknown command.
function run(cliArgs) {
  const wasi = new WASI({
    version: "preview1",
    args: ["hypp-cli", ...cliArgs],
    env: process.env,
    preopens: { ".": process.cwd() },
  });
  const instance = new WebAssembly.Instance(wasmModule, wasi.getImportObject());
  const exitCode = wasi.start(instance);
  return { exitCode };
}

function assertTrue(cond, what) {
  if (!cond) {
    console.error(`FAIL: ${what}`);
    process.exit(1);
  }
}

// 1. dump --format html, written to a file (exercises readBytes + writeBytes).
const htmlOut = `${OUT_DIR}/dump.html`;
const dumpResult = run(["dump", FIXTURE, "--format", "html", "--out", htmlOut]);
assertTrue(dumpResult.exitCode === 0, `dump exited ${dumpResult.exitCode}`);
assertTrue(existsSync(htmlOut), `${htmlOut} was not written`);
const html = readFileSync(htmlOut, "utf8");
assertTrue(html.includes("<!doctype html>"), `expected HTML-looking output, got:\n${html.slice(0, 200)}`);

// 2. extract-images, written under OUT_DIR (a second writeBytes path, and re-exercises the
// path-traversal sanitization already covered by CommandsTest.kt on the commonMain side).
const imagesOut = `${OUT_DIR}/images`;
mkdirSync(imagesOut, { recursive: true });
const extractResult = run([
  "extract-images",
  "src/commonTest/resources/corpus/st-guide_orig_en.hyp",
  "--out",
  imagesOut,
]);
assertTrue(extractResult.exitCode === 0, `extract-images exited ${extractResult.exitCode}`);

console.log("cliRunner: OK");
