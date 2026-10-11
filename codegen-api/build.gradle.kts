plugins {
    id("buildsrc.convention.published-module")
    kotlin("plugin.serialization") version "2.4.21"
}

dependencies {
    api(libs.ktgenApi)
    api(libs.kotlinxSerializationCore)
}

kotlin {
    explicitApi()
}
