includeBuild("../ktgen") {
    dependencySubstitution {
        substitute(module("net.echonolix:ktgen-api")).using(project(":api"))
        substitute(module("net.echonolix:ktgen-runtime")).using(project(":runtime"))
    }
}

pluginManagement {
    includeBuild("../ktgen")

    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.1.20"
    }
}

rootProject.name = "ktffi"

listOf(
    "ktffi-core" to file("core"),
    "ktffi-core.codegen" to file("core/codegen"),
    "ktffi-codegen-api" to file("codegen-api"),
).forEach { (name, dir) ->
    includeFlat(name)
    project(":$name").projectDir = dir
}


include(":vulkan", ":vulkan:codegen")