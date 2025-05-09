plugins {
    id("buildsrc.convention.published-module")
    kotlin("plugin.serialization") version "2.1.0"
}

dependencies {
    api("net.echonolix:ktgen-api:1.0.0")
    api("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.0")
}

kotlin {
    explicitApi()
}