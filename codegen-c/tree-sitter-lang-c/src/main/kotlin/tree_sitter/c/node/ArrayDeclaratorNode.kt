package tree_sitter.c.node

import tree_sitter.Node

public sealed interface ArrayDeclaratorNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): ArrayDeclaratorNodeChildren {
            val n = createNode(node)
            if (n is ArrayDeclaratorNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a ArrayDeclaratorNodeChildren")
        }
    }
}

public sealed interface ArrayDeclaratorNodeDeclarator : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): ArrayDeclaratorNodeDeclarator {
            val n = createNode(node)
            if (n is ArrayDeclaratorNodeDeclarator) {
                return n
            }
            throw IllegalArgumentException("Node is not a ArrayDeclaratorNodeDeclarator")
        }
    }
}

public sealed interface ArrayDeclaratorNodeSize : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): ArrayDeclaratorNodeSize {
            val n = createNode(node)
            if (n is ArrayDeclaratorNodeSize) {
                return n
            }
            throw IllegalArgumentException("Node is not a ArrayDeclaratorNodeSize")
        }
    }
}

public class ArrayDeclaratorNode(
    override val `$node`: Node,
) : CNodeBase,
    _DeclaratorNode,
    _FieldDeclaratorNode,
    _TypeDeclaratorNode,
    DeclarationNodeChildren,
    DeclarationNodeDeclarator {
    public val declarator: ArrayDeclaratorNodeDeclarator
        get() = ArrayDeclaratorNodeDeclarator(
            `$node`.getChildByFieldName("declarator") ?: error("required field declarator is null")
        )

    public val size: ArrayDeclaratorNodeSize?
        get() = (`$node`.getChildByFieldName("size"))?.let { ArrayDeclaratorNodeSize(it) }

    public fun children(): List<ArrayDeclaratorNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            ArrayDeclaratorNodeChildren(it)
        }
    }
}
