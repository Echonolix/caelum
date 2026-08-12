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

val glfwDll = providers.gradleProperty("glfwDll")

tasks.register<JavaExec>("opengl33Smoke") {
    group = "verification"
    description = "Runs the OpenGL 3.3 core smoke test against GLFW"
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("net.echonolix.caelum.opengl.OpenGL33GlfwSmokeKt")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty("glfwDll", glfwDll.orNull ?: "")
}

tasks.register<JavaExec>("openglTeapotDemo") {
    group = "application"
    description = "Runs the lit, rotating teapot demo with Caelum OpenGL and Caelum GLFW"
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("net.echonolix.caelum.opengl.demo.CaelumTeapotDemoKt")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty("glfwDll", glfwDll.orNull ?: "")
    systemProperty("caelum.demo.seconds", providers.gradleProperty("demoSeconds").orNull ?: "0")
    systemProperty("caelum.demo.hidden", providers.gradleProperty("demoHidden").orNull ?: "false")
}

kotlin {
    explicitApi()
}
