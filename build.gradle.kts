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
    macosArm64()

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

// Phase 11 (wild sweep, doc/PLAN.md): opt-in, network — not part of build/check.
// Downloads the full public .hyp corpus and runs it through HypDocument.open(); see
// src/jvmTest/kotlin/de/rholambdapi/hypp/CorpusSweep.kt.
val corpusSweep by tasks.registering(JavaExec::class) {
    dependsOn("jvmTestClasses")
    val jvmTest = kotlin.jvm().compilations.getByName("test")
    classpath = jvmTest.output.allOutputs + jvmTest.runtimeDependencyFiles
    mainClass.set("de.rholambdapi.hypp.CorpusSweepKt")
    args(layout.projectDirectory.dir("build/corpusSweep/cache").asFile.absolutePath)
}
