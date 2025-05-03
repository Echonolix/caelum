package tree_sitter.c.node

import tree_sitter.Node

public sealed interface PreprocFunctionDefNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): PreprocFunctionDefNodeChildren {
            val n = createNode(node)
            if (n is PreprocFunctionDefNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a PreprocFunctionDefNodeChildren")
        }
    }
}

public class PreprocFunctionDefNode(
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

    public val parameters: PreprocParamsNode
        get() = PreprocParamsNode(
            `$node`.getChildByFieldName("parameters") ?: error("required field parameters is null")
        )

    public val `value`: PreprocArgNode?
        get() = (`$node`.getChildByFieldName("value"))?.let { PreprocArgNode(it) }

    public fun children(): List<PreprocFunctionDefNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            PreprocFunctionDefNodeChildren(it)
        }
    }
}
