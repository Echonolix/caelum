package net.echonolix.caelum

import c.ast.parse
import c.ast.visitor.*
import net.echonolix.ktgen.KtgenProcessor
import tree_sitter.Range
import tree_sitter.c.node.CNodeBase
import tree_sitter.c.node.TypeDefinitionNode
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*


class FFIContext {
    lateinit var lineMarker: LineMarker
}

/**
 * @return the line num at original source
 */
fun posOf(lineMarker: LineMarker, ast: CNodeBase): Int {
    val offsetLine = ast.`$node`.range.endPoint.row - lineMarker.range.endPoint.row
    return lineMarker.lineNum + offsetLine.toInt()
}


class FFIASTVisitor(val ctx: FFIContext) : ASTVisitor {
    override fun visitLineMarker(lineNum: Int, fileName: String, newFile: Boolean, returnFile: Boolean, fromSysHeader: Boolean, pos: Range) {
        ctx.lineMarker = LineMarker(lineNum, fileName, newFile, returnFile, fromSysHeader, pos)
    }

    override fun visitComment(comment: String) {
        TODO()
    }

    override fun visitTypedef(ast: TypeDefinitionNode): TypeDefVisitor {
        println(ctx.lineMarker.fileName)
        println(posOf(ctx.lineMarker, ast))
        val visitor = BuildTypedefVisitor()
        return object : TypeDefVisitor by visitor {
            override fun visitEnd() {
                println(visitor.cType)
            }
        }
    }

    override fun visitStructSpecifier(): GroupSpecifierVisitor {
        val visitor = CommonGroupSpecifierVisitor()
        return object : GroupSpecifierVisitor by visitor {
            override fun visitEnd() {
                println(CType.Struct(visitor.identifier, visitor.members))
            }
        }
    }

    override fun visitUnionSpecifier(): GroupSpecifierVisitor {
        TODO()
    }

    override fun visitEnumSpecifier(): EnumVisitor {
        TODO()
    }

    override fun visitDeclaration(): DeclarationVisitor {
        val visitor = BuildDeclarationVisitor()
        return object : DeclarationVisitor by visitor {
            override fun visitEnd() {
                println(visitor.cType)
            }
        }
    }
}

class CCodeGenProcessor : KtgenProcessor {
    override fun process(inputs: Set<Path>, outputDir: Path): Set<Path> {
        val ctx = FFIContext()
        val visitor = FFIASTVisitor(ctx)

        parse(javaClass.getResource("/test2.h")!!.readText(), visitor)

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
    val time = System.nanoTime()
    val outputDir = Path("testoutput")
    val updatedFiles = CCodeGenProcessor().process(emptySet(), outputDir).toMutableSet()
    updatedFiles.toList().forEach {
            addParentUpTo(it.parent, outputDir, updatedFiles)
        }
    outputDir.walk(PathWalkOption.INCLUDE_DIRECTORIES, PathWalkOption.BREADTH_FIRST, PathWalkOption.FOLLOW_LINKS)
        .filter { it != outputDir }.filter { it !in updatedFiles }.forEach {
            it.deleteRecursively()
        }
    println("Time: %.2fs".format((System.nanoTime() - time) / 1_000_000.0 / 1000.0))
}