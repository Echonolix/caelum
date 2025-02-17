allprojects {
    group = "org.echonolix"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

plugins {
    kotlin("jvm") apply false
    id("org.echonolix.ktgen") apply false
}

subprojects {
    apply {
        plugin("kotlin")
    }
}