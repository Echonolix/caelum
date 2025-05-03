package tree_sitter.c.node

import tree_sitter.Node

public sealed interface PreprocDefinedNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): PreprocDefinedNodeChildren {
            val n = createNode(node)
            if (n is PreprocDefinedNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a PreprocDefinedNodeChildren")
        }
    }
}

public class PreprocDefinedNode(
    override val `$node`: Node,
) : CNodeBase,
    ArgumentListNodeChildren,
    ParenthesizedExpressionNodeChildren,
    BinaryExpressionNodeChildren,
    PreprocElifNodeChildren,
    PreprocIfNodeChildren,
    UnaryExpressionNodeChildren,
    BinaryExpressionNodeLeft,
    BinaryExpressionNodeRight,
    PreprocElifNodeCondition,
    PreprocIfNodeCondition,
    UnaryExpressionNodeArgument {
    public fun children(): PreprocDefinedNodeChildren =
        PreprocDefinedNodeChildren(
            `$node`.getNamedChild(0u) ?: error("no child found for preproc_defined")
        )
}
