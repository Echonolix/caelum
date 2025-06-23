plugins {
    id("buildsrc.convention.published-module")
    kotlin("plugin.serialization") version "2.1.0"
}

dependencies {
    api(libs.ktgenApi)
    api(libs.kotlinxSerializationCore)
}

kotlin {
    explicitApi()
}