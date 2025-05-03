package tree_sitter.c.node

import tree_sitter.Node

public sealed interface DoStatementNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): DoStatementNodeChildren {
            val n = createNode(node)
            if (n is DoStatementNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a DoStatementNodeChildren")
        }
    }
}

public class DoStatementNode(
    override val `$node`: Node,
) : CNodeBase,
    StatementNode,
    CaseStatementNodeChildren,
    TranslationUnitNodeChildren {
    public val body: StatementNode
        get() = StatementNode(
            `$node`.getChildByFieldName("body") ?: error("required field body is null")
        )

    public val condition: ParenthesizedExpressionNode
        get() = ParenthesizedExpressionNode(
            `$node`.getChildByFieldName("condition") ?: error("required field condition is null")
        )

    public fun children(): List<DoStatementNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            DoStatementNodeChildren(it)
        }
    }
}
