import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Exec
import org.gradle.jvm.tasks.Jar

plugins {
    id("buildsrc.convention.published-module")
    id("buildsrc.convention.native-library")
}

kotlin {
    explicitApi()
}

val vulkanInclude = providers.gradleProperty("vulkanIncludeDir").orElse(
    providers.environmentVariable("VULKAN_SDK").map { "$it/include" }
)

nativeLibrary {
    libraryName.set("caelum_vma")
    headers.from(layout.projectDirectory.file("src/main/headers/vk_mem_alloc.h"))
    includeDirs.from(vulkanInclude.map(::file))
    // VMA includes Vulkan-Headers transitively. Track each consumed SDK header
    // as an input without making it a top-level include in the generated TU.
    dependencyHeaders.from(vulkanInclude.map { includeDirectory ->
        fileTree(includeDirectory) { include("**/*.h") }
    })
    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        apiDefines.put("VMA_CALL_PRE", "__declspec(dllexport)")
    }
    // vk_mem_alloc.h derives this macro from Vulkan-Headers when it is absent.
    // The Kotlin VmaAllocatorCreateInfo layout includes the corresponding final
    // pTypeExternalMemoryHandleTypes field, so make the compiled ABI explicit
    // and independent of an individual Vulkan-Headers feature macro set.
    apiDefines.put("VMA_EXTERNAL_MEMORY", "1")
    implementationDefines.put("VMA_STATIC_VULKAN_FUNCTIONS", "0")
    implementationDefines.put("VMA_DYNAMIC_VULKAN_FUNCTIONS", "1")
    implementationDefines.put("VMA_IMPLEMENTATION", "")
    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        compilerExecutable.set("clang++")
        compileArgs.add("-w")
    }
}

publishing {
    publications.named<MavenPublication>(project.name) {
        pom {
            licenses {
                license {
                    name.set("MIT License (Vulkan Memory Allocator 3.4.0 component)")
                    url.set("https://github.com/GPUOpen-LibrariesAndSDKs/VulkanMemoryAllocator/blob/3aa921224c154a0d2c43912bc88e1c42ce1f7607/LICENSE.txt")
                    distribution.set("repo")
                }
            }
        }
    }
}

// Compile the pinned-header static-assert probe with exactly the feature macro
// used by the native library. This guards the FFM structure layout against a
// future Vulkan-Headers change that would otherwise toggle VMA's conditional
// external-memory field.
val verifyVmaAbi by tasks.registering(Exec::class) {
    group = "verification"
    description = "Compiles VMA's pinned-header ABI static assertions."
    val headerDirectory = layout.projectDirectory.dir("src/main/headers").asFile
    val probe = layout.projectDirectory.file("src/test/native/VmaAllocatorAbiProbe.cpp").asFile
    inputs.file(probe)
    inputs.file(layout.projectDirectory.file("src/main/headers/vk_mem_alloc.h"))
    inputs.dir(vulkanInclude.map(::file))
    executable(nativeLibrary.compilerExecutable.get())
    args(
        "-std=${nativeLibrary.cppStandard.get()}",
        "-fsyntax-only",
        "-DVMA_EXTERNAL_MEMORY=1",
        "-I${headerDirectory.absolutePath}",
        "-I${vulkanInclude.get()}",
        probe.absolutePath,
    )
}

tasks.test {
    dependsOn("compileNativeLibrary")
    dependsOn(verifyVmaAbi)
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    val libraryFileName = when {
        System.getProperty("os.name").startsWith("Windows", ignoreCase = true) -> "caelum_vma.dll"
        System.getProperty("os.name").startsWith("Mac", ignoreCase = true) -> "libcaelum_vma.dylib"
        else -> "libcaelum_vma.so"
    }
    systemProperty(
        "caelum.vma.test.library",
        layout.buildDirectory.file("native/caelum-vma/$libraryFileName").get().asFile.absolutePath,
    )
    systemProperty("caelum.vma.test.externalMemory", "1")
}

dependencies {
    api(project(":caelum-core"))
    // The full allocator API uses Caelum Vulkan types; the current virtual API
    // remains device-independent while keeping the module dependency stable.
    api(project(":caelum-vulkan"))
    testImplementation(kotlin("test-junit5"))
}

tasks.test {
    useJUnitPlatform()
}
