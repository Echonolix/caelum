plugins {
    id("net.echonolix.ktgen")
}

dependencies {
    ktgen(project(":codegen-c"))
    ktgenInput(files(projectDir.resolve("glfw3.h")))
}

tasks.ktgen {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}