package tree_sitter.c.node

import tree_sitter.Node

public sealed interface _DeclaratorNode : CNodeBase, AttributedDeclaratorNodeChildren,
    ParenthesizedDeclaratorNodeChildren, ArrayDeclaratorNodeChildren,
    FunctionDeclaratorNodeChildren, FunctionDefinitionNodeChildren, InitDeclaratorNodeChildren,
    ParameterDeclarationNodeChildren, PointerDeclaratorNodeChildren, ArrayDeclaratorNodeDeclarator,
    FunctionDeclaratorNodeDeclarator, ParameterDeclarationNodeDeclarator,
    PointerDeclaratorNodeDeclarator {
    public companion object {
        public operator fun invoke(node: Node): _DeclaratorNode {
            val n = createNode(node)
            if (n is _DeclaratorNode) {
                return n
            }
            throw IllegalArgumentException("Node is not a _DeclaratorNode")
        }
    }
}
