package tree_sitter.c.node

import tree_sitter.Node

public sealed interface DeclarationNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): DeclarationNodeChildren {
            val n = createNode(node)
            if (n is DeclarationNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a DeclarationNodeChildren")
        }
    }
}

public sealed interface DeclarationNodeDeclarator : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): DeclarationNodeDeclarator {
            val n = createNode(node)
            if (n is DeclarationNodeDeclarator) {
                return n
            }
            throw IllegalArgumentException("Node is not a DeclarationNodeDeclarator")
        }
    }
}

public class DeclarationNode(
    override val `$node`: Node,
) : CNodeBase,
    CaseStatementNodeChildren,
    CompoundStatementNodeChildren,
    DeclarationListNodeChildren,
    FunctionDefinitionNodeChildren,
    LabeledStatementNodeChildren,
    PreprocElifNodeChildren,
    PreprocElifdefNodeChildren,
    PreprocElseNodeChildren,
    PreprocIfNodeChildren,
    PreprocIfdefNodeChildren,
    TranslationUnitNodeChildren,
    ForStatementNodeChildren,
    LinkageSpecificationNodeChildren,
    ForStatementNodeInitializer,
    LinkageSpecificationNodeBody {
    public val declarator: List<DeclarationNodeDeclarator>
        get() = useCursor { cursor ->
            `$node`.childrenByFieldName("declarator", cursor)
                .asSequence()
                .map { DeclarationNodeDeclarator(it) }
                .toList()
        }

    public val type: TypeSpecifierNode
        get() = TypeSpecifierNode(
            `$node`.getChildByFieldName("type") ?: error("required field type is null")
        )

    public fun children(): List<DeclarationNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            DeclarationNodeChildren(it)
        }
    }
}
