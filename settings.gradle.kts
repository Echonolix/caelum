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

(listOf(
    "core",
    "vulkan",
).flatMap {
    sequenceOf(
        "caelum-$it" to file(it),
        "caelum-$it:codegen" to file("$it/codegen")
    )
} + listOf(
    "codegen-api",
    "struct",
    "glfw",
    "glfw-vulkan"
).map {
    "caelum-$it" to file(it)
}).forEach { (name, dir) ->
    include(name)
    project(":$name").projectDir = dir
}

rootProject.name = "caelum"