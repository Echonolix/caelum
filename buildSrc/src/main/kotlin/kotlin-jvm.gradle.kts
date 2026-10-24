// The code in this file is a convention plugin - a Gradle mechanism for sharing reusable build logic.
// `buildSrc` is a Gradle-recognized directory and every plugin there will be easily available in the rest of the build.
package buildsrc.convention

import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode

plugins {
    kotlin("jvm")
}

java {
    toolchain {
        languageVersion.set(
            JavaLanguageVersion.of(
                providers.gradleProperty("caelumJavaVersion").getOrElse("24").toInt()
            )
        )
    }
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.contracts.ExperimentalContracts")
        jvmDefault = JvmDefaultMode.NO_COMPATIBILITY
        freeCompilerArgs.addAll(
//            "-Xbackend-threads=0",
            "-Xassertions=jvm",
            "-Xno-call-assertions",
            "-Xno-receiver-assertions",
            "-Xno-param-assertions",
            "-Xuse-type-table",
            "-Xuse-fast-jar-file-system",
//            "-Xuse-javac",
            "-Xcompile-java",
            "-Xir-inliner",
            "-Xuse-inline-scopes-numbers"
        )
    }
}

dependencies {
    implementation("org.jetbrains:annotations:26.0.2")
}

// Generators must use the same JDK as their processor classes, even when the
// Gradle daemon runs on an older JDK.
tasks.withType<JavaExec>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
}
