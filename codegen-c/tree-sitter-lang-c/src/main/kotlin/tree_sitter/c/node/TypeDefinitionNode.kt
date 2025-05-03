package tree_sitter.c.node

import tree_sitter.Node

public sealed interface TypeDefinitionNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): TypeDefinitionNodeChildren {
            val n = createNode(node)
            if (n is TypeDefinitionNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a TypeDefinitionNodeChildren")
        }
    }
}

public class TypeDefinitionNode(
    override val `$node`: Node,
) : CNodeBase,
    CaseStatementNodeChildren,
    CompoundStatementNodeChildren,
    DeclarationListNodeChildren,
    PreprocElifNodeChildren,
    PreprocElifdefNodeChildren,
    PreprocElseNodeChildren,
    PreprocIfNodeChildren,
    PreprocIfdefNodeChildren,
    TranslationUnitNodeChildren {
    public val declarator: List<_TypeDeclaratorNode>
        get() = useCursor { cursor ->
            `$node`.childrenByFieldName("declarator", cursor)
                .asSequence()
                .map { _TypeDeclaratorNode(it) }
                .toList()
        }

    public val type: TypeSpecifierNode
        get() = TypeSpecifierNode(
            `$node`.getChildByFieldName("type") ?: error("required field type is null")
        )

    public fun children(): List<TypeDefinitionNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            TypeDefinitionNodeChildren(it)
        }
    }
}
