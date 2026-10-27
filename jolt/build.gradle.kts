import buildsrc.convention.ElementType
import buildsrc.convention.NativeTarget
import buildsrc.convention.CompileCrossNativeTask
import buildsrc.convention.registerPinnedArchive

plugins {
    id("buildsrc.convention.codegen-c")
    id("buildsrc.convention.cross-native")
}

val joltCommit = "e77f175595e64cb44218cc9d9d56fc365ad0e36a"
val sources = registerPinnedArchive(
    "joltSources",
    "https://codeload.github.com/jrouwe/JoltPhysics/tar.gz/$joltCommit",
    "1f32328fb763135de10a244568d6ccb2ed9b1e6593fafe6dc6db5b2719d330bd",
)
val upstream = sources.flatMap { it.outputDirectory.dir("JoltPhysics-$joltCommit") }
val nativeSourcePaths = providers.fileContents(layout.projectDirectory.file("native-sources.txt")).asText.map { text ->
    text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
}

codegenC {
    packageName.set("net.echonolix.caelum.jolt")
    preprocessDefines.put("CJ_CODEGEN", "1")
    val opaqueNames = mapOf(
        "CJWorld" to "JoltWorldHandle", "CJShape" to "JoltShapeHandle",
        "CJTempAllocator" to "JoltTempAllocatorHandle", "CJJobSystem" to "JoltJobSystemHandle",
        "CJBodyLock" to "JoltBodyLockHandle", "CJWorldConfig" to "JoltPhysicsSystemConfig",
    )
    elementMapper = { type, name ->
        when (type) {
            ElementType.CONST -> null
            ElementType.FUNCTION -> "JoltFunc" + name.removePrefix("cj_")
            else -> opaqueNames[name] ?: if (name.startsWith("CJ")) "Jolt" + name.removePrefix("CJ") else name
        }
    }
}

dependencies {
    ktgenInput(files("include/caelum_jolt.h"))
    testImplementation(kotlin("test-junit5"))
}

sourceSets.test {
    compileClasspath = files(sourceSets.main.get().output.classesDirs, configurations.testCompileClasspath)
}
val examples = sourceSets.create("examples") {
    compileClasspath += sourceSets.main.get().output.classesDirs
    runtimeClasspath += sourceSets.main.get().output
}
// Compile the standalone consumer source locally too; actual Maven resolution
// and native execution remain independent acceptance gates.
kotlin.sourceSets.named("examples") { kotlin.srcDir("acceptance/maven-consumer/src/main/kotlin") }
configurations.named(examples.implementationConfigurationName) { extendsFrom(configurations.implementation.get()) }
configurations.named(examples.runtimeOnlyConfigurationName) { extendsFrom(configurations.runtimeOnly.get()) }
tasks.register<JavaExec>("joltHelloWorld") {
    group = "application"
    description = "Runs the headless floor/ball acceptance example (native execution)."
    classpath = examples.runtimeClasspath
    mainClass.set("net.echonolix.caelum.jolt.examples.HelloWorld")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    args(providers.gradleProperty("joltWorkers").getOrElse("0"))
}
tasks.register<JavaExec>("joltBenchmark") {
    group = "benchmark"
    description = "Runs the opt-in Jolt call/batch/update/callback benchmark; prints CSV."
    classpath = examples.runtimeClasspath
    mainClass.set("net.echonolix.caelum.jolt.examples.Benchmark")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    args(providers.gradleProperty("joltBodies").getOrElse("1000"), providers.gradleProperty("joltWorkers").getOrElse("0"))
}

crossNative {
    libraryName.set("caelum_jolt")
    // Keep local compile/iteration on the host; cross-native's portable publication
    // still requires all three targets. No native build is needed for compileKotlin.
    runtimeTargets.set(listOf(NativeTarget.host()))
    // Exactly the base source block in the pinned Jolt.cmake: no optional
    // object-stream sources, shader wrappers or compute backend translation units.
    cppSources.from(upstream.zip(nativeSourcePaths) { root, paths -> paths.map { root.file("Jolt/$it") } })
    cppSources.from(fileTree("src/main/cpp") { include("**/*.cpp") })
    includeDirs.from(upstream, "include")
    defines.putAll(mapOf(
        "JPH_DOUBLE_PRECISION" to "1", "JPH_OBJECT_LAYER_BITS" to "16",
        "JPH_CROSS_PLATFORM_DETERMINISTIC" to "1", "NDEBUG" to "1",
    ))
    cppFlags.addAll("-fvisibility=hidden", "-fno-rtti", "-fexceptions", "-ffp-contract=off")
    // Exceptions are enabled for the bridge's status boundary and all Jolt TUs.
    targetLinkFlags(NativeTarget.LINUX_X86_64).addAll("-lpthread", "-ldl")
    optimization.set("-O2")
}

tasks.jar {
    from(upstream.map { it.file("LICENSE") }) { into("META-INF/licenses"); rename { "JoltPhysics-LICENSE" } }
    from("UPSTREAM.md") { into("META-INF/licenses") }
}
tasks.named<Jar>("sourcesJar") {
    from("include") { into("include") }
    from("src/main/cpp") { into("native") }
    from("UPSTREAM.md", "SURFACE.md", "native-sources.txt", "build.gradle.kts")
    from(upstream.map { it.file("LICENSE") }) { into("META-INF/licenses"); rename { "JoltPhysics-LICENSE" } }
}

val verifyJoltSources = tasks.register("verifyJoltSources") {
    group = "verification"
    description = "Checks the pinned source manifest against upstream Jolt.cmake. Does not compile native code."
    val manifest = layout.projectDirectory.file("native-sources.txt")
    val cmake = upstream.map { it.file("Jolt/Jolt.cmake") }
    inputs.file(manifest)
    inputs.file(cmake)
    doLast {
        val source = cmake.get().asFile.readText().replace("\r\n", "\n")
        val base = source.substringAfter("set(JOLT_PHYSICS_SRC_FILES").substringBefore("\n)")
        val expected = Regex("/([^\\s)]+\\.cpp)").findAll(base).map { it.groupValues[1] }.toList()
        val actual = manifest.asFile.readLines().map(String::trim).filter(String::isNotEmpty)
        check(expected == actual) { "native-sources.txt must match the pinned base Jolt.cmake source block" }
    }
}
tasks.withType<CompileCrossNativeTask>().configureEach { dependsOn(verifyJoltSources) }
tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    // Native integration/acceptance is explicitly opt-in. Use -x processResources
    // for source-only iteration, since Kotlin test compilation can request the main jar.
    if (!providers.gradleProperty("joltNativeTests").isPresent) {
        useJUnitPlatform { excludeTags("native") }
        classpath = files(sourceSets.test.get().output, sourceSets.main.get().output.classesDirs, configurations.testRuntimeClasspath)
    }
    if (!providers.gradleProperty("joltStressTests").isPresent) useJUnitPlatform { excludeTags("stress") }
}
