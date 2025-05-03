package tree_sitter.c.node

import tree_sitter.Node

public sealed interface PreprocCallNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): PreprocCallNodeChildren {
            val n = createNode(node)
            if (n is PreprocCallNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a PreprocCallNodeChildren")
        }
    }
}

public class PreprocCallNode(
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
    public val argument: PreprocArgNode?
        get() = (`$node`.getChildByFieldName("argument"))?.let { PreprocArgNode(it) }

    public val directive: PreprocDirectiveNode
        get() = PreprocDirectiveNode(
            `$node`.getChildByFieldName("directive") ?: error("required field directive is null")
        )

    public fun children(): List<PreprocCallNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            PreprocCallNodeChildren(it)
        }
    }
}
