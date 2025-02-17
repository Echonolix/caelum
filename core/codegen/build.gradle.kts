plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.echonolix:ktgen-api")
    implementation(kotlin("reflect"))
}