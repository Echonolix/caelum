plugins {
    id("buildsrc.convention.codegen")
    id("buildsrc.convention.published-module")
    `java-gradle-plugin`
}

apply {
    plugin("org.gradle.kotlin.kotlin-dsl")
}

dependencies {
    implementation(libs.ktgen)
    implementation(libs.ktgenApi)
    implementation(libs.asmTree)
    implementation(kotlin("reflect"))
}

gradlePlugin {
    plugins {
        create("caelum-struct") {
            id = "net.echonolix.caelum-struct"
            implementationClass = "net.echonolix.caelum.struct.StructCodegenPlugin"
            displayName = "caelum-struct"
        }
    }
}