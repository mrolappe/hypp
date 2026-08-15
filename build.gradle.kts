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
