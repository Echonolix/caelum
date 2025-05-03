package tree_sitter.c.node

import tree_sitter.Node

public sealed interface PreprocIfNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): PreprocIfNodeChildren {
            val n = createNode(node)
            if (n is PreprocIfNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a PreprocIfNodeChildren")
        }
    }
}

public sealed interface PreprocIfNodeAlternative : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): PreprocIfNodeAlternative {
            val n = createNode(node)
            if (n is PreprocIfNodeAlternative) {
                return n
            }
            throw IllegalArgumentException("Node is not a PreprocIfNodeAlternative")
        }
    }
}

public sealed interface PreprocIfNodeCondition : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): PreprocIfNodeCondition {
            val n = createNode(node)
            if (n is PreprocIfNodeCondition) {
                return n
            }
            throw IllegalArgumentException("Node is not a PreprocIfNodeCondition")
        }
    }
}

public class PreprocIfNode(
    override val `$node`: Node,
) : CNodeBase,
    CompoundStatementNodeChildren,
    DeclarationListNodeChildren,
    EnumeratorListNodeChildren,
    FieldDeclarationListNodeChildren,
    PreprocElifNodeChildren,
    PreprocElifdefNodeChildren,
    PreprocElseNodeChildren,
    PreprocIfNodeChildren,
    PreprocIfdefNodeChildren,
    TranslationUnitNodeChildren {
    public val alternative: PreprocIfNodeAlternative?
        get() = (`$node`.getChildByFieldName("alternative"))?.let { PreprocIfNodeAlternative(it) }

    public val condition: PreprocIfNodeCondition
        get() = PreprocIfNodeCondition(
            `$node`.getChildByFieldName("condition") ?: error("required field condition is null")
        )

    public fun children(): List<PreprocIfNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            PreprocIfNodeChildren(it)
        }
    }
}
