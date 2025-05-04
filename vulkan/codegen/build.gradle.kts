plugins {
    id("buildsrc.convention.codegen")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(libs.kotlinxSerializationCore)
    implementation(libs.kotlinxSerializationXml)
}