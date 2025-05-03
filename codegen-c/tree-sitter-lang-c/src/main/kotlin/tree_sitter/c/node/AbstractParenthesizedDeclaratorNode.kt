package tree_sitter.c.node

import tree_sitter.Node

public sealed interface AbstractParenthesizedDeclaratorNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): AbstractParenthesizedDeclaratorNodeChildren {
            val n = createNode(node)
            if (n is AbstractParenthesizedDeclaratorNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a AbstractParenthesizedDeclaratorNodeChildren")
        }
    }
}

public class AbstractParenthesizedDeclaratorNode(
    override val `$node`: Node,
) : CNodeBase,
    _AbstractDeclaratorNode {
    public fun children(): List<AbstractParenthesizedDeclaratorNodeChildren> =
        `$node`.namedChildren.map {
            AbstractParenthesizedDeclaratorNodeChildren(it)
        }
}
