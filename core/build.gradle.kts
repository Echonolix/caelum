plugins {
    id("org.echonolix.ktgen")
}

dependencies {
    ktgen(project(":ktffi-core.codegen"))
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.contracts.ExperimentalContracts")
        freeCompilerArgs.addAll("-Xbackend-threads=0", "-Xcontext-parameters")
    }
}