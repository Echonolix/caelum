package tree_sitter.c.node

import tree_sitter.Node

public sealed interface SwitchStatementNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): SwitchStatementNodeChildren {
            val n = createNode(node)
            if (n is SwitchStatementNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a SwitchStatementNodeChildren")
        }
    }
}

public class SwitchStatementNode(
    override val `$node`: Node,
) : CNodeBase,
    StatementNode,
    CaseStatementNodeChildren,
    TranslationUnitNodeChildren {
    public val body: CompoundStatementNode
        get() = CompoundStatementNode(
            `$node`.getChildByFieldName("body") ?: error("required field body is null")
        )

    public val condition: ParenthesizedExpressionNode
        get() = ParenthesizedExpressionNode(
            `$node`.getChildByFieldName("condition") ?: error("required field condition is null")
        )

    public fun children(): List<SwitchStatementNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            SwitchStatementNodeChildren(it)
        }
    }
}
