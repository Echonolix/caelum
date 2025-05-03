package tree_sitter.c.node

import tree_sitter.Node

public sealed interface AlignofExpressionNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): AlignofExpressionNodeChildren {
            val n = createNode(node)
            if (n is AlignofExpressionNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a AlignofExpressionNodeChildren")
        }
    }
}

public class AlignofExpressionNode(
    override val `$node`: Node,
) : CNodeBase,
    ExpressionNode {
    public val type: TypeDescriptorNode
        get() = TypeDescriptorNode(
            `$node`.getChildByFieldName("type") ?: error("required field type is null")
        )

    public fun children(): List<AlignofExpressionNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            AlignofExpressionNodeChildren(it)
        }
    }
}
