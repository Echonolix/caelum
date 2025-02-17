plugins {
    id("org.echonolix.ktgen")
}

dependencies {
    ktgen(project(":vulkan:codegen"))
    implementation(kotlin("reflect"))
    api(project(":ktffi-core"))
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.contracts.ExperimentalContracts")
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}