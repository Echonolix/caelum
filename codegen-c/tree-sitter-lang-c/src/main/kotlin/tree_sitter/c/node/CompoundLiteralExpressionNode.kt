package tree_sitter.c.node

import tree_sitter.Node

public sealed interface CompoundLiteralExpressionNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): CompoundLiteralExpressionNodeChildren {
            val n = createNode(node)
            if (n is CompoundLiteralExpressionNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a CompoundLiteralExpressionNodeChildren")
        }
    }
}

public class CompoundLiteralExpressionNode(
    override val `$node`: Node,
) : CNodeBase,
    ExpressionNode {
    public val type: TypeDescriptorNode
        get() = TypeDescriptorNode(
            `$node`.getChildByFieldName("type") ?: error("required field type is null")
        )

    public val `value`: InitializerListNode
        get() = InitializerListNode(
            `$node`.getChildByFieldName("value") ?: error("required field value is null")
        )

    public fun children(): List<CompoundLiteralExpressionNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            CompoundLiteralExpressionNodeChildren(it)
        }
    }
}
