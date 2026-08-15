import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    `maven-publish`
}

group = "de.rholambdapi"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    jvm()
    wasmJs {
        nodejs()
        binaries.library()
    }
    wasmWasi {
        nodejs()
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

publishing {
    repositories {
        mavenLocal()
    }
}

// Proves the @JsExport facade (HyppJs.kt) is reachable from real, outside-Kotlin JavaScript —
// HyppJsTest.kt calls the same functions but as plain Kotlin, which bypasses the generated JS
// bindings entirely. See src/wasmJsTest/js/facadeSmokeTest.mjs.
val wasmJsFacadeSmokeTest by tasks.registering(Exec::class) {
    // Production, not development: `assemble` already builds the production library (for
    // `wasmJsJar`/publishing), and running both distributions in one invocation trips Gradle's
    // task-output validation — they share the same `build/wasm/packages/hypp/kotlin` directory.
    dependsOn("wasmJsNodeProductionLibraryDistribution")
    val moduleFile = layout.buildDirectory.file("dist/wasmJs/productionLibrary/hypp.mjs")
    inputs.file(moduleFile)
    inputs.file("src/wasmJsTest/js/facadeSmokeTest.mjs")
    commandLine("node", "src/wasmJsTest/js/facadeSmokeTest.mjs", moduleFile.get().asFile.absolutePath)
}

tasks.named("check") {
    dependsOn(wasmJsFacadeSmokeTest)
}
