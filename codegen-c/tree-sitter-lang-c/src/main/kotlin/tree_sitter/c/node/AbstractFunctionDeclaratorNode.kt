package tree_sitter.c.node

import tree_sitter.Node

public sealed interface AbstractFunctionDeclaratorNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): AbstractFunctionDeclaratorNodeChildren {
            val n = createNode(node)
            if (n is AbstractFunctionDeclaratorNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a AbstractFunctionDeclaratorNodeChildren")
        }
    }
}

public class AbstractFunctionDeclaratorNode(
    override val `$node`: Node,
) : CNodeBase,
    _AbstractDeclaratorNode {
    public val declarator: _AbstractDeclaratorNode?
        get() = (`$node`.getChildByFieldName("declarator"))?.let { _AbstractDeclaratorNode(it) }

    public val parameters: ParameterListNode
        get() = ParameterListNode(
            `$node`.getChildByFieldName("parameters") ?: error("required field parameters is null")
        )

    public fun children(): List<AbstractFunctionDeclaratorNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            AbstractFunctionDeclaratorNodeChildren(it)
        }
    }
}
