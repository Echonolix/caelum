package tree_sitter.c.node

import tree_sitter.Node

public sealed interface ElseClauseNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): ElseClauseNodeChildren {
            val n = createNode(node)
            if (n is ElseClauseNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a ElseClauseNodeChildren")
        }
    }
}

public class ElseClauseNode(
    override val `$node`: Node,
) : CNodeBase,
    IfStatementNodeChildren {
    public fun children(): ElseClauseNodeChildren = ElseClauseNodeChildren(
        `$node`.getNamedChild(0u)
            ?: error("no child found for else_clause")
    )
}
