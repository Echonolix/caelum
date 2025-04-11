allprojects {
    group = "net.echonolix"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
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
}