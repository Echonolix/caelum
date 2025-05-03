package tree_sitter.c.node

import tree_sitter.Node

public sealed interface GotoStatementNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): GotoStatementNodeChildren {
            val n = createNode(node)
            if (n is GotoStatementNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a GotoStatementNodeChildren")
        }
    }
}

public class GotoStatementNode(
    override val `$node`: Node,
) : CNodeBase,
    StatementNode,
    CaseStatementNodeChildren,
    TranslationUnitNodeChildren {
    public val label: StatementIdentifierNode
        get() = StatementIdentifierNode(
            `$node`.getChildByFieldName("label") ?: error("required field label is null")
        )

    public fun children(): List<GotoStatementNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            GotoStatementNodeChildren(it)
        }
    }
}
