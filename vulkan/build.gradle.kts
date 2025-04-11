plugins {
    id("net.echonolix.ktgen")
}

dependencies {
    ktgen(project(":vulkan:codegen"))
    implementation(kotlin("reflect"))
    api(project(":ktffi-core"))
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.contracts.ExperimentalContracts")
        freeCompilerArgs.addAll("-Xbackend-threads=0", "-Xcontext-parameters")
    }
}