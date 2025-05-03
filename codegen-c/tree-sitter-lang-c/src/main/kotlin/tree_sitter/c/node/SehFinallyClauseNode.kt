package tree_sitter.c.node

import tree_sitter.Node

public sealed interface SehFinallyClauseNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): SehFinallyClauseNodeChildren {
            val n = createNode(node)
            if (n is SehFinallyClauseNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a SehFinallyClauseNodeChildren")
        }
    }
}

public class SehFinallyClauseNode(
    override val `$node`: Node,
) : CNodeBase,
    SehTryStatementNodeChildren {
    public val body: CompoundStatementNode
        get() = CompoundStatementNode(
            `$node`.getChildByFieldName("body") ?: error("required field body is null")
        )

    public fun children(): List<SehFinallyClauseNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            SehFinallyClauseNodeChildren(it)
        }
    }
}
