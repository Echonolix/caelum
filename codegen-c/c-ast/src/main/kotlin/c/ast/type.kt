package c.ast

import c.ast.visitor.DeclaratorVisitor
import c.ast.visitor.TypeSpecifierVisitor
import c.lang.CPrimitiveType
import c.lang.CSizeSpecifier
import tree_sitter.c.node.*
import java.util.Locale.getDefault

private fun String.cap() = replaceFirstChar { if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString() }

context(ctx: ParseContext)
internal fun visitTypeSpecifierNode(n: TypeSpecifierNode, v: TypeSpecifierVisitor) {
    when (n) {
        is EnumSpecifierNode -> {
            val enumVisitor = v.visitEnumSpecifier()
            processEnum(n, enumVisitor)
            enumVisitor.visitEnd()
        }
        is MacroTypeSpecifierNode -> {
            println("MACRO: ${n.content()}")
        }
        is PrimitiveTypeNode -> {
            val primitive = CPrimitiveType.entries.find { it.name == (n.content().cap()) }
            if (primitive != null) {
                v.visitPrimitiveType(primitive)
            } else {
                v.visitTypeIdentifier(n.content())
            }
        }

        is SizedTypeSpecifierNode -> {

            val sizedVisitor = v.visitSizedTypeSpecifier()

            n.sizeSpecifier.forEach {
                sizedVisitor.visitSizedSpecifier(CSizeSpecifier.valueOf(it.content().cap()))
            }
            when (val type = n.type) {
                is PrimitiveTypeNode -> sizedVisitor.visitType(CPrimitiveType.valueOf(type.content().cap()))
                is TypeIdentifierNode -> {}
                null -> {

                }
            }

            sizedVisitor.visitEnd()
        }

        is StructSpecifierNode -> {
            val visitor = v.visitStructSpecifier()
            processStruct(n, visitor)
            visitor.visitEnd()
        }

        is UnionSpecifierNode -> {
            val visitor = v.visitStructSpecifier()
            processUnion(n, visitor)
            visitor.visitEnd()
        }

        is TypeIdentifierNode -> v.visitTypeIdentifier(n.content())
    }
}

context(_: ParseContext)
internal fun visitDeclaratorNode(n: _DeclaratorNode, v: DeclaratorVisitor) {
    when (n) {
        is _TypeDeclaratorNode -> {
            visitTypeDeclaratorNode(n as _TypeDeclaratorNode, v)
        }

        is IdentifierNode -> v.visitIdentifier(n.content())
    }
}

context(_: ParseContext)
internal fun visitFieldDeclaratorNode(n: _FieldDeclaratorNode, v: DeclaratorVisitor) {
    when (n) {
        is _TypeDeclaratorNode -> {
            visitTypeDeclaratorNode(n as _TypeDeclaratorNode, v)
        }

        is FieldIdentifierNode -> v.visitFieldIdentifier(n.content())
    }
}

context(_: ParseContext)
internal fun visitTypeDeclaratorNode(n: _TypeDeclaratorNode, v: DeclaratorVisitor) {
    when (n) {
        is ArrayDeclaratorNode -> {
            val d = n.declarator
            when (d) {
                is _TypeDeclaratorNode -> visitTypeDeclaratorNode(d, v)
                is IdentifierNode -> v.visitIdentifier(d.content())
                is FieldIdentifierNode -> v.visitFieldIdentifier(d.content())
            }
            v.visitArray()
        }

        is AttributedDeclaratorNode -> TODO()
        is FunctionDeclaratorNode -> visitFunctionDeclaratorNode(n, v)
        is ParenthesizedDeclaratorNode -> {
            val d = n.children()[0]
            when (d) {
                is _TypeDeclaratorNode -> visitTypeDeclaratorNode(d, v)
                else -> error("Unexpected type of declarator: ${d::class.simpleName}")
            }

        }


        is PointerDeclaratorNode -> {
            when (val n = n.declarator) {
                is _DeclaratorNode -> visitDeclaratorNode(n, v)
                is _TypeDeclaratorNode -> visitTypeDeclaratorNode(n, v)
                is FieldIdentifierNode -> v.visitFieldIdentifier(n.content())
            }
            v.visitPointer()
        }

        is PrimitiveTypeNode -> v.visitIdentifier(n.content())
        is TypeIdentifierNode -> v.visitIdentifier(n.content())
    }
}


context(_: ParseContext)
internal fun visitAbstractTypeDeclaratorNode(n: _AbstractDeclaratorNode, v: DeclaratorVisitor) {
    when (n) {
        is AbstractArrayDeclaratorNode -> TODO()
        is AbstractFunctionDeclaratorNode -> TODO()
        is AbstractParenthesizedDeclaratorNode -> TODO()
        is AbstractPointerDeclaratorNode -> {
            when (val n = n.declarator) {
                is _AbstractDeclaratorNode -> {
                    visitAbstractTypeDeclaratorNode(n, v)
                }
                null -> {

                }
            }
            v.visitPointer()
        }
    }
}


context(_: ParseContext)
internal fun visitFunctionDeclaratorNode(n: FunctionDeclaratorNode, v: DeclaratorVisitor) {
    val p = n.parameters
    val functionVisitor = v.visitFunction()
    p.children().filterIsInstance<ParameterDeclarationNode>().forEachIndexed { i, it ->
        val visitor = functionVisitor.visitParameter()

        val typeVisitor = visitor.visitType()
        visitTypeSpecifierNode(it.type, typeVisitor)
        typeVisitor.visitEnd()

        val declaratorVisitor = visitor.visitDeclarator()
        when (val declarator = it.declarator) {
            is _AbstractDeclaratorNode -> {
                visitAbstractTypeDeclaratorNode(declarator, declaratorVisitor)
            }

            is _DeclaratorNode -> {
                visitDeclaratorNode(declarator, declaratorVisitor)
            }

            null -> {

            }
        }
        declaratorVisitor.visitEnd()

        visitor.visitEnd()
    }
    functionVisitor.visitEnd()


    when (val d = n.declarator) {
        is _TypeDeclaratorNode -> {
            visitTypeDeclaratorNode(d, v)
        }

        is IdentifierNode -> v.visitIdentifier(d.content())
        is FieldIdentifierNode -> v.visitFieldIdentifier(d.content())
    }


}

//context(_: ParseContext)
//internal fun visitTypeDeclarationNode(type: TypeSpecifierNode, declarator: _TypeDeclaratorNode?, visitor: TypeVisitor) {
//    visitTypeSpecifierNode(type, visitor)
//    declarator?.let { visitTypeDeclaratorNode(it, visitor) }
//}

//context(_: ParseContext)
//internal fun visitDeclarationNode(type: TypeSpecifierNode, declarator: _DeclaratorNode?, visitor: DeclaratorVisitor) {
//    visitTypeSpecifierNode(type, visitor)
//    declarator?.let { visitDeclaratorNode(declarator, visitor) }
//}
//
//context(_: ParseContext)
//internal fun visitAbstractDeclarationNode(
//    type: TypeSpecifierNode,
//    declarator: _AbstractDeclaratorNode?,
//    visitor: DeclaratorVisitor
//) {
//    visitTypeSpecifierNode(type, visitor)
//    declarator?.let { visitAbstractTypeDeclaratorNode(declarator, visitor) }
//}