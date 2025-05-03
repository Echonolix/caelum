package tree_sitter.c.node

import tree_sitter.Node

public sealed interface _FieldDeclaratorNode : CNodeBase, AttributedDeclaratorNodeChildren,
    ParenthesizedDeclaratorNodeChildren, ArrayDeclaratorNodeChildren, FieldDeclarationNodeChildren,
    FunctionDeclaratorNodeChildren, PointerDeclaratorNodeChildren, ArrayDeclaratorNodeDeclarator,
    FunctionDeclaratorNodeDeclarator, PointerDeclaratorNodeDeclarator {
    public companion object {
        public operator fun invoke(node: Node): _FieldDeclaratorNode {
            val n = createNode(node)
            if (n is _FieldDeclaratorNode) {
                return n
            }
            throw IllegalArgumentException("Node is not a _FieldDeclaratorNode")
        }
    }
}
