rootProject.name = "hypp-cli"

includeBuild("..") {
    dependencySubstitution {
        substitute(module("de.rholambdapi:hypp-jvm")).using(project(":"))
    }
}
