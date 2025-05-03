package tree_sitter.c.node

import tree_sitter.Node

public sealed interface InitializerListNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): InitializerListNodeChildren {
            val n = createNode(node)
            if (n is InitializerListNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a InitializerListNodeChildren")
        }
    }
}

public class InitializerListNode(
    override val `$node`: Node,
) : CNodeBase,
    InitializerListNodeChildren,
    CompoundLiteralExpressionNodeChildren,
    InitDeclaratorNodeChildren,
    InitializerPairNodeChildren,
    InitDeclaratorNodeValue,
    InitializerPairNodeValue {
    public fun children(): List<InitializerListNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            InitializerListNodeChildren(it)
        }
    }
}
