plugins {
    id("buildsrc.convention.codegen")
}

dependencies {
    implementation("net.echonolix:ktgen-api")
    implementation("org.ow2.asm:asm-tree:9.7")
    implementation(kotlin("reflect"))
}