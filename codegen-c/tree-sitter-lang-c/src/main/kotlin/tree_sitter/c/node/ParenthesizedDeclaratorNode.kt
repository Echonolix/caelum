package tree_sitter.c.node

import tree_sitter.Node

public sealed interface ParenthesizedDeclaratorNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): ParenthesizedDeclaratorNodeChildren {
            val n = createNode(node)
            if (n is ParenthesizedDeclaratorNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a ParenthesizedDeclaratorNodeChildren")
        }
    }
}

public class ParenthesizedDeclaratorNode(
    override val `$node`: Node,
) : CNodeBase,
    _DeclaratorNode,
    _FieldDeclaratorNode,
    _TypeDeclaratorNode,
    DeclarationNodeChildren,
    DeclarationNodeDeclarator {
    public fun children(): List<ParenthesizedDeclaratorNodeChildren> = `$node`.namedChildren.map {
        ParenthesizedDeclaratorNodeChildren(it)
    }
}
