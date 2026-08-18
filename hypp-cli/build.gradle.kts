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

// No Main class exists yet (Phase 16 adds one) — this just proves the fat-jar
// task definition is valid and doesn't break `build`. kotlin-stdlib and hypp-jvm
// (dependency-free) are the only runtime deps, so Shadow would be overkill.
val fatJar by tasks.registering(Jar::class) {
    archiveClassifier.set("all")
    val jvmJar = tasks.named<Jar>("jvmJar")
    dependsOn(jvmJar)
    from({ zipTree(jvmJar.get().archiveFile.get()) })
    from({
        kotlin.jvm().compilations.getByName("main").runtimeDependencyFiles
            .filter { it.exists() }
            .map { if (it.isDirectory) it else zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
