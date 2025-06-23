// The code in this file is a convention plugin - a Gradle mechanism for sharing reusable build logic.
// `buildSrc` is a Gradle-recognized directory and every plugin there will be easily available in the rest of the build.
package buildsrc.convention

import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode

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
        jvmDefault = JvmDefaultMode.NO_COMPATIBILITY
        freeCompilerArgs.addAll(
//            "-Xbackend-threads=0",
            "-Xcontext-parameters",
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