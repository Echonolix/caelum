package tree_sitter.c.node

import tree_sitter.Node

public sealed interface SehExceptClauseNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): SehExceptClauseNodeChildren {
            val n = createNode(node)
            if (n is SehExceptClauseNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a SehExceptClauseNodeChildren")
        }
    }
}

public class SehExceptClauseNode(
    override val `$node`: Node,
) : CNodeBase,
    SehTryStatementNodeChildren {
    public val body: CompoundStatementNode
        get() = CompoundStatementNode(
            `$node`.getChildByFieldName("body") ?: error("required field body is null")
        )

    public val filter: ParenthesizedExpressionNode
        get() = ParenthesizedExpressionNode(
            `$node`.getChildByFieldName("filter") ?: error("required field filter is null")
        )

    public fun children(): List<SehExceptClauseNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            SehExceptClauseNodeChildren(it)
        }
    }
}
