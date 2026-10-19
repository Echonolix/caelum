package buildsrc.convention

/**
 * Platforms declared by [CompileCrossNativeTask]. Zig cross-compiles C/C++; ISPC
 * object-format support depends on its host compiler. Runtime jars may select a
 * subset, while compileNative and portable publication still request every target.
 *
 * [id] matches the `META-INF/caelum/native/<os>-<arch>/` resource layout used by
 * the `native-library` convention and by `NativeLibraries` in caelum-core.
 */
enum class NativeTarget(
    val os: String,
    val arch: String,
    val zigTriple: String,
    val ispcOs: String,
    val ispcArch: String,
    val taskSuffix: String,
) {
    WINDOWS_X86_64("windows", "x86_64", "x86_64-windows-gnu", "windows", "x86-64", "WindowsX64"),

    // glibc 2.28 (2018) keeps the binary loadable on older distributions.
    LINUX_X86_64("linux", "x86_64", "x86_64-linux-gnu.2.28", "linux", "x86-64", "LinuxX64"),

    MACOS_AARCH_64("macos", "aarch64", "aarch64-macos-none", "macos", "aarch64", "MacOsAArch64");

    companion object {
        fun host(): NativeTarget {
            val arch = System.getProperty("os.arch").lowercase()
            val os = System.getProperty("os.name")
            return when (Pair(os.substringBefore(' ').lowercase(), arch)) {
                "windows" to "amd64", "windows" to "x86_64" -> WINDOWS_X86_64
                "linux" to "amd64", "linux" to "x86_64" -> LINUX_X86_64
                "mac" to "aarch64", "mac" to "arm64",
                "macos" to "aarch64", "macos" to "arm64",
                "darwin" to "aarch64", "darwin" to "arm64" -> MACOS_AARCH_64
                else -> error("Unsupported native host: $os/$arch")
            }
        }
    }

    val id: String get() = "$os-$arch"

    val isWindows: Boolean get() = os == "windows"

    fun libraryFileName(libraryName: String): String =
        when (os) {
            "windows" -> "$libraryName.dll"
            "macos" -> "lib$libraryName.dylib"
            else -> "lib$libraryName.so"
        }
}
