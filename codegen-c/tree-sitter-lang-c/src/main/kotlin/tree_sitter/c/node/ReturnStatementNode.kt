package tree_sitter.c.node

import tree_sitter.Node

public sealed interface ReturnStatementNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): ReturnStatementNodeChildren {
            val n = createNode(node)
            if (n is ReturnStatementNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a ReturnStatementNodeChildren")
        }
    }
}

public class ReturnStatementNode(
    override val `$node`: Node,
) : CNodeBase,
    StatementNode,
    CaseStatementNodeChildren,
    TranslationUnitNodeChildren {
    public fun children(): ReturnStatementNodeChildren? {
        if (`$node`.namedChildCount == 0U) {
            return null
        }
        return ReturnStatementNodeChildren(
            `$node`.getNamedChild(0u) ?: error("no child found for return_statement")
        )
    }
}
