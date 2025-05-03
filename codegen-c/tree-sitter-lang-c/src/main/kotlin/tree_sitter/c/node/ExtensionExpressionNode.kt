package tree_sitter.c.node

import tree_sitter.Node

public sealed interface ExtensionExpressionNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): ExtensionExpressionNodeChildren {
            val n = createNode(node)
            if (n is ExtensionExpressionNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a ExtensionExpressionNodeChildren")
        }
    }
}

public class ExtensionExpressionNode(
    override val `$node`: Node,
) : CNodeBase,
    ExpressionNode {
    public fun children(): ExtensionExpressionNodeChildren =
        ExtensionExpressionNodeChildren(
            `$node`.getNamedChild(0u) ?: error("no child found for extension_expression")
        )
}
