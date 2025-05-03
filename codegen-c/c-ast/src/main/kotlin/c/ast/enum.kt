package c.ast

import c.ast.visitor.EnumVisitor
import tree_sitter.c.node.CommentNode
import tree_sitter.c.node.EnumSpecifierNode
import tree_sitter.c.node.EnumeratorNode



context(ctx: ParseContext)
internal fun processEnum(n: EnumSpecifierNode, v: EnumVisitor) {
    n.name?.let { v.visitName(it.content()) }

    val body = n.body ?: return

    body.children().forEach {
        when (val node = it) {
            is CommentNode -> {
                v.visitComment(it.content())
            }

            is EnumeratorNode -> {
                val name = node.name.content()
                v.visitEnumerator(name, node.value, ctx)
            }

            else -> error("unexpected child: ${it::class.simpleName}")
        }
    }
}