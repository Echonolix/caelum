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

// Keep Jolt and its native resources out of the library and ordinary DirectX tests.
val joltDemos = sourceSets.create("joltDemos") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations[joltDemos.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[joltDemos.runtimeOnlyConfigurationName].extendsFrom(configurations.runtimeOnly.get())
dependencies {
    add(joltDemos.implementationConfigurationName, project(":caelum-jolt"))
}
kotlin.sourceSets.named("joltDemos") {
    kotlin.srcDir("src/test/kotlin")
    kotlin.include(
        "**/demo/jolt/**",
        "**/demo/TeapotMath.kt",
        "**/demo/TeapotMesh.kt",
        "**/demo/model/DemoMesh.kt",
        "**/demo/model/StanfordDragonMesh.kt",
        "**/demo/d3d11/Win32DemoWindow.kt",
        "**/demo/d3d12/D3D12Native.kt",
    )
}
joltDemos.resources {
    srcDir("src/test/resources")
    include("**/jolt/dxr/**", "**/model/stanford_dragon_res3.ply", "**/model/STANFORD_DRAGON_NOTICE.txt")
}

fun registerJoltDxrDemo(taskName: String, hidden: Boolean) = tasks.register<JavaExec>(taskName) {
    group = if (hidden) "verification" else "application"
    description = "Runs the DX12 hardware-raytraced Jolt kinetic museum with teapot and Stanford Dragon"
    dependsOn(joltDemos.classesTaskName)
    classpath = joltDemos.runtimeClasspath
    mainClass.set("net.echonolix.caelum.directx.demo.jolt.museum.JoltDxrMuseumKt")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty("caelum.museum.hidden", providers.gradleProperty("demoHidden").orNull ?: hidden.toString())
    systemProperty("caelum.museum.frames", providers.gradleProperty("demoFrames").orNull ?: if (hidden) "720" else "0")
    systemProperty("caelum.museum.workers", providers.gradleProperty("demoWorkers").orNull ?: "2")
    systemProperty("caelum.museum.samples", providers.gradleProperty("demoDxrSamples").orNull ?: "2")
    systemProperty("caelum.museum.output", providers.gradleProperty("demoOutput").orNull
        ?: layout.buildDirectory.dir("jolt-dxr-museum").get().asFile.absolutePath)
}
registerJoltDxrDemo("joltDxrMuseumDemo", false)
registerJoltDxrDemo("joltDxrMuseumSmoke", true)
tasks.register<JavaExec>("joltDxrContracts") {
    group = "verification"
    description = "Checks native DXR instance, atlas and SDK field-layout contracts"
    dependsOn(joltDemos.classesTaskName)
    classpath = joltDemos.runtimeClasspath
    mainClass.set("net.echonolix.caelum.directx.demo.jolt.dxr.DxrSceneContractCheck")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
tasks.register<JavaExec>("joltMuseumPhysicsSmoke") {
    group = "verification"
    description = "Checks the kinetic museum's actual contact-driven physics without creating a GPU device"
    dependsOn(joltDemos.classesTaskName)
    classpath = joltDemos.runtimeClasspath
    mainClass.set("net.echonolix.caelum.directx.demo.jolt.museum.MuseumPhysicsSmokeKt")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty("caelum.museum.workers", providers.gradleProperty("demoWorkers").orNull ?: "2")
    systemProperty("caelum.museum.output", layout.buildDirectory.dir("jolt-museum-physics").get().asFile.absolutePath)
}

fun registerJoltDemo(taskName: String, scene: String) = tasks.register<JavaExec>(taskName) {
    group = "application"
    description = "Runs the Jolt $scene scene using Caelum Direct3D 11"
    dependsOn(joltDemos.classesTaskName)
    classpath = joltDemos.runtimeClasspath
    mainClass.set("net.echonolix.caelum.directx.demo.jolt.JoltDirectXDemoKt")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty("caelum.jolt.demo.scene", scene)
    systemProperty("caelum.jolt.demo.frames", providers.gradleProperty("demoFrames").orNull ?: "0")
    systemProperty("caelum.jolt.demo.hidden", providers.gradleProperty("demoHidden").orNull ?: "false")
    systemProperty("caelum.jolt.demo.workers", providers.gradleProperty("demoWorkers").orNull ?: "0")
    systemProperty("caelum.jolt.demo.output", providers.gradleProperty("demoOutput").orNull
        ?: layout.buildDirectory.dir("jolt-demos").get().asFile.absolutePath)
}
val joltStackDemo = registerJoltDemo("joltStackDemo", "stack")
val joltBowlingDemo = registerJoltDemo("joltBowlingDemo", "bowling")
val joltKinematicDemo = registerJoltDemo("joltKinematicDemo", "kinematic")
tasks.register<JavaExec>("joltDemosSmoke") {
    group = "verification"
    description = "Runs all three hidden Jolt/Direct3D 11 scenes with physics and GPU readback assertions"
    dependsOn(joltDemos.classesTaskName)
    classpath = joltDemos.runtimeClasspath
    mainClass.set("net.echonolix.caelum.directx.demo.jolt.JoltDirectXDemoKt")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty("caelum.jolt.demo.scene", "all")
    systemProperty("caelum.jolt.demo.hidden", "true")
    systemProperty("caelum.jolt.demo.frames", providers.gradleProperty("demoFrames").orNull ?: "360")
    systemProperty("caelum.jolt.demo.workers", providers.gradleProperty("demoWorkers").orNull ?: "0")
    systemProperty("caelum.jolt.demo.output", providers.gradleProperty("demoOutput").orNull
        ?: layout.buildDirectory.dir("jolt-demos").get().asFile.absolutePath)
}

dependencies {
    extraJarEntries(rootProject.files("README.MD"))
}
