package tree_sitter.c.node

import tree_sitter.Node

public sealed interface IfStatementNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): IfStatementNodeChildren {
            val n = createNode(node)
            if (n is IfStatementNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a IfStatementNodeChildren")
        }
    }
}

public class IfStatementNode(
    override val `$node`: Node,
) : CNodeBase,
    StatementNode,
    CaseStatementNodeChildren,
    TranslationUnitNodeChildren {
    public val alternative: ElseClauseNode?
        get() = (`$node`.getChildByFieldName("alternative"))?.let { ElseClauseNode(it) }

    public val condition: ParenthesizedExpressionNode
        get() = ParenthesizedExpressionNode(
            `$node`.getChildByFieldName("condition") ?: error("required field condition is null")
        )

    public val consequence: StatementNode
        get() = StatementNode(
            `$node`.getChildByFieldName("consequence") ?: error("required field consequence is null")
        )

    public fun children(): List<IfStatementNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            IfStatementNodeChildren(it)
        }
    }
}
