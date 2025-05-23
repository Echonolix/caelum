package net.echonolix.caelum.codegen.c.adapter

import c.ast.ParseContext
import c.ast.content
import c.ast.visitor.EnumVisitor
import c.lang.ASTNumberValue
import tree_sitter.c.node.*
import kotlin.collections.plusAssign

class BuildEnumVisitor() : EnumVisitor {
    var identifier: Identifier? = null
    val enumerators = mutableListOf<CEnumerator>()

    override fun visitName(name: String) {
        identifier = Identifier(name)
    }

    override fun visitComment(comment: String) {}

    override fun visitEnumerator(name: String, value: ExpressionNode?, ctx: ParseContext) {
        with(ctx) {
            val exp = if (value != null) {
                parseASTNumber(value)
            } else {
                enumerators.lastOrNull()?.value?.let { left ->
                    ASTNumberValue.Binary(ASTNumberValue.BinaryOp.Add, left, ASTNumberValue.Literal("1"))
                } ?: ASTNumberValue.Literal("0")
            }

            enumerators += CEnumerator(Identifier(name), exp)
        }
    }

    override fun visitEnd() {}

    fun build(): CEnum {
        return CEnum(identifier, enumerators)
    }
}

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