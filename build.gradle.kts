allprojects {
    group = "org.echonolix"
    version = "1.0-SNAPSHOT"
}

plugins {
    kotlin("jvm") version "2.1.20-Beta2"
    kotlin("plugin.serialization") version "2.1.0"
}

repositories {
    mavenCentral()
}

val vkffi = sourceSets.create("vkffi")

dependencies {
    vkffi.implementationConfigurationName(project(":ktffi-core"))
    implementation("com.squareup:kotlinpoet:2.0.0")
    implementation("io.github.pdvrieze.xmlutil:serialization:0.90.3")
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.contracts.ExperimentalContracts")
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}