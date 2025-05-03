package tree_sitter.c.node

import tree_sitter.Node

public sealed interface _TypeDeclaratorNode : CNodeBase, AttributedDeclaratorNodeChildren,
    ParenthesizedDeclaratorNodeChildren, ArrayDeclaratorNodeChildren,
    FunctionDeclaratorNodeChildren, PointerDeclaratorNodeChildren, TypeDefinitionNodeChildren,
    ArrayDeclaratorNodeDeclarator, FunctionDeclaratorNodeDeclarator, PointerDeclaratorNodeDeclarator {
    public companion object {
        public operator fun invoke(node: Node): _TypeDeclaratorNode {
            val n = createNode(node)
            if (n is _TypeDeclaratorNode) {
                return n
            }
            throw IllegalArgumentException("Node is not a _TypeDeclaratorNode")
        }
    }
}
