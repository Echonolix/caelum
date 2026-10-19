package net.echonolix.caelum.codegen.c

import net.echonolix.caelum.NativeLibraries
import net.echonolix.caelum.codegen.api.CSyntax
import net.echonolix.caelum.codegen.api.ctx.CodegenContext
import net.echonolix.caelum.codegen.api.ctx.CodegenOutput
import net.echonolix.caelum.codegen.api.ctx.ElementDocumenter
import net.echonolix.caelum.codegen.c.adapter.CAstContext
import net.echonolix.caelum.codegen.c.tasks.GenerateConstantTask
import net.echonolix.caelum.codegen.c.tasks.GenerateEnumTask
import net.echonolix.caelum.codegen.c.tasks.GenerateFunctionTask
import net.echonolix.caelum.codegen.c.tasks.GenerateGroupTask
import net.echonolix.ktgen.KtgenProcessor
import java.io.BufferedReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.RecursiveAction
import java.util.concurrent.CompletableFuture
import kotlin.io.path.*

private var dev = false

class CCodegenProcessor : KtgenProcessor {
    private fun BufferedReader.readNLines(n: Int): List<String> {
        return lineSequence().take(n).toList()
    }

    override fun process(inputs: Set<Path>, outputDir: Path): Set<Path> {
        val stdinReader = System.`in`.bufferedReader()
        val renameMap = mutableMapOf<String, String>()
        val elementCtx = CAstContext()
        if (!dev) {
            run {
                val defineRegex = """^\s*#define\s+(${CSyntax.nameRegex.pattern})\s+(.+)$""".toRegex()
                val defines = inputs.asSequence()
                    .flatMap { path ->
                        return@flatMap path.useLines { lines ->
                            lines.mapNotNull { line ->
                                defineRegex.matchEntire(line)?.let {
                                    val (name, value) = it.destructured
                                    name to value
                                }
                            }.toList()
                        }
                    }
                    .toList()

                defines.forEach { (name, _) ->
                    println("${CAstContext.ElementType.CONST} $name")
                }

                System.out.flush()

                val constMapResult = stdinReader.readNLines(defines.size)

                val defineLines = defines.asSequence()
                    .mapIndexedNotNull { index, (name, value) ->
                        val newName = constMapResult[index]
                        if (newName == "null") {
                            null
                        } else {
                            renameMap[name] = newName
                            "const int $newName = $value;"
                        }
                    }
                    .joinToString("\n")

                elementCtx.parse(preprocess(defineLines))
            }
        }

        fun String?.parseList(): Sequence<String> {
            if (this == null) return emptySequence()
            return this.splitToSequence(",")
                .filter { it.isNotBlank() }
        }

        run {
            val extraArgs = mutableListOf<String>()
            System.getProperty("codegenc.preprocessDefines", null)
                .parseList()
                .mapTo(extraArgs) { "-D$it" }

            val includeRegex = """\s*#include\s+["<](.+)[">]\s*""".toRegex()
            val excludedIncludes = System.getProperty("codegenc.excludedIncludes", null)
                .parseList()
                .toSet()
            val declarations = inputs.flatMap { it.readLines() }
                .filter { line ->
                    includeRegex.matchEntire(line)?.let {
                        it.groups[1]?.value !in excludedIncludes
                    } ?: true
                }.joinToString("\n")
            elementCtx.parse(preprocess(declarations, extraArgs))
        }

        if (!dev) {
            var totalLines = 0
            elementCtx.allElements.asSequence()
                .filter { (typeName, _) -> typeName != CAstContext.ElementType.CONST }
                .forEach { (typeName, elements) ->
                    elements.forEach { (name) ->
                        println("$typeName $name")
                        totalLines++
                    }
                }
            System.out.flush()

            val results = stdinReader.readNLines(totalLines)
            var index = 0
            elementCtx.renameElements { elementType, name ->
                if (elementType == CAstContext.ElementType.CONST) {
                    return@renameElements name
                }
                val newName = results[index++]
                renameMap[name] = newName
                newName
            }
        }

        val elementResolver = CElementResolver(elementCtx, renameMap)
        val ctx = CodegenContext(
            CodegenOutput.Base(outputDir, System.getProperty("codegenc.packageName")),
            elementResolver,
            ElementDocumenter.Base(),
        )
        elementResolver.ctx = ctx
        elementCtx.consts.keys.forEach(ctx::resolveElement)
        elementCtx.allElements.asSequence()
            .filter { (typeName, _) -> typeName != CAstContext.ElementType.CONST }
            .map { it.value }
            .flatMap { it.keys }
            .forEach(ctx::resolveElement)

        object : RecursiveAction() {
            override fun compute() {
                val enum = GenerateEnumTask(ctx).fork()
                val consts = GenerateConstantTask(ctx).fork()
                val group = GenerateGroupTask(ctx).fork()
//                val typeDef = GenerateTypeDefTask(ctx).fork()
                val function = GenerateFunctionTask(ctx).fork()
                consts.join()
                enum.join()
                group.join()
//                typeDef.join()
                function.join()
            }
        }.fork().join()

        return ctx.outputFiles
    }

    private fun preprocess(source: String, extraArgs: List<String> = emptyList()): String {
        val command = listOf(
            System.getProperty("codegenc.clang", "clang"), "-E", "-C",
            "--target=x86_64-pc-windows-gnu", "-std=c23",
        ) + extraArgs + "-"
        val process = ProcessBuilder(command).start()
        // Drain both pipes while writing stdin, including for large headers.
        val output = CompletableFuture.supplyAsync { process.inputReader().use { it.readText() } }
        val errors = CompletableFuture.supplyAsync { process.errorReader().use { it.readText() } }
        try {
            process.outputWriter().use { it.write(source) }
            val code = process.waitFor()
            val text = output.get()
            val diagnostics = errors.get()
            check(code == 0) { "C preprocessing failed ($code): ${command.joinToString(" ")}\n$diagnostics" }
            return text
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    companion object {
        private val tempLibDir = Path(System.getProperty("java.io.tmpdir"), "net.echonolix.caelum")

        init {
            tempLibDir.createDirectories()
            val osName = System.getProperty("os.name")
            if (osName.startsWith("Linux", ignoreCase = true) ||
                osName.startsWith("Mac", ignoreCase = true) ||
                osName.startsWith("Darwin", ignoreCase = true)
            ) {
                NativeLibraries.load(CCodegenProcessor::class.java, "caelum_codegen_c")
            } else {
                loadLibrary("libzstd")
                loadLibrary("wasmtime")
                loadLibrary("libtree-sitter-0.25")
                loadLibrary("c")
            }
        }

        @JvmStatic
        private fun loadLibrary(libName: String) {
            val osName = System.getProperty("os.name").lowercase()
            val libExt = when {
                osName.contains("windows") -> "dll"
                osName.contains("mac") -> "dylib"
                else -> "so"
            }
            val libraryFileName = "$libName.$libExt"
            val libraryPath = CCodegenProcessor::class.java.getResource("/$libraryFileName")
                ?: error("Library $libraryFileName not found in resources")

            val libraryFilePathStr = if (libraryPath.protocol == "file") {
                libraryPath.path
            } else {
                val dst = tempLibDir.resolve(libraryFileName)
                if (!dst.exists()) {
                    libraryPath.openStream().use {
                        Files.copy(it, dst)
                    }
                }
                dst.pathString
            }

            System.load(libraryFilePathStr)
        }
    }
}

tailrec fun addParentUpTo(curr: Path?, end: Path, output: MutableCollection<Path>) {
    if (curr == null) return
    if (curr == end) return
    output.add(curr)
    return addParentUpTo(curr.parent, end, output)
}

@OptIn(ExperimentalPathApi::class)
fun main() {
    dev = true
    System.setProperty("codegenc.packageName", "net.echonolix.caelum.glfw")
    fun resourcePath(path: String): Path {
        return Paths.get(CCodegenProcessor::class.java.getResource(path)!!.toURI())
    }

//    val time = System.nanoTime()
    val inputs = setOf(
//        resourcePath("/test1.h")
//        resourcePath("/test2.h")
        Path("glfw/include/GLFW/glfw3.h")
//        Path("codegen-c/llvm-c.h")
//        Path("codegen-c/test.h")
    )
    val outputDir = Path("glfw/build/generated/ktgen")
    val updatedFiles = CCodegenProcessor().process(inputs, outputDir).toMutableSet()
    updatedFiles.toList().forEach {
        addParentUpTo(it.parent, outputDir, updatedFiles)
    }
    outputDir.walk(PathWalkOption.INCLUDE_DIRECTORIES, PathWalkOption.BREADTH_FIRST, PathWalkOption.FOLLOW_LINKS)
        .filter { it != outputDir }.filter { it !in updatedFiles }.forEach {
            it.deleteRecursively()
        }
//    println("Time: %.2fs".format((System.nanoTime() - time) / 1_000_000.0 / 1000.0))
}
