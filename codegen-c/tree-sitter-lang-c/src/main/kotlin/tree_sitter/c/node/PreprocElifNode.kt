package tree_sitter.c.node

import tree_sitter.Node

public sealed interface PreprocElifNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): PreprocElifNodeChildren {
            val n = createNode(node)
            if (n is PreprocElifNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a PreprocElifNodeChildren")
        }
    }
}

public sealed interface PreprocElifNodeAlternative : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): PreprocElifNodeAlternative {
            val n = createNode(node)
            if (n is PreprocElifNodeAlternative) {
                return n
            }
            throw IllegalArgumentException("Node is not a PreprocElifNodeAlternative")
        }
    }
}

public sealed interface PreprocElifNodeCondition : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): PreprocElifNodeCondition {
            val n = createNode(node)
            if (n is PreprocElifNodeCondition) {
                return n
            }
            throw IllegalArgumentException("Node is not a PreprocElifNodeCondition")
        }
    }
}

public class PreprocElifNode(
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
    public val alternative: PreprocElifNodeAlternative?
        get() = (`$node`.getChildByFieldName("alternative"))?.let { PreprocElifNodeAlternative(it) }

    public val condition: PreprocElifNodeCondition
        get() = PreprocElifNodeCondition(
            `$node`.getChildByFieldName("condition") ?: error("required field condition is null")
        )

    public fun children(): List<PreprocElifNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            PreprocElifNodeChildren(it)
        }
    }
}
