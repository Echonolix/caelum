package tree_sitter.c.node

import tree_sitter.Node

public sealed interface AttributedDeclaratorNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): AttributedDeclaratorNodeChildren {
            val n = createNode(node)
            if (n is AttributedDeclaratorNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a AttributedDeclaratorNodeChildren")
        }
    }
}

public class AttributedDeclaratorNode(
    override val `$node`: Node,
) : CNodeBase,
    _DeclaratorNode,
    _FieldDeclaratorNode,
    _TypeDeclaratorNode,
    DeclarationNodeChildren,
    DeclarationNodeDeclarator {
    public fun children(): List<AttributedDeclaratorNodeChildren> = `$node`.namedChildren.map {
        AttributedDeclaratorNodeChildren(it)
    }
}
