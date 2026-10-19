package buildsrc.convention

import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.process.CommandLineArgumentProvider

/** Resolves an optional SDK location only when a native compiler is invoked. */
abstract class NativeIncludeArguments : CommandLineArgumentProvider {
    @get:Input
    @get:Optional
    abstract val includeDirectory: Property<String>

    override fun asArguments(): Iterable<String> {
        val directory = includeDirectory.orNull
            ?: throw GradleException("Vulkan headers not found; install Vulkan-Headers, set VULKAN_SDK, or pass -PvulkanIncludeDir=<include directory>")
        return listOf("-I$directory")
    }
}
