package tree_sitter.c.node

import tree_sitter.Node

public sealed interface GnuAsmOutputOperandNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): GnuAsmOutputOperandNodeChildren {
            val n = createNode(node)
            if (n is GnuAsmOutputOperandNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a GnuAsmOutputOperandNodeChildren")
        }
    }
}

public class GnuAsmOutputOperandNode(
    override val `$node`: Node,
) : CNodeBase,
    GnuAsmOutputOperandListNodeChildren {
    public val constraint: StringLiteralNode
        get() = StringLiteralNode(
            `$node`.getChildByFieldName("constraint") ?: error("required field constraint is null")
        )

    public val symbol: IdentifierNode?
        get() = (`$node`.getChildByFieldName("symbol"))?.let { IdentifierNode(it) }

    public val `value`: ExpressionNode
        get() = ExpressionNode(
            `$node`.getChildByFieldName("value") ?: error("required field value is null")
        )

    public fun children(): List<GnuAsmOutputOperandNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            GnuAsmOutputOperandNodeChildren(it)
        }
    }
}
