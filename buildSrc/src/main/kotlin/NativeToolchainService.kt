package buildsrc.convention

import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.io.File
import java.io.Serializable
import java.net.HttpURLConnection
import java.net.URL
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/** A downloadable archive identified by URL and verified by SHA-256. */
data class PinnedArchive(val url: String, val sha256: String) : Serializable {
    val fileName: String get() = url.substringAfterLast('/')
}

/** A tool shipped in a [PinnedArchive]; the executable is found by file name inside the archive. */
data class PinnedTool(val archive: PinnedArchive, val executableName: String) : Serializable

/**
 * Pinned native toolchain versions. Bump the version, URLs and hashes together.
 * Hashes for zig come from https://ziglang.org/download/index.json; ISPC publishes
 * no hash files, so its hashes were computed from the release assets.
 */
object NativeToolchainPins {
    const val ZIG_VERSION: String = "0.17.0"
    const val ISPC_VERSION: String = "1.31.0"

    private val zigWindows = PinnedTool(
        PinnedArchive(
            "https://ziglang.org/download/0.17.0/zig-x86_64-windows-0.17.0.zip",
            "b5663f69581dcf391293fbf16c06cb80d81d806545ce618b4d0bab7f0eb8c428",
        ),
        "zig.exe",
    )
    private val zigLinux = PinnedTool(
        PinnedArchive(
            "https://ziglang.org/download/0.17.0/zig-x86_64-linux-0.17.0.tar.xz",
            "1cbe9df9f27e6b78d14ccbca43b6703a404ef79ef1c463de901d7f088d4e2026",
        ),
        "zig",
    )
    private val ispcWindows = PinnedTool(
        PinnedArchive(
            "https://github.com/ispc/ispc/releases/download/v1.31.0/ispc-v1.31.0-windows.zip",
            "9a18793800b91d5be7b851513672cd9a81a985a5a5dfec5611c2318e8ad4140a",
        ),
        "ispc.exe",
    )
    private val ispcLinux = PinnedTool(
        PinnedArchive(
            "https://github.com/ispc/ispc/releases/download/v1.31.0/ispc-v1.31.0-linux.tar.gz",
            "d74089c835e10fd7e2c4b9225ced38b87d1fb53d35c7ceabd48cdf035da11b11",
        ),
        "ispc",
    )

    /** Stable identity of the pinned toolchain, used as a task input. */
    val id: String get() = "zig-$ZIG_VERSION,ispc-$ISPC_VERSION"

    fun zig(): PinnedTool = if (hostIsWindows()) zigWindows else zigLinux
    fun ispc(): PinnedTool = if (hostIsWindows()) ispcWindows else ispcLinux

    private fun hostIsWindows(): Boolean {
        val os = System.getProperty("os.name")
        val arch = System.getProperty("os.arch").lowercase()
        if (arch != "amd64" && arch != "x86_64") {
            throw GradleException("Caelum cross-native builds need an x86_64 host, found $arch")
        }
        return when {
            os.startsWith("Windows", ignoreCase = true) -> true
            os.startsWith("Linux", ignoreCase = true) -> false
            else -> throw GradleException("Caelum cross-native builds need a Windows or Linux host, found $os")
        }
    }
}

/**
 * Downloads, verifies and extracts [PinnedArchive]s into a cache shared by all
 * builds on the machine (`<gradle user home>/caches/caelum-native`). Extraction
 * is serialized within the build and across processes with a file lock.
 */
abstract class NativeToolchainService : BuildService<NativeToolchainService.Params> {
    interface Params : BuildServiceParameters {
        val cacheDirectory: DirectoryProperty
    }

    private val logger = Logging.getLogger(NativeToolchainService::class.java)

    val cacheDirectory: File get() = parameters.cacheDirectory.get().asFile

    fun zig(): File = executable(NativeToolchainPins.zig())

    fun ispc(): File = executable(NativeToolchainPins.ispc())

    fun executable(tool: PinnedTool): File {
        val root = extract(tool.archive)
        return root.walkTopDown().maxDepth(4)
            .firstOrNull { it.isFile && it.name == tool.executableName }
            ?: throw GradleException("${tool.executableName} not found in ${tool.archive.url}")
    }

    /** Returns the directory holding the extracted contents of [archive]. */
    @Synchronized
    fun extract(archive: PinnedArchive): File {
        val key = archive.sha256.lowercase().take(16)
        val cache = cacheDirectory.also { it.mkdirs() }
        val target = File(cache, key)
        val marker = File(target, ".caelum-complete")
        if (marker.isFile) return target

        FileChannel.open(File(cache, "$key.lock").toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            channel.lock().use {
                if (marker.isFile) return target
                val download = File(cache, "$key-${archive.fileName}")
                if (!download.isFile || sha256(download) != archive.sha256.lowercase()) {
                    download(archive, download)
                }
                val staging = File(cache, "$key.staging")
                staging.deleteRecursively()
                staging.mkdirs()
                untar(download, staging)
                target.deleteRecursively()
                Files.move(staging.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
                marker.writeText(archive.url)
                download.delete()
            }
        }
        return target
    }

    private fun download(archive: PinnedArchive, destination: File) {
        logger.lifecycle("Downloading ${archive.url}")
        val partial = File(destination.path + ".part")
        val digest = MessageDigest.getInstance("SHA-256")
        val connection = URL(archive.url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 30_000
        connection.readTimeout = 120_000
        try {
            if (connection.responseCode != 200) {
                throw GradleException("Download of ${archive.url} failed with HTTP ${connection.responseCode}")
            }
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(1 shl 16)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        digest.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        val actual = digest.digest().toHex()
        if (actual != archive.sha256.lowercase()) {
            partial.delete()
            throw GradleException("SHA-256 mismatch for ${archive.url}: expected ${archive.sha256}, got $actual")
        }
        Files.move(partial.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    // Windows 10+ ships bsdtar as tar.exe, which reads .zip and .tar.xz; GNU tar
    // reads the .tar.gz/.tar.xz archives used for Linux hosts.
    private fun untar(archive: File, destination: File) {
        val process = ProcessBuilder("tar", "-xf", archive.absolutePath, "-C", destination.absolutePath)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        if (process.waitFor() != 0) {
            throw GradleException("Extracting $archive failed:\n$output")
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        fun register(gradle: Gradle): Provider<NativeToolchainService> =
            gradle.sharedServices.registerIfAbsent("caelumNativeToolchains", NativeToolchainService::class.java) {
                parameters.cacheDirectory.set(File(gradle.gradleUserHomeDir, "caches/caelum-native"))
            }
    }
}
