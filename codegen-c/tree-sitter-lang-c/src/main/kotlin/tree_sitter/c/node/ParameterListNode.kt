package tree_sitter.c.node

import tree_sitter.Node

public sealed interface ParameterListNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): ParameterListNodeChildren {
            val n = createNode(node)
            if (n is ParameterListNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a ParameterListNodeChildren")
        }
    }
}

public class ParameterListNode(
    override val `$node`: Node,
) : CNodeBase,
    AbstractFunctionDeclaratorNodeChildren,
    FunctionDeclaratorNodeChildren {
    public fun children(): List<ParameterListNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            ParameterListNodeChildren(it)
        }
    }
}
