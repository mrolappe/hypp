plugins {
    kotlin("multiplatform") version "2.4.10"
}

group = "de.rholambdapi"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation("de.rholambdapi:hypp-jvm:0.1.0-SNAPSHOT")
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
