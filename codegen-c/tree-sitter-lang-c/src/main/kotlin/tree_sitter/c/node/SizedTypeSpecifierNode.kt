package tree_sitter.c.node

import tree_sitter.Node

public sealed interface SizedTypeSpecifierNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): SizedTypeSpecifierNodeChildren {
            val n = createNode(node)
            if (n is SizedTypeSpecifierNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a SizedTypeSpecifierNodeChildren")
        }
    }
}

public sealed interface SizedTypeSpecifierNodeSizeSpecifier : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): SizedTypeSpecifierNodeSizeSpecifier {
            val n = createNode(node)
            if (n is SizedTypeSpecifierNodeSizeSpecifier) {
                return n
            }
            throw IllegalArgumentException("Node is not a SizedTypeSpecifierNodeSizeSpecifier")
        }
    }
}

public sealed interface SizedTypeSpecifierNodeType : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): SizedTypeSpecifierNodeType {
            val n = createNode(node)
            if (n is SizedTypeSpecifierNodeType) {
                return n
            }
            throw IllegalArgumentException("Node is not a SizedTypeSpecifierNodeType")
        }
    }
}

public class SizedTypeSpecifierNode(
    override val `$node`: Node,
) : CNodeBase,
    TypeSpecifierNode {
    public val sizeSpecifier: List<SizedTypeSpecifierNodeSizeSpecifier>
        get() = useCursor { cursor ->
            `$node`.childrenByFieldName("size_specifier", cursor)
                .asSequence()
                .map { SizedTypeSpecifierNodeSizeSpecifier(it) }
                .toList()
        }

    public val type: SizedTypeSpecifierNodeType?
        get() = (`$node`.getChildByFieldName("type"))?.let { SizedTypeSpecifierNodeType(it) }

    public fun children(): List<SizedTypeSpecifierNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            SizedTypeSpecifierNodeChildren(it)
        }
    }
}
