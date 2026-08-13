package buildsrc.convention

import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    `java-library`
}

val nativeLibrary = extensions.create("nativeLibrary", NativeLibraryExtension::class.java)
// Resolve host identity during configuration instead of storing Kotlin-script
// mapping lambdas in task properties; the latter is not configuration-cache
// serializable in precompiled script plugins.
val hostOs = hostOperatingSystem(System.getProperty("os.name"))
val hostArch = hostArchitecture(System.getProperty("os.arch"))

nativeLibrary.libraryName.convention(project.name)
nativeLibrary.compilerExecutable.convention(
    providers.environmentVariable("CXX")
        .orElse(if (hostOs == "windows") "clang++" else "c++"),
)
nativeLibrary.cppStandard.convention("c++17")
nativeLibrary.outputDirectory.convention(layout.buildDirectory.dir("native/${project.name}"))
nativeLibrary.generatedImplementationSource.convention(
    layout.buildDirectory.file("generated/native/${project.name}/${project.name}-implementation.cpp"),
)
nativeLibrary.outputLibrary.convention(
    nativeLibrary.outputDirectory.file(
        nativeLibrary.libraryName.map { name -> sharedLibraryFileName(name, hostOs) },
    ),
)

val compileNativeLibrary = tasks.register("compileNativeLibrary", CompileNativeLibraryTask::class.java) {
    group = "build"
    description = "Generates and compiles the host native shared library."
    libraryName.set(nativeLibrary.libraryName)
    compilerExecutable.set(nativeLibrary.compilerExecutable)
    cppStandard.set(nativeLibrary.cppStandard)
    apiDefines.set(nativeLibrary.apiDefines)
    implementationDefines.set(nativeLibrary.implementationDefines)
    generateImplementationSource.set(nativeLibrary.generateImplementationSource)
    compileArgs.set(nativeLibrary.compileArgs)
    linkArgs.set(nativeLibrary.linkArgs)
    hostOperatingSystem.set(hostOs)
    hostArchitecture.set(hostArch)
    sources.from(nativeLibrary.sources)
    dependencyHeaders.from(nativeLibrary.dependencyHeaders)
    headers.from(nativeLibrary.headers)
    includeDirs.from(nativeLibrary.includeDirs)
    includeDirectoryPaths.set(
        nativeLibrary.includeDirs.files
            .map { directory -> directory.absoluteFile.invariantSeparatorsPath }
            .sorted(),
    )
    generatedImplementationSource.set(nativeLibrary.generatedImplementationSource)
    outputLibrary.set(nativeLibrary.outputLibrary)
}

tasks.named<ProcessResources>("processResources") {
    from(compileNativeLibrary.flatMap { it.outputLibrary }) {
        into(
            "META-INF/caelum/native/$hostOs-$hostArch",
        )
    }
}

fun hostOperatingSystem(name: String): String = when {
    name.startsWith("Windows", ignoreCase = true) -> "windows"
    name.startsWith("Mac", ignoreCase = true) || name.startsWith("Darwin", ignoreCase = true) -> "macos"
    name.startsWith("Linux", ignoreCase = true) -> "linux"
    else -> name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
}

fun hostArchitecture(name: String): String = when (name.lowercase()) {
    "amd64", "x86_64" -> "x86_64"
    "aarch64", "arm64" -> "aarch64"
    "x86", "i386", "i486", "i586", "i686" -> "x86"
    else -> name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
}

fun sharedLibraryFileName(name: String, operatingSystem: String): String = when (operatingSystem) {
    "windows" -> "$name.dll"
    "macos" -> "lib$name.dylib"
    else -> "lib$name.so"
}
