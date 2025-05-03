package tree_sitter.c.node

import tree_sitter.Node

public sealed interface InitDeclaratorNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): InitDeclaratorNodeChildren {
            val n = createNode(node)
            if (n is InitDeclaratorNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a InitDeclaratorNodeChildren")
        }
    }
}

public sealed interface InitDeclaratorNodeValue : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): InitDeclaratorNodeValue {
            val n = createNode(node)
            if (n is InitDeclaratorNodeValue) {
                return n
            }
            throw IllegalArgumentException("Node is not a InitDeclaratorNodeValue")
        }
    }
}

public class InitDeclaratorNode(
    override val `$node`: Node,
) : CNodeBase,
    DeclarationNodeChildren,
    DeclarationNodeDeclarator {
    public val declarator: _DeclaratorNode
        get() = _DeclaratorNode(
            `$node`.getChildByFieldName("declarator") ?: error("required field declarator is null")
        )

    public val `value`: InitDeclaratorNodeValue
        get() = InitDeclaratorNodeValue(
            `$node`.getChildByFieldName("value") ?: error("required field value is null")
        )

    public fun children(): List<InitDeclaratorNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            InitDeclaratorNodeChildren(it)
        }
    }
}
