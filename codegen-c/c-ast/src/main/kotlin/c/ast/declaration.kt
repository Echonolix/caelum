package c.ast

import c.ast.visitor.DeclarationVisitor
import c.lang.ASTNumberValue
import tree_sitter.c.node.*

context(_: ParseContext)
internal fun parseASTNumber(node: ExpressionNode): ASTNumberValue {
    return when (node) {
        is NumberLiteralNode -> ASTNumberValue.Literal(node.content())
        is UnaryExpressionNode -> {
            val opStr = node.operator.content()
            val exp = node.argument

            require(exp is ExpressionNode)

            val op = when (opStr) {
                "-" -> ASTNumberValue.UnaryOp.Negative
                else -> error("Unknown unary op: $opStr")
            }
            ASTNumberValue.Unary(op, parseASTNumber(exp))
        }
        is BinaryExpressionNode -> {
            val leftNode = node.left
            val rightNode = node.right

            require(leftNode is ExpressionNode)
            require(rightNode is ExpressionNode)

            val left = parseASTNumber(leftNode)
            val right = parseASTNumber(rightNode)
            val opStr = node.operator

            val op = when (opStr.content()) {
                "|" -> ASTNumberValue.BinaryOp.Or
                "<<" -> ASTNumberValue.BinaryOp.Shl
                "-" -> ASTNumberValue.BinaryOp.Sub
                else -> error("unknown binary operator $opStr")
            }

            ASTNumberValue.Binary(op, left, right)
        }
        is IdentifierNode -> {
            val id = node.content()
            ASTNumberValue.Ref(id)
        }
        is ParenthesizedExpressionNode -> {

            val exp = node.children()
            require(exp is ExpressionNode)

            ASTNumberValue.Paraenthesized(parseASTNumber(exp))
        }
        else -> error("Unknown node for number literal: $node")
    }
}

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

        when (declarator) {
            is _DeclaratorNode -> {
                val declaratorVisitor = v.visitDeclarator()
                visitDeclaratorNode(declarator, declaratorVisitor)
                declaratorVisitor.visitEnd()
            }
            is InitDeclaratorNode -> {
                val initDeclaratorVisitor = v.visitInitDeclarator()
                val declaratorVisitor = initDeclaratorVisitor.visitDeclarator()
                visitDeclaratorNode(declarator.declarator, declaratorVisitor)
                declaratorVisitor.visitEnd()
                initDeclaratorVisitor.visitInitializer(parseASTNumber(declarator.value as ExpressionNode))
                initDeclaratorVisitor.visitEnd()
            }
            is GnuAsmExpressionNode -> TODO()
            is MsCallModifierNode -> TODO()
        }
    }
}
