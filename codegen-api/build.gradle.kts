dependencies {
    api("net.echonolix:ktgen-api")
    api("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.0")
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.contracts.ExperimentalContracts")
        freeCompilerArgs.addAll("-Xbackend-threads=0", "-Xcontext-parameters")
    }
}