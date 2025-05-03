package tree_sitter.c.node

import lib.tree_sitter.TSTreeCursor
import tree_sitter.TreeCursor
import java.lang.foreign.Arena

internal inline fun <R> useCursor(action: (TreeCursor) -> R): R = Arena.ofConfined().use { alloc ->
    val cursor = TreeCursor(TSTreeCursor.allocate(alloc).`$mem`)
    val r = cursor.let(action)
    cursor.drop()
    r
}
