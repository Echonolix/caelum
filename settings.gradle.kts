includeBuild("../ktgen") {
    dependencySubstitution {
        substitute(module("org.echonolix:ktgen-api")).using(project(":api"))
        substitute(module("org.echonolix:ktgen-runtime")).using(project(":runtime"))
    }
}

pluginManagement {
    includeBuild("../ktgen")

    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.1.20-Beta2"
    }
}

rootProject.name = "ktffi"

include(":core:codegen")

listOf(":core").forEach {
    include(it)
    project(it).name = rootProject.name + it.replace(":", "-")
}

include(":vkffi", ":vkffi:codegen")