package tree_sitter.c.node

import tree_sitter.Node

public sealed interface SubscriptDesignatorNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): SubscriptDesignatorNodeChildren {
            val n = createNode(node)
            if (n is SubscriptDesignatorNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a SubscriptDesignatorNodeChildren")
        }
    }
}

public class SubscriptDesignatorNode(
    override val `$node`: Node,
) : CNodeBase,
    InitializerPairNodeChildren,
    InitializerPairNodeDesignator {
    public fun children(): SubscriptDesignatorNodeChildren =
        SubscriptDesignatorNodeChildren(
            `$node`.getNamedChild(0u) ?: error("no child found for subscript_designator")
        )
}
