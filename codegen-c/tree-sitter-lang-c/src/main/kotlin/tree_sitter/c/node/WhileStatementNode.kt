package tree_sitter.c.node

import tree_sitter.Node

public sealed interface WhileStatementNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): WhileStatementNodeChildren {
            val n = createNode(node)
            if (n is WhileStatementNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a WhileStatementNodeChildren")
        }
    }
}

public class WhileStatementNode(
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

    public fun children(): List<WhileStatementNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            WhileStatementNodeChildren(it)
        }
    }
}
