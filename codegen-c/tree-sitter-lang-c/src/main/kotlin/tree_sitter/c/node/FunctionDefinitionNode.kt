package tree_sitter.c.node

import tree_sitter.Node

public sealed interface FunctionDefinitionNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): FunctionDefinitionNodeChildren {
            val n = createNode(node)
            if (n is FunctionDefinitionNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a FunctionDefinitionNodeChildren")
        }
    }
}

public class FunctionDefinitionNode(
    override val `$node`: Node,
) : CNodeBase,
    CompoundStatementNodeChildren,
    DeclarationListNodeChildren,
    PreprocElifNodeChildren,
    PreprocElifdefNodeChildren,
    PreprocElseNodeChildren,
    PreprocIfNodeChildren,
    PreprocIfdefNodeChildren,
    TranslationUnitNodeChildren,
    LinkageSpecificationNodeChildren,
    LinkageSpecificationNodeBody {
    public val body: CompoundStatementNode
        get() = CompoundStatementNode(
            `$node`.getChildByFieldName("body") ?: error("required field body is null")
        )

    public val declarator: _DeclaratorNode
        get() = _DeclaratorNode(
            `$node`.getChildByFieldName("declarator") ?: error("required field declarator is null")
        )

    public val type: TypeSpecifierNode
        get() = TypeSpecifierNode(
            `$node`.getChildByFieldName("type") ?: error("required field type is null")
        )

    public fun children(): List<FunctionDefinitionNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            FunctionDefinitionNodeChildren(it)
        }
    }
}
