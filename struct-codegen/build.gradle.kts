plugins {
    id("buildsrc.convention.codegen")
    id("buildsrc.convention.published-module")
    `java-gradle-plugin`
}

apply {
    plugin("org.gradle.kotlin.kotlin-dsl")
}

dependencies {
    implementation("net.echonolix:ktgen-api")
    implementation("org.ow2.asm:asm-tree:9.7")
    implementation(kotlin("reflect"))
}

gradlePlugin {
    plugins {
        create("caelum-struct-codegen") {
            id = "net.echonolix.caelum"
            implementationClass = "net.echonolix.caelum.struct.StructCodegenPlugin"
            displayName = "caelum-struct-codegen"
        }
    }
}