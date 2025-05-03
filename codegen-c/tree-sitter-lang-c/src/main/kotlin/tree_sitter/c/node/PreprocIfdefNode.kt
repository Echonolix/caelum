package tree_sitter.c.node

import tree_sitter.Node

public sealed interface PreprocIfdefNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): PreprocIfdefNodeChildren {
            val n = createNode(node)
            if (n is PreprocIfdefNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a PreprocIfdefNodeChildren")
        }
    }
}

public sealed interface PreprocIfdefNodeAlternative : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): PreprocIfdefNodeAlternative {
            val n = createNode(node)
            if (n is PreprocIfdefNodeAlternative) {
                return n
            }
            throw IllegalArgumentException("Node is not a PreprocIfdefNodeAlternative")
        }
    }
}

public class PreprocIfdefNode(
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
    public val alternative: PreprocIfdefNodeAlternative?
        get() = (`$node`.getChildByFieldName("alternative"))?.let { PreprocIfdefNodeAlternative(it) }

    public val name: IdentifierNode
        get() = IdentifierNode(
            `$node`.getChildByFieldName("name") ?: error("required field name is null")
        )

    public fun children(): List<PreprocIfdefNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            PreprocIfdefNodeChildren(it)
        }
    }
}
