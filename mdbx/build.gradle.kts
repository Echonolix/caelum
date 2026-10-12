import buildsrc.convention.ElementType
import buildsrc.convention.NativeTarget
import buildsrc.convention.registerPinnedArchive

plugins {
    id("buildsrc.convention.codegen-c")
    id("buildsrc.convention.cross-native")
}

val sources = registerPinnedArchive(
    "mdbxSources",
    "https://github.com/Mithril-mine/libmdbx/archive/a032e40c1c06eaf695a163cfc32d4a01ffb8b26c.tar.gz",
    "48d16ea17f33ebd7143a7d60701ff8259ecd500c05561f533d9d64273a022f47",
)
val sourceRoot = sources.flatMap { it.outputDirectory.dir("libmdbx-a032e40c1c06eaf695a163cfc32d4a01ffb8b26c") }

codegenC {
    packageName.set("net.echonolix.caelum.mdbx")
    elementMapper = { type, name ->
        when (type) {
            ElementType.FUNCTION -> "MdbxFunc${name.removePrefix("mdbx_")}"
            else -> name
        }
    }
}

dependencies {
    ktgenInput(files("include/mdbx-binding.h"))
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
}

crossNative {
    libraryName.set("caelum_mdbx")
    cSources.from(sourceRoot.map { it.file("mdbx.c") }, "src/main/c/mdbx_abi.c")
    includeDirs.from(sourceRoot)
    defines.putAll(mapOf(
        "MDBX_HAVE_BUILTIN_CPU_SUPPORTS" to "0",
        "MDBX_BUILD_SHARED_LIBRARY" to "1",
        "LIBMDBX_EXPORTS" to "1",
        // Deterministic metadata, never __DATE__/__TIME__ from the build machine.
        "MDBX_BUILD_TIMESTAMP" to "\"2026-10-12T00:00:00Z\"",
        "MDBX_BUILD_FLAGS" to "\"Caelum Zig 0.17.0; C11; O2; shared; CPU dispatch disabled\"",
    ))
    targetDefines(NativeTarget.LINUX_X86_64).put("_SYS_CACHECTL_H", "1")
    linkFlags.add("-pthread")
    targetLinkFlags(NativeTarget.WINDOWS_X86_64).add("-lntdll")
}

tasks.jar {
    from(sourceRoot.map { it.file("LICENSE") }) { rename { "LICENSE-libmdbx" } }
    from(sourceRoot.map { it.file("NOTICE") }) { rename { "NOTICE-libmdbx" } }
}
tasks.named<Jar>("sourcesJar") {
    from("include")
    from("src/main/c")
    from(sourceRoot.map { it.file("mdbx.h") }) { into("upstream") }
    from(sourceRoot.map { it.file("VERSION.json") }) { into("upstream") }
    from(sourceRoot.map { it.file("LICENSE") }) { rename { "LICENSE-libmdbx" } }
    from(sourceRoot.map { it.file("NOTICE") }) { rename { "NOTICE-libmdbx" } }
    from("README.md")
}
tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
