package tree_sitter.c.node

import tree_sitter.Node

public sealed interface SehTryStatementNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): SehTryStatementNodeChildren {
            val n = createNode(node)
            if (n is SehTryStatementNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a SehTryStatementNodeChildren")
        }
    }
}

public class SehTryStatementNode(
    override val `$node`: Node,
) : CNodeBase,
    StatementNode,
    CaseStatementNodeChildren {
    public val body: CompoundStatementNode
        get() = CompoundStatementNode(
            `$node`.getChildByFieldName("body") ?: error("required field body is null")
        )

    public fun children(): SehTryStatementNodeChildren =
        SehTryStatementNodeChildren(
            `$node`.getNamedChild(0u) ?: error("no child found for seh_try_statement")
        )
}
