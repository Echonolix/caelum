plugins {
    id("buildsrc.convention.published-module")
    id("net.echonolix.ktgen")
}

dependencies {
    ktgen(project("codegen"))
}

kotlin {
    explicitApi()
}

dependencies {
    extraJarEntries(rootProject.files("README.MD"))
}