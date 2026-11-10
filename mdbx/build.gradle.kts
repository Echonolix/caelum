import buildsrc.convention.CompileCrossNativeTask
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
val upstreamMdbx = sourceRoot.map { it.file("mdbx.c") }
val patchedMdbx = layout.buildDirectory.file("patchedMdbx/mdbx.c")
val prepareMdbx = tasks.register("prepareMdbx") {
    inputs.file(upstreamMdbx)
    outputs.file(patchedMdbx)
    doLast {
        var source = upstreamMdbx.get().asFile.readText()
        fun replaceOnce(original: String, replacement: String) {
            val start = source.indexOf(original)
            check(start >= 0 && source.indexOf(original, start + original.length) < 0) {
                "The pinned libmdbx source no longer matches the local Windows external backing patch: $original"
            }
            source = source.replaceRange(start, start + original.length, replacement)
        }
        val checkLocal = "int osal_check_fs_local(mdbx_filehandle_t handle, int flags)"
        replaceOnce("$checkLocal {\n#if IS_WINDOWS", """
            #if IS_WINDOWS
            static int osal_check_fs_local_windows(mdbx_filehandle_t handle, int flags, bool asynchronous) {
            #else
            $checkLocal {
            #endif
            #if IS_WINDOWS
        """.trimIndent())
        val query = """
            IO_STATUS_BLOCK StatusBlock;
            rc = imports.NtFsControlFile(handle, nullptr, nullptr, nullptr, &StatusBlock, FSCTL_GET_EXTERNAL_BACKING, nullptr,
                                         0, &GetExternalBacking_OutputBuffer, sizeof(GetExternalBacking_OutputBuffer));
        """.trimIndent().prependIndent("    ")
        replaceOnce(query, "#include \"mdbx_windows_backing.inc\"")
        val queryFailure = """
            } else if (rc != STATUS_OBJECT_NOT_EXTERNALLY_BACKED && rc != STATUS_INVALID_DEVICE_REQUEST &&
                       rc != STATUS_NOT_SUPPORTED)
              return osal_ntstatus2errcode(rc);
        """.trimIndent().prependIndent("    ")
        replaceOnce(queryFailure, "$queryFailure\n    if (close_error)\n      return close_error;")
        val mapEntry = "int osal_mmap(const int flags, osal_mmap_t *map, size_t size,"
        replaceOnce(mapEntry, """
            #if IS_WINDOWS
            $checkLocal {
              return osal_check_fs_local_windows(handle, flags, false);
            }
            #endif

            $mapEntry
        """.trimIndent())
        replaceOnce("  int err = osal_check_fs_local(map->fd, flags);", """
            #if IS_WINDOWS
              /* Only the lock-file map uses an overlapped handle. */
              int err = osal_check_fs_local_windows(map->fd, flags, (options & MMAP_OPTION_SEMAPHORE) != 0);
            #else
              int err = osal_check_fs_local(map->fd, flags);
            #endif
        """.trimIndent())
        val output = patchedMdbx.get().asFile
        output.parentFile.mkdirs()
        output.writeText(source)
    }
}

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
    cSources.from(upstreamMdbx, "src/main/c/mdbx_abi.c")
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

tasks.named<CompileCrossNativeTask>("compileNativeWindowsX64") {
    cSources.setFrom(files(patchedMdbx).builtBy(prepareMdbx), "src/main/c/mdbx_abi.c")
    includeDirs.from("src/main/c")
}

tasks.jar {
    from(sourceRoot.map { it.file("LICENSE") }) { rename { "LICENSE-libmdbx" } }
    from(sourceRoot.map { it.file("NOTICE") }) { rename { "NOTICE-libmdbx" } }
    from(sourceRoot.map { it.file("COPYRIGHT") }) { rename { "COPYRIGHT-libmdbx" } }
}
tasks.named<Jar>("sourcesJar") {
    from("include")
    from("src/main/c")
    from(sourceRoot.map { it.file("mdbx.h") }) { into("upstream") }
    from(sourceRoot.map { it.file("VERSION.json") }) { into("upstream") }
    from(sourceRoot.map { it.file("LICENSE") }) { rename { "LICENSE-libmdbx" } }
    from(sourceRoot.map { it.file("NOTICE") }) { rename { "NOTICE-libmdbx" } }
    from(sourceRoot.map { it.file("COPYRIGHT") }) { rename { "COPYRIGHT-libmdbx" } }
    from("README.md")
}
tasks.test {
    // /tmp may be tmpfs: libmdbx then uses its in-core path. Exercise actual
    // workspace files for lifecycle/geometry acceptance instead.
    val databaseDirectory = layout.buildDirectory.dir("test-databases")
    systemProperty("java.io.tmpdir", databaseDirectory.get().asFile.absolutePath)
    doFirst { databaseDirectory.get().asFile.mkdirs() }
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
