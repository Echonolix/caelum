package tree_sitter.c.node

import tree_sitter.Node

public sealed interface CharLiteralNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): CharLiteralNodeChildren {
            val n = createNode(node)
            if (n is CharLiteralNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a CharLiteralNodeChildren")
        }
    }
}

public class CharLiteralNode(
    override val `$node`: Node,
) : CNodeBase,
    ExpressionNode,
    PreprocElifNodeChildren,
    PreprocIfNodeChildren,
    PreprocElifNodeCondition,
    PreprocIfNodeCondition {
    public fun children(): List<CharLiteralNodeChildren> = `$node`.namedChildren.map {
        CharLiteralNodeChildren(it)
    }
}
