package tree_sitter.c.node

import tree_sitter.Node

public sealed interface GnuAsmInputOperandNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): GnuAsmInputOperandNodeChildren {
            val n = createNode(node)
            if (n is GnuAsmInputOperandNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a GnuAsmInputOperandNodeChildren")
        }
    }
}

public class GnuAsmInputOperandNode(
    override val `$node`: Node,
) : CNodeBase,
    GnuAsmInputOperandListNodeChildren {
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

    public fun children(): List<GnuAsmInputOperandNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            GnuAsmInputOperandNodeChildren(it)
        }
    }
}
