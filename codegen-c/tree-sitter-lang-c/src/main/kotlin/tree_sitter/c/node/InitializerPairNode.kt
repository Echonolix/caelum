package tree_sitter.c.node

import tree_sitter.Node

public sealed interface InitializerPairNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): InitializerPairNodeChildren {
            val n = createNode(node)
            if (n is InitializerPairNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a InitializerPairNodeChildren")
        }
    }
}

public sealed interface InitializerPairNodeDesignator : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): InitializerPairNodeDesignator {
            val n = createNode(node)
            if (n is InitializerPairNodeDesignator) {
                return n
            }
            throw IllegalArgumentException("Node is not a InitializerPairNodeDesignator")
        }
    }
}

public sealed interface InitializerPairNodeValue : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): InitializerPairNodeValue {
            val n = createNode(node)
            if (n is InitializerPairNodeValue) {
                return n
            }
            throw IllegalArgumentException("Node is not a InitializerPairNodeValue")
        }
    }
}

public class InitializerPairNode(
    override val `$node`: Node,
) : CNodeBase,
    InitializerListNodeChildren {
    public val designator: List<InitializerPairNodeDesignator>
        get() = useCursor { cursor ->
            `$node`.childrenByFieldName("designator", cursor)
                .asSequence()
                .map { InitializerPairNodeDesignator(it) }
                .toList()
        }

    public val `value`: InitializerPairNodeValue
        get() = InitializerPairNodeValue(
            `$node`.getChildByFieldName("value") ?: error("required field value is null")
        )

    public fun children(): List<InitializerPairNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            InitializerPairNodeChildren(it)
        }
    }
}
