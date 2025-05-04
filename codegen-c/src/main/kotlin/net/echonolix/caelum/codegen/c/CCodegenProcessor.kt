package net.echonolix.caelum.codegen.c

import net.echonolix.caelum.CSyntax
import net.echonolix.caelum.codegen.c.adapter.CAstContext
import net.echonolix.ktgen.KtgenProcessor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*

class CCodegenProcessor : KtgenProcessor {
    override fun process(inputs: Set<Path>, outputDir: Path): Set<Path> {
        val elementCtx = CAstContext(inputs.mapTo(mutableSetOf()) { it.absolutePathString() })
        val excludedConsts = (System.getProperty("codegenc.excludeConsts") ?: "")
            .splitToSequence(",")
            .mapTo(mutableSetOf()) { it.trim() }
        run {
            val defineRegex = """^\s*#define\s+(${CSyntax.nameRegex.pattern})\s+(.+)$""".toRegex()
            val defineLines = inputs.asSequence()
                .flatMap { path ->
                    return@flatMap path.useLines { lines ->
                        lines.mapNotNull { line ->
                            defineRegex.matchEntire(line)?.let {
                                val (name, value) = it.destructured
                                if (name in excludedConsts) return@let null
                                "const int $name = $value;"
                            }
                        }.toList()
                    }
                }
                .joinToString("\n")
            val clangProcess = Runtime.getRuntime().exec(
                arrayOf(
                    "clang",
                    "-E",
                    "-C",
                    "--target=x86_64-pc-windows-gnu",
                    "-std=c23",
                    "-"
                )
            )
            clangProcess.outputWriter().use {
                it.write(defineLines)
            }
            elementCtx.parse(clangProcess.inputReader().readText())
        }

//        run {
//            val clangProcess = Runtime.getRuntime().exec(
//                arrayOf(
//                    "clang",
//                    "-E",
//                    "-C",
//                    "--target=x86_64-pc-windows-gnu",
//                    "-std=c23",
//                    *inputs.map { it.absolutePathString() }.toTypedArray()
//                )
//            )
//            elementCtx.parse(clangProcess.inputReader().readText())
//        }

        println("Typedefs:")
        elementCtx.typedefs.forEach { (name, type) ->
            println("\t$name -> $type")
        }
        println()
        println("Consts:")
        elementCtx.consts.forEach { (name, type) ->
            println("\t$name -> $type")
        }
        println("Enums:")
        elementCtx.enums.forEach { (name, type) ->
            println("\t$name -> $type")
        }
        println()
        println("Global Enums:")
        elementCtx.globalEnums.forEach { type ->
            println("\t$type")
        }
        println("Structs:")
        elementCtx.structs.forEach { (name, type) ->
            println("\t$name -> $type")
        }
        println()
        println("Unions:")
        elementCtx.unions.forEach { (name, type) ->
            println("\t$name -> $type")
        }
        println()
        println("Functions:")
        elementCtx.functions.forEach { (name, type) ->
            println("\t$name -> $type")
        }
        println()

        val ctx = CCodeGenContext(System.getProperty("codegenc.packageName"), outputDir, elementCtx)
        elementCtx.typedefs.forEach { (name, _) ->
            println(ctx.resolveElement(name))
        }
        elementCtx.consts.forEach { (name, _) ->
            println(ctx.resolveElement(name))
        }
        elementCtx.enums.forEach { (name, _) ->
            println(ctx.resolveElement(name))
        }
        elementCtx.globalEnums.forEach { (name, _) ->
            println(ctx.resolveElement(name))
        }
        elementCtx.structs.forEach { (name, _) ->
            println(ctx.resolveElement(name))
        }
        elementCtx.unions.forEach { (name, _) ->
            println(ctx.resolveElement(name))
        }
        elementCtx.functions.forEach { (name, _) ->
            println(ctx.resolveElement(name))
        }

        return emptySet()
    }

    companion object {
        private val tempLibDir = Path(System.getProperty("java.io.tmpdir"), "net.echonolix.caelum")

        init {
            tempLibDir.createDirectories()
            loadLibrary("libzstd")
            loadLibrary("wasmtime")
            loadLibrary("libtree-sitter-0.25")
            loadLibrary("c")
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
    System.setProperty("codegenc.packageName", "net.echonolix.caelum.glfw")
    System.setProperty("codegenc.excludeConsts", "APIENTRY,WINGDIAPI,CALLBACK,GLFWAPI,GLAPIENTRY")
    fun resourcePath(path: String): Path {
        return Paths.get(CCodegenProcessor::class.java.getResource(path)!!.toURI())
    }

    val time = System.nanoTime()
    val inputs = setOf(
//        resourcePath("/test1.h")
//        resourcePath("/test2.h")
        Path("glfw/glfw3.h")
//        Path("codegen-c/llvm-c.h")
//        Path("codegen-c/test.h")
    )
    val outputDir = Path("testoutput")
    val updatedFiles = CCodegenProcessor().process(inputs, outputDir).toMutableSet()
    updatedFiles.toList().forEach {
        addParentUpTo(it.parent, outputDir, updatedFiles)
    }
    outputDir.walk(PathWalkOption.INCLUDE_DIRECTORIES, PathWalkOption.BREADTH_FIRST, PathWalkOption.FOLLOW_LINKS)
        .filter { it != outputDir }.filter { it !in updatedFiles }.forEach {
            it.deleteRecursively()
        }
    println("Time: %.2fs".format((System.nanoTime() - time) / 1_000_000.0 / 1000.0))
}