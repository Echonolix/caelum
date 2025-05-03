package net.echonolix.caelum.codegen.c

import net.echonolix.caelum.codegen.c.adapter.ElementContext
import net.echonolix.ktgen.KtgenProcessor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*

class CCodeGenProcessor : KtgenProcessor {
    override fun process(inputs: Set<Path>, outputDir: Path): Set<Path> {
        val elementCtx = ElementContext()
        inputs.forEach {
            elementCtx.parse(it.readText())
        }
        return emptySet()
    }

    companion object {
        private val tempLibDir = Path(System.getProperty("java.io.tmpdir"), "net.echonolix.caelum")

        init {
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
            val libraryPath = CCodeGenProcessor::class.java.getResource("/$libraryFileName")
                ?: error("Library $libraryFileName not found in resources")

            if (libraryPath.protocol == "file") {
                System.load(libraryPath.path)
            } else {
                libraryPath.openStream().use {
                    Files.copy(it, tempLibDir.resolve(libraryFileName))
                }
            }
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
    fun resourcePath(path: String): Path {
        return Paths.get(CCodeGenProcessor::class.java.getResource(path)!!.toURI())
    }
    val time = System.nanoTime()
    val inputs = setOf(
        resourcePath("/test2.h")
    )
    val outputDir = Path("testoutput")
    val updatedFiles = CCodeGenProcessor().process(inputs, outputDir).toMutableSet()
    updatedFiles.toList().forEach {
        addParentUpTo(it.parent, outputDir, updatedFiles)
    }
    outputDir.walk(PathWalkOption.INCLUDE_DIRECTORIES, PathWalkOption.BREADTH_FIRST, PathWalkOption.FOLLOW_LINKS)
        .filter { it != outputDir }.filter { it !in updatedFiles }.forEach {
            it.deleteRecursively()
        }
    println("Time: %.2fs".format((System.nanoTime() - time) / 1_000_000.0 / 1000.0))
}