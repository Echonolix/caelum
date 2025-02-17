plugins {
    kotlin("jvm")
    id("org.echonolix.ktgen")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("reflect"))
    ktgen(project(":core:codegen"))
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.contracts.ExperimentalContracts")
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}