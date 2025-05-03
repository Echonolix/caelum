package tree_sitter.c.node

import tree_sitter.Node

public sealed interface AbstractPointerDeclaratorNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): AbstractPointerDeclaratorNodeChildren {
            val n = createNode(node)
            if (n is AbstractPointerDeclaratorNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a AbstractPointerDeclaratorNodeChildren")
        }
    }
}

public class AbstractPointerDeclaratorNode(
    override val `$node`: Node,
) : CNodeBase,
    _AbstractDeclaratorNode {
    public val declarator: _AbstractDeclaratorNode?
        get() = (`$node`.getChildByFieldName("declarator"))?.let { _AbstractDeclaratorNode(it) }

    public fun children(): List<AbstractPointerDeclaratorNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            AbstractPointerDeclaratorNodeChildren(it)
        }
    }
}
