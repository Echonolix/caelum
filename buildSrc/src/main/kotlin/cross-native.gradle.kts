package buildsrc.convention

import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.attributes.Attribute
import org.gradle.jvm.tasks.Jar

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
crossNative.runtimeTargets.convention(NativeTarget.values().toList())

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
        linkFlags.set(crossNative.linkFlags.zip(crossNative.targetLinkFlags(target)) { common, specific -> common + specific })
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

// Runtime target selection belongs to the binding, not to each consumer's task exclusions.
// Evaluate after the module has configured its extension; keep every compile target registered.
afterEvaluate {
    val runtimeTargets = crossNative.runtimeTargets.get()
    require(runtimeTargets.isNotEmpty() && runtimeTargets.distinct().size == runtimeTargets.size) {
        "crossNative.runtimeTargets must contain distinct supported targets"
    }
    tasks.named<ProcessResources>("processResources") {
        compileTasks.filter { it.first in runtimeTargets }.forEach { (target, task) ->
            from(task.flatMap { it.outputLibrary }) {
                into("META-INF/caelum/native/${target.id}")
            }
        }
    }

    if (runtimeTargets.toSet() != NativeTarget.values().toSet()) {
        val localJar = tasks.named<Jar>("jar")
        val portableJar = tasks.register<Jar>("portableJar") {
            group = "build"
            description = "Packages all native targets for portable Maven publication."
            archiveBaseName.set(localJar.flatMap { it.archiveBaseName })
            archiveVersion.set(localJar.flatMap { it.archiveVersion })
            archiveClassifier.set(localJar.flatMap { it.archiveClassifier })
            destinationDirectory.set(layout.buildDirectory.dir("portable-libs"))
            manifest.from(localJar.get().manifest)
            // Preserve classes, notices and module-specific additions, replacing only native resources.
            from(localJar.map { zipTree(it.archiveFile) }) {
                exclude("META-INF/caelum/native/**", "META-INF/MANIFEST.MF")
            }
            compileTasks.forEach { (target, task) ->
                from(task.flatMap { it.outputLibrary }) {
                    into("META-INF/caelum/native/${target.id}")
                }
            }
        }
        val javaComponent = components.getByName("java") as AdhocComponentWithVariants
        for ((name, scope) in listOf("apiElements" to "compile", "runtimeElements" to "runtime")) {
            val local = configurations.getByName(name)
            javaComponent.withVariantsFromConfiguration(local) { skip() }
            // Publication-only variants are invisible to local composite dependency resolution.
            val portable = configurations.create("portable${name.replaceFirstChar { it.uppercase() }}") {
                isCanBeConsumed = false
                isCanBeResolved = false
                extendsFrom(local)
                local.attributes.keySet().forEach { key ->
                    @Suppress("UNCHECKED_CAST")
                    val typedKey = key as Attribute<Any>
                    attributes.attribute(typedKey, local.attributes.getAttribute(typedKey)!!)
                }
                outgoing.artifact(portableJar)
            }
            javaComponent.addVariantsFromConfiguration(portable) { mapToMavenScope(scope) }
        }
    }
}
