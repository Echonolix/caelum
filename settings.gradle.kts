dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.endoqa.io")
    }
}

pluginManagement {
    includeBuild("../ktgen")

    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.1.20"
    }
}

includeBuild("../ktgen")

include(
    ":codegen-c",
    ":codegen-c:tree-sitter-lang-c",
    ":codegen-c:c-ast",
    ":codegen-c:adapter"
)

listOf(
    "codegen-api",
    "struct",
    "core",
    "vulkan",
    "glfw",
    "glfw-vulkan"
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