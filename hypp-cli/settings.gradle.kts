rootProject.name = "hypp-cli"

includeBuild("..") {
    dependencySubstitution {
        substitute(module("de.rholambdapi:hypp-jvm")).using(project(":"))
        substitute(module("de.rholambdapi:hypp-wasm-wasi")).using(project(":"))
        substitute(module("de.rholambdapi:hypp-macosarm64")).using(project(":"))
        substitute(module("de.rholambdapi:hypp")).using(project(":"))
    }
}
