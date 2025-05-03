import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

allprojects {
    group = "net.echonolix"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven("https://maven.endoqa.io")
    }
}

plugins {
    kotlin("jvm") apply false
    id("net.echonolix.ktgen") apply false
}

subprojects {
    apply {
        plugin("kotlin")
    }

    configure<JavaPluginExtension> {
        withSourcesJar()
    }

    configure<KotlinJvmProjectExtension> {
        compilerOptions {
            optIn.add("kotlin.contracts.ExperimentalContracts")
            freeCompilerArgs.addAll("-Xbackend-threads=0", "-Xcontext-parameters")
        }
    }
}