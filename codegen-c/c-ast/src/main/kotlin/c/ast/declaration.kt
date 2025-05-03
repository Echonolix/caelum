package c.ast

import c.ast.visitor.DeclarationVisitor
import tree_sitter.c.node.*


context(_: ParseContext)
internal fun processDeclaration(
    type: TypeSpecifierNode,
    declarator: DeclarationNodeDeclarator,
    v: DeclarationVisitor,
) {
    run {
        val typeVisitor = v.visitType()
        visitTypeSpecifierNode(type, typeVisitor)
        typeVisitor.visitEnd()
    }

    run {
        val declaratorVisitor = v.visitDeclarator()

        when (declarator) {
            is _DeclaratorNode -> {
                visitDeclaratorNode(declarator, declaratorVisitor)
            }

            is GnuAsmExpressionNode -> TODO()
            is InitDeclaratorNode -> TODO()
            is MsCallModifierNode -> TODO()
        }
        declaratorVisitor.visitEnd()
    }
}
