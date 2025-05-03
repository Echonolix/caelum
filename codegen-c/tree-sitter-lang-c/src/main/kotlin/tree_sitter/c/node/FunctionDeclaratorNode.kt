package tree_sitter.c.node

import tree_sitter.Node

public sealed interface FunctionDeclaratorNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): FunctionDeclaratorNodeChildren {
            val n = createNode(node)
            if (n is FunctionDeclaratorNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a FunctionDeclaratorNodeChildren")
        }
    }
}

public sealed interface FunctionDeclaratorNodeDeclarator : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): FunctionDeclaratorNodeDeclarator {
            val n = createNode(node)
            if (n is FunctionDeclaratorNodeDeclarator) {
                return n
            }
            throw IllegalArgumentException("Node is not a FunctionDeclaratorNodeDeclarator")
        }
    }
}

public class FunctionDeclaratorNode(
    override val `$node`: Node,
) : CNodeBase,
    _DeclaratorNode,
    _FieldDeclaratorNode,
    _TypeDeclaratorNode,
    DeclarationNodeChildren,
    DeclarationNodeDeclarator {
    public val declarator: FunctionDeclaratorNodeDeclarator
        get() = FunctionDeclaratorNodeDeclarator(
            `$node`.getChildByFieldName("declarator") ?: error("required field declarator is null")
        )

    public val parameters: ParameterListNode
        get() = ParameterListNode(
            `$node`.getChildByFieldName("parameters") ?: error("required field parameters is null")
        )

    public fun children(): List<FunctionDeclaratorNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            FunctionDeclaratorNodeChildren(it)
        }
    }
}
