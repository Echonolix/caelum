package buildsrc.convention

import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import java.io.File
import java.util.concurrent.TimeUnit

/** Finds installed Vulkan-Headers without requiring the full Vulkan SDK. */
abstract class VulkanHeadersInclude : ValueSource<String, ValueSourceParameters.None> {
    override fun obtain(): String? {
        val candidates = listOfNotNull(
            pkgConfigInclude(),
            "/opt/homebrew/include",
            "/usr/local/include",
            "/usr/include",
            "/home/linuxbrew/.linuxbrew/include",
        )
        return candidates.firstOrNull { File(it, "vulkan/vulkan.h").isFile }
    }

    private fun pkgConfigInclude(): String? = try {
        val process = ProcessBuilder("pkg-config", "--variable=includedir", "vulkan")
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            null
        } else if (process.exitValue() == 0) {
            process.inputStream.bufferedReader().use { it.readText().trim() }.takeIf(String::isNotEmpty)
        } else {
            null
        }
    } catch (_: java.io.IOException) {
        null
    }
}
