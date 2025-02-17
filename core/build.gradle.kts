plugins {
    id("org.echonolix.ktgen")
}

dependencies {
    ktgen(project(":ktffi-core.codegen"))
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.contracts.ExperimentalContracts")
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}