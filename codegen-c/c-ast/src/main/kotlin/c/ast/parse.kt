package c.ast

import c.ast.visitor.ASTVisitor
import tree_sitter.Node
import tree_sitter.Parser
import tree_sitter.Tree
import tree_sitter.c.node.*

class ParseContext(
    private val source: String,
    private val lang: TSCLanguage,
) {
    private val sourceBytes: ByteArray = source.toByteArray(Charsets.UTF_8)

    internal fun content(node: Node): String {
        return sourceBytes.sliceArray(node.startByte.toInt()..<node.endByte.toInt()).toString(Charsets.UTF_8)
    }

    internal fun content(base: CNodeBase): String {
        return content(base.`$node`)
    }
}

context(ctx: ParseContext)
fun Node.content(): String {
    return ctx.content(this)
}

context(ctx: ParseContext)
fun CNodeBase.content(): String {
    return ctx.content(this)
}

fun parse(
    source: String,
    visitor: ASTVisitor
) {
    val parser = Parser()
    val lang = TSCLanguage.Lang
    parser.language = lang.lang


    val tree = parser.parse(source)
    val ctx = ParseContext(source, lang)
    with(ctx) {
        processTree(tree, source, visitor)
    }
}

context(_: ParseContext)
private fun processTree(tree: Tree, source: String, visitor: ASTVisitor) {
    val cursor = tree.rootNode.walk()
    if (!cursor.gotoFirstChild()) {
        return
    }

    do {
        val n = cursor.node
        if (!n.isNamed) continue
        processTranslationUnit(n, visitor)
    } while (cursor.gotoNextSibling())

}

context(_: ParseContext)
private fun processTranslationUnit(node: Node, visitor: ASTVisitor) {
    val n = createNode(node)
    try {
        when (n) {
            is PreprocCallNode -> {
                parseLineMarker(n, visitor)
            }

            is CommentNode -> {
                visitor.visitComment(n.content())
            }

            is TypeDefinitionNode -> {
                val v = visitor.visitTypedef(n)
                processTypedef(n, v)
            }

            is StructSpecifierNode -> {
                val v = visitor.visitStructSpecifier()
                processStruct(n, v)
                v.visitEnd()
            }

            is EnumSpecifierNode -> {
                val v = visitor.visitEnumSpecifier()
                processEnum(n, v)
                v.visitEnd()
            }

            is DeclarationNode -> {
                val v = visitor.visitDeclaration()
                processDeclaration(n.type, n.declarator[0], v)
                v.visitEnd()
            }

            is FunctionDefinitionNode -> {
                // ignore function body here
                val v = visitor.visitDeclaration()


                run {
                    val typeVisitor = v.visitType()
                    visitTypeSpecifierNode(n.type, typeVisitor)
                    typeVisitor.visitEnd()
                }

                run {
                    val declaratorVisitor = v.visitDeclarator()
                    visitDeclaratorNode(n.declarator, declaratorVisitor)
                    declaratorVisitor.visitEnd()
                }

                v.visitEnd()
            }

            else -> {
                println("unknown node: ${n::class.simpleName}")
            }
        }
    } catch (e: Exception) {
        throw NodeVisitException(n, e)
    }
}

class NodeVisitException(
    val node: CNodeBase,
    override val cause: Throwable
) : Exception()