plugins {
    id("buildsrc.convention.published-module")
}

dependencies {
    api(project(":caelum-core"))
    api(project(":caelum-dxgi"))
    testImplementation(kotlin("test-junit5"))
}

kotlin {
    explicitApi()
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

val directxDragonDemo = tasks.register<JavaExec>("directxDragonDemo") {
    group = "application"
    description = "Runs the native Direct3D 12 Stanford Dragon demo (optionally exercising hardware DXR)"
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("net.echonolix.caelum.directx.demo.d3d12.CaelumDirectX12TeapotDemoKt")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty("caelum.demo.seconds", providers.gradleProperty("demoSeconds").orNull ?: "0")
    systemProperty("caelum.demo.hidden", providers.gradleProperty("demoHidden").orNull ?: "false")
    systemProperty("caelum.demo.dxr", providers.gradleProperty("demoDxr").orNull ?: "false")
    systemProperty("caelum.demo.dxr.samples", providers.gradleProperty("demoDxrSamples").orNull ?: "8")
    systemProperty("caelum.directx.version", providers.gradleProperty("directxVersion").orNull ?: "12")
}

tasks.register("directxTeapotDemo") {
    group = "application"
    description = "Compatibility alias for directxDragonDemo"
    dependsOn(directxDragonDemo)
}

dependencies {
    extraJarEntries(rootProject.files("README.MD"))
}
