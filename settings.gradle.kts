pluginManagement {
    includeBuild("../ktgen")

    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.1.20"
    }
}

includeBuild("../ktgen") {
    dependencySubstitution {
        substitute(module("net.echonolix:ktgen-api")).using(project(":api"))
        substitute(module("net.echonolix:ktgen-runtime")).using(project(":runtime"))
    }
}

include(
    ":codegen-api",
    ":codegen-c",
    ":codegen-c:tree-sitter-lang-c",
    ":codegen-c:c-ast"
)

listOf(
    "core",
    "vulkan",
    "glfw",
    "vma",
    "jemalloc",
    "assimp"
).flatMap {
    sequenceOf(
        "caelum-$it" to file(it),
        "caelum-$it:codegen" to file("$it/codegen")
    )
}.forEach { (name, dir) ->
    include(name)
    project(":$name").projectDir = dir
}

rootProject.name = "caelum"