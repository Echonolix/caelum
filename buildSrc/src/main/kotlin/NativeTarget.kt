package buildsrc.convention

/**
 * Platforms that [CompileCrossNativeTask] builds for. Every target is cross-compiled
 * with zig from any supported host, so all of them are built on every machine.
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
    LINUX_X86_64("linux", "x86_64", "x86_64-linux-gnu.2.28", "linux", "x86-64", "LinuxX64");

    val id: String get() = "$os-$arch"

    val isWindows: Boolean get() = os == "windows"

    fun libraryFileName(libraryName: String): String =
        if (isWindows) "$libraryName.dll" else "lib$libraryName.so"
}
