package tree_sitter.c.node

import tree_sitter.Node

public sealed interface PreprocDefNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): PreprocDefNodeChildren {
            val n = createNode(node)
            if (n is PreprocDefNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a PreprocDefNodeChildren")
        }
    }
}

public class PreprocDefNode(
    override val `$node`: Node,
) : CNodeBase,
    CompoundStatementNodeChildren,
    DeclarationListNodeChildren,
    FieldDeclarationListNodeChildren,
    PreprocElifNodeChildren,
    PreprocElifdefNodeChildren,
    PreprocElseNodeChildren,
    PreprocIfNodeChildren,
    PreprocIfdefNodeChildren,
    TranslationUnitNodeChildren {
    public val name: IdentifierNode
        get() = IdentifierNode(
            `$node`.getChildByFieldName("name") ?: error("required field name is null")
        )

    public val `value`: PreprocArgNode?
        get() = (`$node`.getChildByFieldName("value"))?.let { PreprocArgNode(it) }

    public fun children(): List<PreprocDefNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            PreprocDefNodeChildren(it)
        }
    }
}
