plugins {
    id("net.echonolix.ktgen")
}

val lwjglVersion = "3.3.6"
val lwjglNatives = "natives-windows"

dependencies {
    ktgen(project("codegen"))
    implementation(kotlin("reflect"))
    api(project(":caelum-core"))

    testImplementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    testImplementation("org.lwjgl", "lwjgl", lwjglVersion)
    testImplementation("org.lwjgl", "lwjgl-glfw", lwjglVersion)
    testImplementation("org.lwjgl", "lwjgl-shaderc", lwjglVersion)
    testRuntimeOnly("org.lwjgl", "lwjgl", classifier = lwjglNatives)
    testRuntimeOnly("org.lwjgl", "lwjgl-glfw", classifier = lwjglNatives)
    testRuntimeOnly("org.lwjgl", "lwjgl-shaderc", classifier = lwjglNatives)
}

kotlin {
    explicitApi()
}