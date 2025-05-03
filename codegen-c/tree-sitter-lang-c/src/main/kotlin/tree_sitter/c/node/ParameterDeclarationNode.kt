package tree_sitter.c.node

import tree_sitter.Node

public sealed interface ParameterDeclarationNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): ParameterDeclarationNodeChildren {
            val n = createNode(node)
            if (n is ParameterDeclarationNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a ParameterDeclarationNodeChildren")
        }
    }
}

public sealed interface ParameterDeclarationNodeDeclarator : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): ParameterDeclarationNodeDeclarator {
            val n = createNode(node)
            if (n is ParameterDeclarationNodeDeclarator) {
                return n
            }
            throw IllegalArgumentException("Node is not a ParameterDeclarationNodeDeclarator")
        }
    }
}

public class ParameterDeclarationNode(
    override val `$node`: Node,
) : CNodeBase,
    ParameterListNodeChildren {
    public val declarator: ParameterDeclarationNodeDeclarator?
        get() = (`$node`.getChildByFieldName("declarator"))?.let {
            ParameterDeclarationNodeDeclarator(it)
        }

    public val type: TypeSpecifierNode
        get() = TypeSpecifierNode(
            `$node`.getChildByFieldName("type") ?: error("required field type is null")
        )

    public fun children(): List<ParameterDeclarationNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            ParameterDeclarationNodeChildren(it)
        }
    }
}
