package tree_sitter.c.node

import tree_sitter.Node

public sealed interface ConditionalExpressionNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): ConditionalExpressionNodeChildren {
            val n = createNode(node)
            if (n is ConditionalExpressionNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a ConditionalExpressionNodeChildren")
        }
    }
}

public sealed interface ConditionalExpressionNodeConsequence : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): ConditionalExpressionNodeConsequence {
            val n = createNode(node)
            if (n is ConditionalExpressionNodeConsequence) {
                return n
            }
            throw IllegalArgumentException("Node is not a ConditionalExpressionNodeConsequence")
        }
    }
}

public class ConditionalExpressionNode(
    override val `$node`: Node,
) : CNodeBase,
    ExpressionNode {
    public val alternative: ExpressionNode
        get() = ExpressionNode(
            `$node`.getChildByFieldName("alternative") ?: error("required field alternative is null")
        )

    public val condition: ExpressionNode
        get() = ExpressionNode(
            `$node`.getChildByFieldName("condition") ?: error("required field condition is null")
        )

    public val consequence: ConditionalExpressionNodeConsequence?
        get() = (`$node`.getChildByFieldName("consequence"))?.let {
            ConditionalExpressionNodeConsequence(it)
        }

    public fun children(): List<ConditionalExpressionNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            ConditionalExpressionNodeChildren(it)
        }
    }
}
