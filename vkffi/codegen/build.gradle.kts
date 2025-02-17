plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.1.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.echonolix:ktgen-api")
    implementation(kotlin("reflect"))
    implementation("io.github.pdvrieze.xmlutil:serialization:0.90.3")
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.contracts.ExperimentalContracts")
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}