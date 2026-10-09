package buildsrc.convention

import org.gradle.language.jvm.tasks.ProcessResources

// Cross-compiles one shared library for every NativeTarget with a pinned zig and
// ISPC, and packages each binary as META-INF/caelum/native/<os>-<arch>/<file> so
// NativeLibraries in caelum-core can extract and load it at runtime.

plugins {
    `java-library`
}

val crossNative = extensions.create("crossNative", CrossNativeExtension::class.java)
crossNative.libraryName.convention("caelum_" + project.name.removePrefix("caelum-").replace('-', '_'))
crossNative.cStandard.convention("c11")
crossNative.cppStandard.convention("c++17")
crossNative.optimization.convention("-O2")
crossNative.ispcTargets.convention(listOf("sse2-i32x4", "sse4-i32x4", "avx2-i32x8", "avx512skx-x16"))
crossNative.strip.convention(true)

val toolchainService = NativeToolchainService.register(gradle)

val compileTasks = NativeTarget.values().map { target ->
    target to tasks.register("compileNative${target.taskSuffix}", CompileCrossNativeTask::class.java) {
        group = "build"
        description = "Cross-compiles the native library for ${target.id}."
        this.target.set(target)
        libraryName.set(crossNative.libraryName)
        cSources.from(crossNative.cSources)
        cppSources.from(crossNative.cppSources)
        ispcSources.from(crossNative.ispcSources)
        includeDirs.from(crossNative.includeDirs)
        defines.set(crossNative.defines.zip(crossNative.targetDefines(target)) { common, specific -> common + specific })
        cFlags.set(crossNative.cFlags)
        cppFlags.set(crossNative.cppFlags)
        ispcFlags.set(crossNative.ispcFlags)
        linkFlags.set(crossNative.linkFlags)
        cStandard.set(crossNative.cStandard)
        cppStandard.set(crossNative.cppStandard)
        optimization.set(crossNative.optimization)
        ispcTargets.set(crossNative.ispcTargets)
        strip.set(crossNative.strip)
        toolchainId.set(NativeToolchainPins.id)
        toolchains.set(toolchainService)
        zigWorkingDir.set(layout.projectDirectory)
        usesService(toolchainService)
        outputLibrary.set(
            layout.buildDirectory.dir("native/${target.id}")
                .zip(crossNative.libraryName) { dir, name -> dir.file(target.libraryFileName(name)) },
        )
        generatedHeaders.set(layout.buildDirectory.dir("native/${target.id}/include"))
    }
}

tasks.register("compileNative") {
    group = "build"
    description = "Cross-compiles the native library for every target."
    dependsOn(compileTasks.map { it.second })
}

tasks.named<ProcessResources>("processResources") {
    compileTasks.forEach { (target, task) ->
        from(task.flatMap { it.outputLibrary }) {
            into("META-INF/caelum/native/${target.id}")
        }
    }
}
