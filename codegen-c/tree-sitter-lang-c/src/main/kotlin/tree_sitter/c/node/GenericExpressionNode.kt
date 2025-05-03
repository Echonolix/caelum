package tree_sitter.c.node

import tree_sitter.Node

public sealed interface GenericExpressionNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): GenericExpressionNodeChildren {
            val n = createNode(node)
            if (n is GenericExpressionNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a GenericExpressionNodeChildren")
        }
    }
}

public class GenericExpressionNode(
    override val `$node`: Node,
) : CNodeBase,
    ExpressionNode {
    public fun children(): List<GenericExpressionNodeChildren> = `$node`.namedChildren.map {
        GenericExpressionNodeChildren(it)
    }
}
