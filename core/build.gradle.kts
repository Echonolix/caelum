plugins {
    id("net.echonolix.ktgen")
}

dependencies {
    ktgen(project(":caelum-core-codegen"))
    implementation(kotlin("reflect"))
}

kotlin {
    explicitApi()
    compilerOptions {
        optIn.add("kotlin.contracts.ExperimentalContracts")
        freeCompilerArgs.addAll("-Xbackend-threads=0", "-Xcontext-parameters")
    }
}