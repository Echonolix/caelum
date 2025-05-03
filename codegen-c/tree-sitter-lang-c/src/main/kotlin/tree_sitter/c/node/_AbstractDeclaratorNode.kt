package tree_sitter.c.node

import tree_sitter.Node

public sealed interface _AbstractDeclaratorNode : CNodeBase,
    AbstractParenthesizedDeclaratorNodeChildren, AbstractArrayDeclaratorNodeChildren,
    AbstractFunctionDeclaratorNodeChildren, AbstractPointerDeclaratorNodeChildren,
    ParameterDeclarationNodeChildren, TypeDescriptorNodeChildren, ParameterDeclarationNodeDeclarator {
    public companion object {
        public operator fun invoke(node: Node): _AbstractDeclaratorNode {
            val n = createNode(node)
            if (n is _AbstractDeclaratorNode) {
                return n
            }
            throw IllegalArgumentException("Node is not a _AbstractDeclaratorNode")
        }
    }
}
