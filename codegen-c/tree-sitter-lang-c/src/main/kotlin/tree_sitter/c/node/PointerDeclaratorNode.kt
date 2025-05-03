package tree_sitter.c.node

import tree_sitter.Node

public sealed interface PointerDeclaratorNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): PointerDeclaratorNodeChildren {
            val n = createNode(node)
            if (n is PointerDeclaratorNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a PointerDeclaratorNodeChildren")
        }
    }
}

public sealed interface PointerDeclaratorNodeDeclarator : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): PointerDeclaratorNodeDeclarator {
            val n = createNode(node)
            if (n is PointerDeclaratorNodeDeclarator) {
                return n
            }
            throw IllegalArgumentException("Node is not a PointerDeclaratorNodeDeclarator")
        }
    }
}

public class PointerDeclaratorNode(
    override val `$node`: Node,
) : CNodeBase,
    _DeclaratorNode,
    _FieldDeclaratorNode,
    _TypeDeclaratorNode,
    DeclarationNodeChildren,
    DeclarationNodeDeclarator {
    public val declarator: PointerDeclaratorNodeDeclarator
        get() = PointerDeclaratorNodeDeclarator(
            `$node`.getChildByFieldName("declarator") ?: error("required field declarator is null")
        )

    public fun children(): List<PointerDeclaratorNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            PointerDeclaratorNodeChildren(it)
        }
    }
}
