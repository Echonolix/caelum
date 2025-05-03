package tree_sitter.c.node

import tree_sitter.Node

public sealed interface ExpressionStatementNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): ExpressionStatementNodeChildren {
            val n = createNode(node)
            if (n is ExpressionStatementNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a ExpressionStatementNodeChildren")
        }
    }
}

public class ExpressionStatementNode(
    override val `$node`: Node,
) : CNodeBase,
    StatementNode,
    CaseStatementNodeChildren,
    TranslationUnitNodeChildren {
    public fun children(): ExpressionStatementNodeChildren? {
        if (`$node`.namedChildCount == 0U) {
            return null
        }
        return ExpressionStatementNodeChildren(
            `$node`.getNamedChild(0u) ?: error("no child found for expression_statement")
        )
    }
}
