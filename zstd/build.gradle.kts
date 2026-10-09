import buildsrc.convention.ElementType
import buildsrc.convention.NativeTarget
import buildsrc.convention.registerPinnedArchive

plugins {
    id("buildsrc.convention.codegen-c")
    id("buildsrc.convention.cross-native")
}

// zstd release sources, verified against the hash published with the release.
val zstdSources = registerPinnedArchive(
    "zstdSources",
    "https://github.com/facebook/zstd/releases/download/v1.5.7/zstd-1.5.7.tar.gz",
    "eb33e51f49a15e023950cd7825ca74a4a2b43db8354825ac24fc1b7ee09e6fa3",
)
val zstdLib = zstdSources.flatMap { it.outputDirectory.dir("zstd-1.5.7/lib") }

// codegen-c feeds headers to clang through stdin, so the quoted include of
// zstd_errors.h cannot resolve; that header is passed as its own input instead.
// Only the stable API is bound: ZSTD_STATIC_LINKING_ONLY stays undefined.
val bindingHeaders by tasks.registering(Sync::class) {
    from(zstdLib) {
        include("zstd_errors.h", "zstd.h")
    }
    into(layout.buildDirectory.dir("generated/zstd-binding-headers"))
    filter { line: String -> if (line.trimStart().startsWith("#include \"zstd_errors.h\"")) "" else line }
}

codegenC {
    packageName.set("net.echonolix.caelum.zstd")
    preprocessDefines.put("ZSTD_DISABLE_DEPRECATE_WARNINGS", "")
    // Plain integer constants only. 64-bit and expression constants such as
    // ZSTD_CONTENTSIZE_UNKNOWN are declared by hand in Zstd.kt.
    val constants = setOf(
        "ZSTD_VERSION_MAJOR",
        "ZSTD_VERSION_MINOR",
        "ZSTD_VERSION_RELEASE",
        "ZSTD_BLOCKSIZELOG_MAX",
    )
    // Struct-by-value returns are not generated (see BINDING-AUTHORING.md).
    val excludedFunctions = setOf("ZSTD_cParam_getBounds", "ZSTD_dParam_getBounds")
    elementMapper = { type, name ->
        when (type) {
            ElementType.CONST -> name.takeIf { it in constants }
            ElementType.FUNCTION -> if (name in excludedFunctions) null else "ZstdFunc${name.removePrefix("ZSTD_")}"
            else -> name
        }
    }
}

dependencies {
    val headers = layout.buildDirectory.dir("generated/zstd-binding-headers")
    ktgenInput(files(headers.map { it.file("zstd_errors.h") }, headers.map { it.file("zstd.h") }).builtBy(bindingHeaders))
    testImplementation(kotlin("test-junit5"))
}

crossNative {
    libraryName.set("caelum_zstd")
    cSources.from(zstdLib.map { lib ->
        lib.asFileTree.matching { include("common/*.c", "compress/*.c", "decompress/*.c") }
    })
    // The x86_64 Huffman decoder is a .S file; the C fallback keeps the build C-only.
    defines.put("ZSTD_DISABLE_ASM", "1")
    // Export only ZSTDLIB_API symbols: default visibility on Linux, dllexport on
    // Windows (zstd.h gives MinGW no visibility attribute, and hidden symbols are not
    // auto-exported).
    cFlags.add("-fvisibility=hidden")
    targetDefines(NativeTarget.WINDOWS_X86_64).put("ZSTD_DLL_EXPORT", "1")
    optimization.set("-O3")
}

tasks.jar {
    from(zstdLib.map { it.file("../LICENSE") }) { rename { "LICENSE-zstd" } }
}

tasks.named<Jar>("sourcesJar") {
    from(bindingHeaders)
    from(zstdLib.map { it.file("../LICENSE") }) { rename { "LICENSE-zstd" } }
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
