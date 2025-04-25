plugins {
    kotlin("plugin.serialization") version "2.1.0"
}

dependencies {
    implementation(project(":codegen-api"))
    implementation(project(":caelum-core"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.0")
    implementation("io.github.pdvrieze.xmlutil:serialization:0.90.3")
}