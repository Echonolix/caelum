package buildsrc.convention

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import javax.inject.Inject

/** Copies a verified [PinnedArchive] (for example upstream library sources) into the build directory. */
abstract class ExtractPinnedArchiveTask : DefaultTask() {
    @get:Input
    abstract val url: Property<String>

    @get:Input
    abstract val sha256: Property<String>

    @get:Internal
    abstract val toolchains: Property<NativeToolchainService>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @TaskAction
    fun extract() {
        val extracted = toolchains.get().extract(PinnedArchive(url.get(), sha256.get()))
        fileSystemOperations.sync {
            from(extracted) { exclude(".caelum-complete") }
            into(outputDirectory)
        }
    }
}

/** Registers an [ExtractPinnedArchiveTask] writing to `build/archives/<name>`. */
fun Project.registerPinnedArchive(name: String, url: String, sha256: String): TaskProvider<ExtractPinnedArchiveTask> {
    val service = NativeToolchainService.register(gradle)
    return tasks.register(name, ExtractPinnedArchiveTask::class.java) {
        group = "build setup"
        description = "Downloads and extracts $url."
        this.url.set(url)
        this.sha256.set(sha256)
        toolchains.set(service)
        usesService(service)
        outputDirectory.set(layout.buildDirectory.dir("archives/$name"))
    }
}
