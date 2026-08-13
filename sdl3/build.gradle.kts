import java.nio.file.Path

plugins {
    id("buildsrc.convention.published-module")
    id("net.echonolix.ktgen")
}

dependencies {
    api(project(":caelum-core"))
    ktgen(project("codegen"))
    ktgenInput(files("include/SDL3"))
    testImplementation(kotlin("test-junit5"))
    extraJarEntries(files("README.md", "LICENSE.SDL.txt", "UPSTREAM.md"))
}

tasks.ktgen {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

val sdl3DllProperty = providers.gradleProperty("sdl3Dll")

tasks.register<JavaExec>("sdl3Smoke") {
    group = "verification"
    description = "Runs the SDL3 Windows smoke test against an explicit SDL3 DLL"
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("net.echonolix.caelum.sdl3.SDL3SmokeKt")
    jvmArgs("--enable-native-access=ALL-UNNAMED")

    doFirst {
        val configuredPath = sdl3DllProperty.orNull
            ?: throw GradleException("Pass -Psdl3Dll=<absolute path to SDL3.dll>")
        val dll = Path.of(configuredPath)
        if (!dll.isAbsolute) {
            throw GradleException("-Psdl3Dll must be an absolute path: $configuredPath")
        }
        val normalizedDll = dll.normalize()
        if (!normalizedDll.toFile().isFile) {
            throw GradleException("SDL3 DLL does not exist: $normalizedDll")
        }
        systemProperty("sdl3Dll", normalizedDll.toString())
    }
}

kotlin {
    explicitApi()
}
