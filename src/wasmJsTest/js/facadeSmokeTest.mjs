// Proves HyppJs.kt's @JsExport surface is actually reachable from real, outside-Kotlin
// JavaScript — the concern HyppJsTest.kt (plain Kotlin calls into the same functions) cannot
// cover, since Kotlin-to-Kotlin calls bypass the generated JS bindings entirely. Run via the
// `wasmJsFacadeSmokeTest` Gradle task, which passes the built module's path as argv[2].
//
// textattr.hyp — same bytes as TestCorpus.textattr / HyppJsTest's copy.
const TEXTATTR_HYP_B64 =
  "SERPQwAAACIAAgMCFAAAAABuAK4AAAAAAABNYWluAAAO/wAAAOUAAAAAAAAAAAAeAAhhdGFyaXN0AAAEABYtZDEyICtnIC1pIC1zIC10NCAregAAAAgABktlaW5zAAAKAAIAAAALAAJLAAAAAAAAclJyrdUUeA2g2U8AWYsFh03/ZtHfqF1yMh4z6EioihmYAAWpuUWJbQTdH1Ooct47Rt2eDyogrg8tPqRQRGU1u2EWh1mgbzhFjpV4ZaR6lxC1DG1Dbl7kCI7zTG78mcR36+Tnd7L1z5qL6ubaD/7jvaaHEKH9PA==";

const modulePath = process.argv[2];
if (!modulePath) {
  console.error("usage: node facadeSmokeTest.mjs <path-to-hypp.mjs>");
  process.exit(1);
}

const { hyppOpen, hyppNodeLineCount, hyppLineSpanCount, hyppSpanText, hyppSpanLinkKind } =
  await import(modulePath);

function assertEquals(expected, actual, what) {
  if (expected !== actual) {
    console.error(`FAIL: ${what} — expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
    process.exit(1);
  }
}

const handle = hyppOpen(TEXTATTR_HYP_B64);
assertEquals(9, hyppNodeLineCount(handle, 0), "line count");

// Reconstruct line 1's plain text from the flattened spans, the way a real JS consumer would.
const spanCount = hyppLineSpanCount(handle, 0, 1);
let text = "";
for (let i = 0; i < spanCount; i++) {
  assertEquals(-1, hyppSpanLinkKind(handle, 0, 1, i), `span ${i} has no link`);
  text += hyppSpanText(handle, 0, 1, i);
}
assertEquals("Dies ist heller Text.", text, "line 1 reconstructed from spans");

console.log("facadeSmokeTest: OK");
