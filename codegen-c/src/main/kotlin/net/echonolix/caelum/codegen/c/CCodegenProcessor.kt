package net.echonolix.caelum.codegen.c

import net.echonolix.caelum.codegen.c.adapter.CAstContext
import net.echonolix.ktgen.KtgenProcessor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*

class CCodegenProcessor : KtgenProcessor {
    override fun process(inputs: Set<Path>, outputDir: Path): Set<Path> {
        val elementCtx = CAstContext(inputs.mapTo(mutableSetOf()) { it.absolutePathString() })
        val clangProcess = Runtime.getRuntime().exec(
            arrayOf(
                "clang",
                "-E",
                "-C",
                "--target=x86_64-pc-windows-gnu",
                "-std=c23",
                *inputs.map { it.absolutePathString() }.toTypedArray()
            )
        )
        val source = clangProcess.inputReader().readText()
        elementCtx.parse(source)

        println("Typedefs:")
        elementCtx.typedefs.forEach { (name, type) ->
            println("\t$name -> $type")
        }
        println()
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
        println(ctx.basePkgName)

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

    fun resourcePath(path: String): Path {
        return Paths.get(CCodegenProcessor::class.java.getResource(path)!!.toURI())
    }

    val time = System.nanoTime()
    val inputs = setOf(
//        resourcePath("/test1.h")
//        resourcePath("/test2.h")
        Path("glfw/glfw3.h")
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