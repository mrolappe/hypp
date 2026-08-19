import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform") version "2.4.10"
}

group = "de.rholambdapi"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    jvm()
    wasmWasi {
        binaries.executable()
        nodejs()
    }

    sourceSets {
        // commonMain (render/*.kt, Commands.kt, ArgParser.kt) references HypDocument etc.
        // directly. With a single target this resolved transitively via jvmMain's dependency
        // (Kotlin's single-target shortcut skips real common-metadata compilation), but adding
        // wasmWasi as a second target makes commonMain's own metadata compilation need its own
        // dependency on the common `hypp` coordinate — substituted to the included build in
        // settings.gradle.kts alongside hypp-jvm/hypp-wasm-wasi.
        commonMain.dependencies {
            implementation("de.rholambdapi:hypp:0.1.0-SNAPSHOT")
        }
        jvmMain.dependencies {
            implementation("de.rholambdapi:hypp-jvm:0.1.0-SNAPSHOT")
        }
        wasmWasiMain.dependencies {
            implementation("de.rholambdapi:hypp-wasm-wasi:0.1.0-SNAPSHOT")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// kotlin-stdlib and hypp-jvm (dependency-free) are the only runtime deps, so Shadow would be
// overkill for merging them into a runnable jar.
val fatJar by tasks.registering(Jar::class) {
    archiveFileName.set("hypp-cli-all.jar")
    val jvmJar = tasks.named<Jar>("jvmJar")
    dependsOn(jvmJar)
    from({ zipTree(jvmJar.get().archiveFile.get()) })
    from({
        kotlin.jvm().compilations.getByName("main").runtimeDependencyFiles
            .filter { it.exists() }
            .map { if (it.isDirectory) it else zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes("Main-Class" to "de.rholambdapi.hypp.cli.MainKt")
    }
}

tasks.register<JavaExec>("run") {
    dependsOn(fatJar)
    mainClass.set("de.rholambdapi.hypp.cli.MainKt")
    classpath = files(fatJar.get().outputs.files)
    args = (project.findProperty("args") as String?)?.split(" ") ?: emptyList()
}

// Phase 16b: the fat jar smoke test (FatJarSmokeTest.kt) runs the built jar out-of-process, so it
// needs `fatJar` to have run first — part of normal jvmTest/check, unlike the root project's
// opt-in corpusSweep.
tasks.named("jvmTest") {
    dependsOn(fatJar)
}

// Phase 17: opt-in, requires a local GraalVM with native-image on PATH — not part of
// build/check, same pattern as the root project's corpusSweep.
val nativeImageCli by tasks.registering(Exec::class) {
    dependsOn(fatJar)
    commandLine("native-image", "-jar", fatJar.get().outputs.files.singleFile.path, "hypp-cli")
}

// Phase 18: runs the built wasmWasi executable under Node end to end (dump + extract-images
// against real corpus fixtures), the same idiom as the root project's wasmJsFacadeSmokeTest —
// a hand-rolled Node WASI host script, since Kotlin's own generated loader
// (build/compileSync/.../hypp-cli.mjs) grants no filesystem preopens at all. Unlike
// nativeImageCli, this needs no extra local tooling (Node's built-in `node:wasi` module), so it's
// wired into `check`.
val wasmWasiSmokeTest by tasks.registering(Exec::class) {
    dependsOn("compileProductionExecutableKotlinWasmWasi")
    val wasmFile = layout.buildDirectory.file("compileSync/wasmWasi/main/productionExecutable/kotlin/hypp-cli.wasm")
    inputs.file(wasmFile)
    inputs.file("src/wasmWasiTest/js/cliRunner.mjs")
    inputs.dir("src/commonTest/resources/corpus")
    workingDir(projectDir)
    commandLine("node", "src/wasmWasiTest/js/cliRunner.mjs", wasmFile.get().asFile.absolutePath)
}

tasks.named("check") {
    dependsOn(wasmWasiSmokeTest)
}
