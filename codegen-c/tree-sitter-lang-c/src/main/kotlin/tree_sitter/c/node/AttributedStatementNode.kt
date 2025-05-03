package tree_sitter.c.node

import tree_sitter.Node

public sealed interface AttributedStatementNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): AttributedStatementNodeChildren {
            val n = createNode(node)
            if (n is AttributedStatementNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a AttributedStatementNodeChildren")
        }
    }
}

public class AttributedStatementNode(
    override val `$node`: Node,
) : CNodeBase,
    StatementNode,
    CaseStatementNodeChildren,
    TranslationUnitNodeChildren {
    public fun children(): List<AttributedStatementNodeChildren> = `$node`.namedChildren.map {
        AttributedStatementNodeChildren(it)
    }
}
