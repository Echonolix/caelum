package buildsrc.convention

plugins {
    id("buildsrc.convention.published-module")
    id("net.echonolix.ktgen")
}

dependencies {
    ktgen(project(":codegen-c"))
    ktgenInput(files(projectDir.resolve("glfw3.h")))
}

val codegenCExtension = extensions.create("codegenC", CodegenCExtension::class.java)

tasks.ktgen {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty("codegenc.packageName", codegenCExtension.packageName)
}