import buildsrc.convention.ElementType
import buildsrc.convention.NativeTarget
import buildsrc.convention.registerPinnedArchive

plugins {
    id("buildsrc.convention.codegen-c")
    id("buildsrc.convention.cross-native")
}

val sources = registerPinnedArchive(
    "mimallocSources",
    "https://codeload.github.com/microsoft/mimalloc/tar.gz/refs/tags/v2.5.2",
    "5cf9d2a10fae5551b764e888a623da411c6c6beb0221b847f83492ab1932bdab",
)
val upstream = sources.flatMap { it.outputDirectory.dir("mimalloc-2.5.2") }

codegenC {
    packageName.set("net.echonolix.caelum.mimalloc")
    elementMapper = { type, name ->
        when (type) {
            ElementType.CONST -> null
            ElementType.FUNCTION -> "MimallocFunc${name.removePrefix("mi_")}"
            else -> name
        }
    }
}

dependencies {
    ktgenInput(files("include/mimalloc-api.h"))
    testImplementation(kotlin("test-junit5"))
}

crossNative {
    libraryName.set("caelum_mimalloc")
    cSources.from(upstream.map { it.file("src/static.c") })
    cSources.from("src/main/c/mimalloc-abi-check.c")
    includeDirs.from(upstream.map { it.dir("include") })
    defines.putAll(mapOf(
        "MI_SHARED_LIB" to "1", "MI_SHARED_LIB_EXPORT" to "1",
        "MI_WIN_NOREDIRECT" to "1", "MI_DEBUG" to "0", "NDEBUG" to "1",
        "MI_STATS" to "1",
    ))
    // MI_MALLOC_OVERRIDE deliberately stays undefined: only explicit mi_* calls use this allocator.
    cFlags.add("-fvisibility=hidden")
    // Snapshot payloads are much smaller than a native segment. Avoid committing an entire
    // segment per worker on Windows; mimalloc still owns pages, caching, and remote frees.
    targetDefines(NativeTarget.WINDOWS_X86_64).put("MI_DEFAULT_EAGER_COMMIT", "0")
    targetLinkFlags(NativeTarget.WINDOWS_X86_64).addAll("-lpsapi", "-lshell32", "-luser32", "-ladvapi32", "-lbcrypt")
    targetLinkFlags(NativeTarget.LINUX_X86_64).addAll("-lpthread", "-ldl")
    optimization.set("-O3")
}

tasks.jar {
    from(upstream.map { it.file("LICENSE") }) { rename { "LICENSE-mimalloc" } }
}
tasks.named<Jar>("sourcesJar") {
    from("include")
    from("src/main/c")
    from(upstream.map { it.file("include/mimalloc.h") }) { into("upstream") }
    from(upstream.map { it.file("LICENSE") }) { rename { "LICENSE-mimalloc" } }
}
val nativeTestClasspath = sourceSets.test.get().runtimeClasspath
tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    doFirst { systemProperty("mimalloc.test.runtimeClasspath", nativeTestClasspath.asPath) }
}
