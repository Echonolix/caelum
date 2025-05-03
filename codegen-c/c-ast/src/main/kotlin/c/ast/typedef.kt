package c.ast

import c.ast.visitor.TypeDefVisitor
import tree_sitter.c.node.TypeDefinitionNode

context(ParseContext)
internal fun processTypedef(n: TypeDefinitionNode, visitor: TypeDefVisitor) {

    val typeVisitor = visitor.visitType()
    visitTypeSpecifierNode(n.type, typeVisitor)
    typeVisitor.visitEnd()


    val declarator = visitor.visitDeclarator()
    visitTypeDeclaratorNode(n.declarator[0], declarator)
    declarator.visitEnd()

    visitor.visitEnd()
}


