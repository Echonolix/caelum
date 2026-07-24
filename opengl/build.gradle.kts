plugins {
    id("buildsrc.convention.published-module")
    id("net.echonolix.ktgen")
}

dependencies {
    api(project(":caelum-core"))
    ktgen(project("codegen"))
    testImplementation(project(":caelum-glfw"))
    testImplementation(kotlin("test-junit5"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("opengl33Smoke") {
    group = "verification"
    description = "Runs the OpenGL 3.3 core smoke test against GLFW"
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("net.echonolix.caelum.opengl.OpenGL33GlfwSmokeKt")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty(
        "glfwDll",
        providers.gradleProperty("glfwDll").orNull
            ?: throw GradleException("Pass -PglfwDll=<absolute path to glfw3.dll>"),
    )
}

kotlin {
    explicitApi()
}
