package tree_sitter.c.node

import tree_sitter.Node

public sealed interface PreprocParamsNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): PreprocParamsNodeChildren {
            val n = createNode(node)
            if (n is PreprocParamsNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a PreprocParamsNodeChildren")
        }
    }
}

public class PreprocParamsNode(
    override val `$node`: Node,
) : CNodeBase,
    PreprocFunctionDefNodeChildren {
    public fun children(): List<PreprocParamsNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            PreprocParamsNodeChildren(it)
        }
    }
}
