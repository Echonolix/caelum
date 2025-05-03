package c.ast

import c.ast.visitor.GroupSpecifierVisitor
import tree_sitter.c.node.*


context(_: ParseContext)
private fun processGroup(name: TypeIdentifierNode?, body: FieldDeclarationListNode?, v: GroupSpecifierVisitor) {
    name?.let { v.visitName(it.content()) }

    val children = body?.children() ?: return


    children.forEach {
        when (val node = it) {
            is CommentNode -> v.visitComment(it.content())
            is FieldDeclarationNode -> {
                val visitor = v.visitField()


                run {
                    val typeVisitor = visitor.visitType()
                    visitTypeSpecifierNode(node.type, typeVisitor)
                    typeVisitor.visitEnd()
                }

                run {
                    val declaratorVisitor = visitor.visitDeclarator()
                    visitFieldDeclaratorNode(node.declarator[0], declaratorVisitor)
                    declaratorVisitor.visitEnd()
                }

                visitor.visitEnd()

            }

            else -> error("unexpected child: ${it::class.simpleName}")
        }
    }

}

context(_: ParseContext)
internal fun processStruct(n: StructSpecifierNode, v: GroupSpecifierVisitor) {
    processGroup(n.name, n.body, v)
}

context(_: ParseContext)
internal fun processUnion(n: UnionSpecifierNode, v: GroupSpecifierVisitor) {
    processGroup(n.name, n.body, v)
}