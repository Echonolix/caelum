allprojects {
    group = "org.echonolix"
    version = "1.0-SNAPSHOT"
    println(this.path)

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