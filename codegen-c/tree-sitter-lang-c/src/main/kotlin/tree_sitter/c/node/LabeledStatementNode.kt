package tree_sitter.c.node

import tree_sitter.Node

public sealed interface LabeledStatementNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): LabeledStatementNodeChildren {
            val n = createNode(node)
            if (n is LabeledStatementNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a LabeledStatementNodeChildren")
        }
    }
}

public class LabeledStatementNode(
    override val `$node`: Node,
) : CNodeBase,
    StatementNode,
    CaseStatementNodeChildren,
    TranslationUnitNodeChildren {
    public val label: StatementIdentifierNode
        get() = StatementIdentifierNode(
            `$node`.getChildByFieldName("label") ?: error("required field label is null")
        )

    public fun children(): LabeledStatementNodeChildren =
        LabeledStatementNodeChildren(
            `$node`.getNamedChild(0u) ?: error("no child found for labeled_statement")
        )
}
