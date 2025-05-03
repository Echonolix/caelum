package tree_sitter.c.node

import tree_sitter.Node

public sealed interface SubscriptRangeDesignatorNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): SubscriptRangeDesignatorNodeChildren {
            val n = createNode(node)
            if (n is SubscriptRangeDesignatorNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a SubscriptRangeDesignatorNodeChildren")
        }
    }
}

public class SubscriptRangeDesignatorNode(
    override val `$node`: Node,
) : CNodeBase,
    InitializerPairNodeChildren,
    InitializerPairNodeDesignator {
    public val end: ExpressionNode
        get() = ExpressionNode(
            `$node`.getChildByFieldName("end") ?: error("required field end is null")
        )

    public val start: ExpressionNode
        get() = ExpressionNode(
            `$node`.getChildByFieldName("start") ?: error("required field start is null")
        )

    public fun children(): List<SubscriptRangeDesignatorNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            SubscriptRangeDesignatorNodeChildren(it)
        }
    }
}
