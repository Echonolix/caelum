package tree_sitter.c.node

import tree_sitter.Node

public sealed interface FieldDeclarationNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): FieldDeclarationNodeChildren {
            val n = createNode(node)
            if (n is FieldDeclarationNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a FieldDeclarationNodeChildren")
        }
    }
}

public class FieldDeclarationNode(
    override val `$node`: Node,
) : CNodeBase,
    FieldDeclarationListNodeChildren,
    PreprocElifNodeChildren,
    PreprocElifdefNodeChildren,
    PreprocElseNodeChildren,
    PreprocIfNodeChildren,
    PreprocIfdefNodeChildren {
    public val declarator: List<_FieldDeclaratorNode>
        get() = useCursor { cursor ->
            `$node`.childrenByFieldName("declarator", cursor)
                .asSequence()
                .map { _FieldDeclaratorNode(it) }
                .toList()
        }

    public val type: TypeSpecifierNode
        get() = TypeSpecifierNode(
            `$node`.getChildByFieldName("type") ?: error("required field type is null")
        )

    public fun children(): List<FieldDeclarationNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            FieldDeclarationNodeChildren(it)
        }
    }
}
