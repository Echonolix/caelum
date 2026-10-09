package net.echonolix.caelum

import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads native libraries packaged as classpath resources under
 * `META-INF/caelum/native/<os>-<arch>/<file>`, the layout produced by Caelum's
 * `cross-native` and `native-library` build conventions.
 *
 * The library is extracted to a content-addressed cache directory and loaded with
 * [System.load] from this class, which binds it to caelum-core's class loader. That
 * is the class loader [APIHelper.LOADER_LOOKUP] searches, so generated bindings
 * resolve its symbols. Load a library before the first call into its bindings.
 *
 * The cache root defaults to `<java.io.tmpdir>/caelum-native` and can be changed
 * with the `caelum.native.cacheDir` system property.
 */
public object NativeLibraries {
    /** Platform id of the running JVM, for example `windows-x86_64`. */
    public val platform: String = run {
        val osName = System.getProperty("os.name")
        val os = when {
            osName.startsWith("Windows", ignoreCase = true) -> "windows"
            osName.startsWith("Mac", ignoreCase = true) || osName.startsWith("Darwin", ignoreCase = true) -> "macos"
            osName.startsWith("Linux", ignoreCase = true) -> "linux"
            else -> osName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        }
        val arch = when (val a = System.getProperty("os.arch").lowercase()) {
            "amd64", "x86_64" -> "x86_64"
            "aarch64", "arm64" -> "aarch64"
            else -> a.replace(Regex("[^a-z0-9]+"), "-").trim('-')
        }
        "$os-$arch"
    }

    private val loaded = ConcurrentHashMap<String, Path>()

    /** Platform-specific file name of [libraryName], for example `zstd.dll` or `libzstd.so`. */
    public fun fileName(libraryName: String): String = when {
        platform.startsWith("windows-") -> "$libraryName.dll"
        platform.startsWith("macos-") -> "lib$libraryName.dylib"
        else -> "lib$libraryName.so"
    }

    /**
     * Extracts and loads [libraryName] from the resources visible to [anchor] (use a
     * class from the jar that packages the library). Loading the same name again
     * returns the path of the first load.
     */
    public fun load(anchor: Class<*>, libraryName: String): Path {
        return loaded.computeIfAbsent(libraryName) {
            val resource = "/META-INF/caelum/native/$platform/${fileName(libraryName)}"
            val bytes = anchor.getResourceAsStream(resource)?.use { it.readBytes() }
                ?: throw UnsatisfiedLinkError("Native library $libraryName is not packaged for $platform ($resource)")
            val path = extract(libraryName, bytes)
            System.load(path.toString())
            path
        }
    }

    private fun extract(libraryName: String, bytes: ByteArray): Path {
        val fileName = fileName(libraryName)
        val digest = sha256(bytes)
        val hash = digest.take(12).joinToString("") { "%02x".format(it) }
        val root = System.getProperty("caelum.native.cacheDir")?.let { Paths.get(it) }
            ?: Paths.get(System.getProperty("java.io.tmpdir"), "caelum-native")
        val directory = root.resolve("$libraryName-$hash")
        val file = directory.resolve(fileName)
        if (matches(file, digest)) return file
        Files.createDirectories(directory)
        val temp = Files.createTempFile(directory, fileName, ".tmp")
        Files.write(temp, bytes)
        try {
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            return file
        } catch (e: FileSystemException) {
            // Another process won the race, or a corrupt entry is held open (Windows).
            if (matches(file, digest)) {
                Files.deleteIfExists(temp)
                return file
            }
        }
        // Load the private copy instead of replacing an entry this process cannot fix.
        temp.toFile().deleteOnExit()
        return temp
    }

    private fun matches(file: Path, digest: ByteArray): Boolean =
        Files.isRegularFile(file) && sha256(Files.readAllBytes(file)).contentEquals(digest)

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
}
