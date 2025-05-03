package net.echonolix.caelum.codegen.c.adapter

import tree_sitter.Node
import tree_sitter.Range
import tree_sitter.c.node.CNodeBase
import kotlin.io.path.Path

data class LineMarker(
    val lineNum: Int,
    val fileName: String,
    val newFile: Boolean,
    val returnFile: Boolean,
    val fromSysHeader: Boolean,
    val range: Range
) {
    fun filePath() = Path(fileName)

    /**
     * @return the line num at original source
     */
    fun posOf(ast: CNodeBase): Int {
        val offsetLine = ast.`$node`.range.endPoint.row - range.endPoint.row
        return lineNum + offsetLine.toInt()
    }

    /**
     * @return the line num at original source
     */
    fun posOf(node: Node): Int {
        val offsetLine = node.range.endPoint.row - range.endPoint.row
        return lineNum + offsetLine.toInt()
    }
}