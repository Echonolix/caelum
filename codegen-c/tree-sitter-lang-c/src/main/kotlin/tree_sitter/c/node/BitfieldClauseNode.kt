package tree_sitter.c.node

import tree_sitter.Node

public sealed interface BitfieldClauseNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): BitfieldClauseNodeChildren {
            val n = createNode(node)
            if (n is BitfieldClauseNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a BitfieldClauseNodeChildren")
        }
    }
}

public class BitfieldClauseNode(
    override val `$node`: Node,
) : CNodeBase,
    FieldDeclarationNodeChildren {
    public fun children(): BitfieldClauseNodeChildren =
        BitfieldClauseNodeChildren(
            `$node`.getNamedChild(0u) ?: error("no child found for bitfield_clause")
        )
}
