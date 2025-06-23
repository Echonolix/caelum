// The code in this file is a convention plugin - a Gradle mechanism for sharing reusable build logic.
// `buildSrc` is a Gradle-recognized directory and every plugin there will be easily available in the rest of the build.
package buildsrc.convention

plugins {
    kotlin("jvm")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(24))
    }
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.contracts.ExperimentalContracts")
        freeCompilerArgs.addAll(
//            "-Xbackend-threads=0",
            "-Xcontext-parameters",
            "-Xjvm-default=all",
            "-Xassertions=jvm",
            "-Xno-call-assertions",
            "-Xno-receiver-assertions",
            "-Xno-param-assertions",
            "-Xuse-type-table",
            "-Xuse-fast-jar-file-system",
//            "-Xuse-javac",
            "-Xcompile-java",
            "-Xjvm-default=all",
            "-Xir-inliner",
            "-Xuse-inline-scopes-numbers"
        )
    }
}