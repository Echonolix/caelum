package tree_sitter.c.node

import tree_sitter.Node

public sealed interface AbstractArrayDeclaratorNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): AbstractArrayDeclaratorNodeChildren {
            val n = createNode(node)
            if (n is AbstractArrayDeclaratorNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a AbstractArrayDeclaratorNodeChildren")
        }
    }
}

public sealed interface AbstractArrayDeclaratorNodeSize : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): AbstractArrayDeclaratorNodeSize {
            val n = createNode(node)
            if (n is AbstractArrayDeclaratorNodeSize) {
                return n
            }
            throw IllegalArgumentException("Node is not a AbstractArrayDeclaratorNodeSize")
        }
    }
}

public class AbstractArrayDeclaratorNode(
    override val `$node`: Node,
) : CNodeBase,
    _AbstractDeclaratorNode {
    public val declarator: _AbstractDeclaratorNode?
        get() = (`$node`.getChildByFieldName("declarator"))?.let { _AbstractDeclaratorNode(it) }

    public val size: AbstractArrayDeclaratorNodeSize?
        get() = (`$node`.getChildByFieldName("size"))?.let { AbstractArrayDeclaratorNodeSize(it) }

    public fun children(): List<AbstractArrayDeclaratorNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            AbstractArrayDeclaratorNodeChildren(it)
        }
    }
}
