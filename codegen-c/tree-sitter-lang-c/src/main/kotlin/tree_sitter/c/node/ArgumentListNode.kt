package tree_sitter.c.node

import tree_sitter.Node

public sealed interface ArgumentListNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): ArgumentListNodeChildren {
            val n = createNode(node)
            if (n is ArgumentListNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a ArgumentListNodeChildren")
        }
    }
}

public class ArgumentListNode(
    override val `$node`: Node,
) : CNodeBase,
    AttributeNodeChildren,
    AttributeSpecifierNodeChildren,
    MsBasedModifierNodeChildren,
    CallExpressionNodeChildren {
    public fun children(): List<ArgumentListNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            ArgumentListNodeChildren(it)
        }
    }
}
