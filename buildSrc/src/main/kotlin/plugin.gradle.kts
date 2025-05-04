package buildsrc.convention

plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("org.gradle.kotlin.kotlin-dsl")
    `maven-publish`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
    withSourcesJar()
}

kotlin {
    jvmToolchain(8)
}