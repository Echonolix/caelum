package tree_sitter.c.node

import tree_sitter.Node

public sealed interface PreprocElifdefNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): PreprocElifdefNodeChildren {
            val n = createNode(node)
            if (n is PreprocElifdefNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a PreprocElifdefNodeChildren")
        }
    }
}

public sealed interface PreprocElifdefNodeAlternative : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): PreprocElifdefNodeAlternative {
            val n = createNode(node)
            if (n is PreprocElifdefNodeAlternative) {
                return n
            }
            throw IllegalArgumentException("Node is not a PreprocElifdefNodeAlternative")
        }
    }
}

public class PreprocElifdefNode(
    override val `$node`: Node,
) : CNodeBase,
    PreprocElifNodeChildren,
    PreprocElifdefNodeChildren,
    PreprocIfNodeChildren,
    PreprocIfdefNodeChildren,
    PreprocElifNodeAlternative,
    PreprocElifdefNodeAlternative,
    PreprocIfNodeAlternative,
    PreprocIfdefNodeAlternative {
    public val alternative: PreprocElifdefNodeAlternative?
        get() = (`$node`.getChildByFieldName("alternative"))?.let { PreprocElifdefNodeAlternative(it) }

    public val name: IdentifierNode
        get() = IdentifierNode(
            `$node`.getChildByFieldName("name") ?: error("required field name is null")
        )

    public fun children(): List<PreprocElifdefNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            PreprocElifdefNodeChildren(it)
        }
    }
}
