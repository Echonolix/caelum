plugins {
    id("net.echonolix.ktgen")
}

dependencies {
    ktgen(project("codegen"))
}

kotlin {
    explicitApi()
}