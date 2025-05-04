plugins {
    id("buildsrc.convention.codegen-c")
}

codegenC {
    packageName.set("net.echonolix.caelum.glfw")
    excludedConsts.addAll(
        "APIENTRY",
        "WINGDIAPI",
        "CALLBACK",
        "GLFWAPI",
        "GLAPIENTRY"
    )
}