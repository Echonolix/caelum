import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("plugin.serialization") version "2.1.0"
}

dependencies {
    implementation(project(":ktffi-codegen-api"))
    implementation(project(":ktffi-core"))
    implementation("io.github.pdvrieze.xmlutil:serialization:0.90.3")
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.contracts.ExperimentalContracts")
        freeCompilerArgs.addAll("-Xbackend-threads=0", "-Xcontext-parameters")
    }
}