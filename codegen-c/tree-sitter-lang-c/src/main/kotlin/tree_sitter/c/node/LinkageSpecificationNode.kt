package tree_sitter.c.node

import tree_sitter.Node

public sealed interface LinkageSpecificationNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): LinkageSpecificationNodeChildren {
            val n = createNode(node)
            if (n is LinkageSpecificationNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a LinkageSpecificationNodeChildren")
        }
    }
}

public sealed interface LinkageSpecificationNodeBody : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): LinkageSpecificationNodeBody {
            val n = createNode(node)
            if (n is LinkageSpecificationNodeBody) {
                return n
            }
            throw IllegalArgumentException("Node is not a LinkageSpecificationNodeBody")
        }
    }
}

public class LinkageSpecificationNode(
    override val `$node`: Node,
) : CNodeBase,
    CompoundStatementNodeChildren,
    DeclarationListNodeChildren,
    PreprocElifNodeChildren,
    PreprocElifdefNodeChildren,
    PreprocElseNodeChildren,
    PreprocIfNodeChildren,
    PreprocIfdefNodeChildren,
    TranslationUnitNodeChildren {
    public val body: LinkageSpecificationNodeBody
        get() = LinkageSpecificationNodeBody(
            `$node`.getChildByFieldName("body") ?: error("required field body is null")
        )

    public val `value`: StringLiteralNode
        get() = StringLiteralNode(
            `$node`.getChildByFieldName("value") ?: error("required field value is null")
        )

    public fun children(): List<LinkageSpecificationNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            LinkageSpecificationNodeChildren(it)
        }
    }
}
