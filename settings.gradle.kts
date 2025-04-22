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

rootProject.name = "caelum-core"

listOf(
    "caelum-core" to file("core"),
    "caelum-core-codegen" to file("core/codegen"),
    "caelum-codegen-api" to file("codegen-api"),
).forEach { (name, dir) ->
    includeFlat(name)
    project(":$name").projectDir = dir
}