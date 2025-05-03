package tree_sitter.c.node

import tree_sitter.Node

public sealed interface CaseStatementNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): CaseStatementNodeChildren {
            val n = createNode(node)
            if (n is CaseStatementNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a CaseStatementNodeChildren")
        }
    }
}

public class CaseStatementNode(
    override val `$node`: Node,
) : CNodeBase,
    StatementNode,
    TranslationUnitNodeChildren {
    public val `value`: ExpressionNode?
        get() = (`$node`.getChildByFieldName("value"))?.let { ExpressionNode(it) }

    public fun children(): List<CaseStatementNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            CaseStatementNodeChildren(it)
        }
    }
}
