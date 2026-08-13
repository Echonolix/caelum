package buildsrc.convention

import net.echonolix.ktgen.KtgenTask

plugins {
    id("buildsrc.convention.published-module")
    id("net.echonolix.ktgen")
}

val codegenCpp = extensions.create("codegenCpp", CodegenCppExtension::class.java)

codegenCpp.moduleName.convention(project.name)
codegenCpp.kotlinObjectName.convention(
    codegenCpp.moduleName.map { moduleName ->
        moduleName
        .split(Regex("[^A-Za-z0-9]+"))
        .filter(String::isNotBlank)
        .joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
        .ifBlank { "Cpp" } + "Native"
    }
)

dependencies {
    ktgen(project(":caelum-codegen-cpp"))
    api(project(":caelum-core"))
}

kotlin {
    explicitApi()
}

tasks.ktgen {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

// Consumer build scripts configure codegenCpp after applying this convention.
// Resolve its values after project evaluation, then write ordinary task inputs
// rather than retaining a task-action closure; this remains configuration-cache
// serializable and produces a direct failure if a required value was omitted.
afterEvaluate {
    tasks.named<KtgenTask>("ktgen") {
        systemProperty("codegencpp.packageName", codegenCpp.packageName.get())
        systemProperty("codegencpp.moduleName", codegenCpp.moduleName.get())
        systemProperty("codegencpp.objectName", codegenCpp.kotlinObjectName.get())
        systemProperty("codegencpp.clang", codegenCpp.clangExecutable.get())

        val clangArguments = buildList {
            codegenCpp.apiDefines.get().toSortedMap().forEach { (name, value) ->
                require(name.isNotBlank()) { "C++ API define names must not be blank" }
                add("-D$name${if (value.isBlank()) "" else "=$value"}")
            }
            codegenCpp.includeDirs.files
                .map { directory -> directory.absoluteFile }
                .sortedBy { it.invariantSeparatorsPath }
                .forEach { directory ->
                    require(directory.isDirectory) { "C++ codegen include directory does not exist: $directory" }
                    add("-I${directory.absolutePath}")
                }
            addAll(codegenCpp.compilerArguments.get())
        }
        // The root header is the Ktgen input. Included headers are also
        // semantic inputs, otherwise changed included declarations could leave
        // generated bindings up-to-date.
        inputs.files(codegenCpp.includeDirs)
        inputs.property("codegencpp.apiDefines", codegenCpp.apiDefines.get())
        // Store a stable scalar rather than Kotlin's builder List: Gradle's
        // configuration-cache serializer cannot restore that implementation.
        inputs.property("codegencpp.compilerArguments", clangArguments.joinToString("\u0000"))
        // Indexed properties preserve compiler arguments containing spaces
        // without shell escaping or newline trimming.
        clangArguments.forEachIndexed { index, argument ->
            systemProperty("codegencpp.compilerArg.$index", argument)
        }
    }
}

// The conventions remain independently usable. When both are applied in one
// project, however, C++ public-ABI configuration has exactly one owner:
// codegenCpp. Bridge its include paths, public macros, and generated shim into
// the native library lazily through KtgenTask's declared output directory.
pluginManager.withPlugin("buildsrc.convention.native-library") {
    val nativeLibrary = extensions.getByType(NativeLibraryExtension::class.java)
    nativeLibrary.includeDirs.from(codegenCpp.includeDirs)
    nativeLibrary.apiDefines.putAll(codegenCpp.apiDefines)

    // `includeDirs` is deliberately path-only on CompileNativeLibraryTask: a
    // compiler search path must not implicitly make every possible SDK file an
    // input.  Here it is a codegenCpp public-ABI input, though, so track the
    // all regular files reachable from those roots as explicit native inputs.
    // This keeps a transitive declaration or inline implementation change from
    // leaving a generated shim compiled against stale headers, including valid
    // extensionless or project-specific include fragments. FileTree only
    // contributes files, never the include directories themselves.
    afterEvaluate {
        nativeLibrary.dependencyHeaders.from(
            codegenCpp.includeDirs.files.map { includeDirectory ->
                fileTree(includeDirectory)
            },
        )
    }

    val shimFileName = codegenCpp.moduleName.map { moduleName ->
        "${moduleName.replace(Regex("[^A-Za-z0-9_]+"), "_").replace(Regex("_+"), "_").trim('_').ifEmpty { "cpp" }}_shim.cpp"
    }
    val generatedShim = tasks.named<KtgenTask>("ktgen").flatMap { task ->
        task.outputDir.file(shimFileName)
    }
    nativeLibrary.sources.from(generatedShim)
    tasks.named<CompileNativeLibraryTask>("compileNativeLibrary") {
        dependsOn(tasks.named("ktgen"))
    }
}
