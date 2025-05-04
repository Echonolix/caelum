plugins {
    id("buildsrc.convention.kotlin-jvm")
    kotlin("plugin.serialization") version "2.1.0"
}

dependencies {
    api("net.echonolix:ktgen-api")
    api("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.0")
}

kotlin {
    explicitApi()
}