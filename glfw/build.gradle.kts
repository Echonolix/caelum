plugins {
    id("buildsrc.convention.codegen-c")
}

codegenC {
    packageName.set("net.echonolix.caelum.glfw")
    libPrefix.set("glfw")
    excludedConsts.addAll(
        "APIENTRY",
        "WINGDIAPI",
        "CALLBACK",
        "GLFWAPI",
        "GLAPIENTRY"
    )
}

dependencies {
    ktgenInput(files(projectDir.resolve("glfw3.h")))
}