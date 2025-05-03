package net.echonolix.caelum

import tree_sitter.Range


data class LineMarker(
    val lineNum: Int,
    val fileName: String,
    val newFile: Boolean,
    val returnFile: Boolean,
    val fromSysHeader: Boolean,
    val range: Range,
)