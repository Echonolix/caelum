package buildsrc.convention

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import javax.inject.Inject

/** Compiles one generated C++ translation unit into the host platform shared library. */
abstract class CompileNativeLibraryTask : DefaultTask() {
    @get:Input
    abstract val libraryName: Property<String>

    @get:Input
    abstract val compilerExecutable: Property<String>

    @get:Input
    abstract val cppStandard: Property<String>

    @get:Input
    abstract val apiDefines: MapProperty<String, String>

    @get:Input
    abstract val implementationDefines: MapProperty<String, String>

    /** Whether the generated header-inclusion translation unit is compiled. */
    @get:Input
    abstract val generateImplementationSource: Property<Boolean>

    @get:Input
    abstract val compileArgs: ListProperty<String>

    @get:Input
    abstract val linkArgs: ListProperty<String>

    @get:Input
    abstract val hostOperatingSystem: Property<String>

    @get:Input
    abstract val hostArchitecture: Property<String>

    /** Explicit C++ translation units, including generated C ABI shims. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /**
     * Transitive headers from SDKs and dependencies. They are inputs so a
     * change recompiles the native library, but are never auto-included.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dependencyHeaders: ConfigurableFileCollection

    /** Headers included in the generated implementation translation unit. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val headers: ConfigurableFileCollection

    /**
     * Compiler search paths. Directory identity is tracked separately from
     * content: headers that affect output must be declared in [headers] or
     * [dependencyHeaders]. Consumers may intentionally snapshot an SDK tree
     * through [dependencyHeaders] when exact transitive dependency tracking is
     * more important than snapshot cost.
     */
    @get:org.gradle.api.tasks.Internal
    abstract val includeDirs: ConfigurableFileCollection

    @get:Input
    abstract val includeDirectoryPaths: ListProperty<String>

    /** The generated implementation translation unit, exposed for consumers that need to inspect it. */
    @get:OutputFile
    abstract val generatedImplementationSource: RegularFileProperty

    /** The host shared library produced by this task. */
    @get:OutputFile
    abstract val outputLibrary: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun compile() {
        require(hostArchitecture.get() in supportedArchitectures) {
            "Native libraries currently require a 64-bit host ABI (x86_64 or aarch64); " +
                "detected ${hostArchitecture.get()}. x86 is not supported."
        }
        val orderedHeaders = headers.files
            .map(File::getAbsoluteFile)
            .sortedBy { it.invariantSeparatorsPath }
        orderedHeaders.forEach { require(it.isFile) { "Native library header does not exist: $it" } }

        val orderedSources = sources.files
            .map(File::getAbsoluteFile)
            .sortedBy { it.invariantSeparatorsPath }
        orderedSources.forEach { require(it.isFile) { "Native library C++ source does not exist: $it" } }
        val generatedSourceRequired = generateImplementationSource.get()
        require((generatedSourceRequired && orderedHeaders.isNotEmpty()) || orderedSources.isNotEmpty()) {
            "nativeLibrary must declare at least one header or C++ source"
        }

        dependencyHeaders.files.forEach { dependencyHeader ->
            require(dependencyHeader.isFile) {
                "Native library dependency header does not exist: ${dependencyHeader.absolutePath}"
            }
        }

        val orderedIncludeDirs = includeDirs.files
            .map(File::getAbsoluteFile)
            .sortedBy { it.invariantSeparatorsPath }
        orderedIncludeDirs.forEach { require(it.isDirectory) { "Native library include directory does not exist: $it" } }

        val implementationSource = generatedImplementationSource.get().asFile
        require(generatedSourceRequired || implementationDefines.get().isEmpty()) {
            "nativeLibrary.implementationDefines require nativeLibrary.generateImplementationSource=true; " +
                "place implementation macros in the explicit implementation source instead"
        }
        implementationSource.parentFile.mkdirs()
        // Always materialize the declared output. A sources-only invocation does
        // not compile this comment-only file, which keeps its implementation
        // macros out of user-provided sources while preserving Gradle output
        // tracking when the configuration changes.
        implementationSource.writeText(
            if (generatedSourceRequired) renderImplementation(orderedHeaders)
            else "// Header inclusion TU disabled; explicit nativeLibrary.sources own implementation.\n",
            StandardCharsets.UTF_8,
        )

        val output = outputLibrary.get().asFile
        output.parentFile.mkdirs()
        Files.deleteIfExists(output.toPath())

        val command = compilerCommand(
            sources = buildList {
                if (generatedSourceRequired) add(implementationSource)
                addAll(orderedSources)
            },
            output = output,
            includeDirectories = orderedIncludeDirs,
        )
        val result = execOperations.exec {
            commandLine(command)
            isIgnoreExitValue = true
        }
        if (result.exitValue != 0) {
            throw GradleException(
                "Native library compiler exited with ${result.exitValue}: " + command.joinToString(" "),
            )
        }
        if (!output.isFile) {
            throw GradleException("Native library compiler completed without producing expected output: $output")
        }
    }

    private fun renderImplementation(orderedHeaders: List<File>): String = buildString {
        appendLine("// Generated by CompileNativeLibraryTask. Do not edit.")
        appendLine("// This file is intentionally deterministic: defines and headers are sorted.")
        implementationDefines.get().toSortedMap().forEach { (name, value) ->
            require(name.isNotBlank()) { "Native implementation define names must not be blank" }
            append("#define ").append(name)
            if (value.isNotBlank()) append(' ').append(value)
            appendLine()
        }
        orderedHeaders.forEach { header ->
            append("#include \"")
                .append(header.invariantSeparatorsPath.replace("\\", "\\\\").replace("\"", "\\\""))
                .appendLine("\"")
        }
    }

    private fun compilerCommand(
        sources: List<File>,
        output: File,
        includeDirectories: List<File>,
    ): List<String> {
        val executable = compilerExecutable.get()
        val isMsvc = executable.substringAfterLast('/').substringAfterLast('\\')
            .lowercase() in setOf("cl", "cl.exe")
        val api = apiDefines.get().toSortedMap().also { defines ->
            require(defines.keys.none(String::isBlank)) { "Native API define names must not be blank" }
        }
        return if (isMsvc) {
            buildList {
                add(executable)
                add("/nologo")
                add("/LD")
                standardFlag(msvc = true)?.let(::add)
                api.forEach { (name, value) -> add("/D$name${if (value.isBlank()) "" else "=$value"}") }
                includeDirectories.forEach { add("/I${it.absolutePath}") }
                addAll(compileArgs.get())
                sources.forEach { source -> add(source.absolutePath) }
                add("/Fe${output.absolutePath}")
                if (linkArgs.get().isNotEmpty()) {
                    add("/link")
                    addAll(linkArgs.get())
                }
            }
        } else {
            buildList {
                add(executable)
                standardFlag(msvc = false)?.let(::add)
                when (hostOperatingSystem.get()) {
                    "macos" -> {
                        add("-fPIC")
                        add("-dynamiclib")
                    }
                    "windows" -> add("-shared")
                    else -> {
                        add("-fPIC")
                        add("-shared")
                    }
                }
                api.forEach { (name, value) -> add("-D$name${if (value.isBlank()) "" else "=$value"}") }
                includeDirectories.forEach { add("-I${it.absolutePath}") }
                addAll(compileArgs.get())
                sources.forEach { source -> add(source.absolutePath) }
                add("-o")
                add(output.absolutePath)
                addAll(linkArgs.get())
            }
        }
    }

    private fun standardFlag(msvc: Boolean): String? {
        val standard = cppStandard.orNull?.trim().orEmpty()
        if (standard.isEmpty()) return null
        return if (msvc) {
            when {
                standard.startsWith("/std:") -> standard
                standard.startsWith("std:") -> "/$standard"
                else -> "/std:$standard"
            }
        } else {
            if (standard.startsWith("-std=")) standard else "-std=$standard"
        }
    }

    private companion object {
        val supportedArchitectures = setOf("x86_64", "aarch64")
    }
}
