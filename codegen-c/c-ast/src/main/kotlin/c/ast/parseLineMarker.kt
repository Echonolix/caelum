package c.ast

import c.ast.visitor.ASTVisitor
import tree_sitter.c.node.PreprocCallNode

private val pattern = Regex("# (?<linenum>\\d+) \"(?<path>(((.+\\..+))|(<.*>)))\"(?<flags>( \\d)*)")

context(ParseContext)
fun parseLineMarker(node: PreprocCallNode, visitor: ASTVisitor) {
    val match = pattern.matchEntire(node.content().trim()) ?: return
    val linenum = match.groups["linenum"]!!.value.toInt()
    val path = match.groups["path"]!!.value
    val flags = (match.groups["flags"]?.value ?: "").split(" ").filter { it.isNotEmpty() }.map { it.toInt() }

    val newFile = flags.contains(1)
    val returnFile = flags.contains(2)
    val fromSysHeader = flags.contains(3)
    visitor.visitLineMarker(linenum, path, newFile, returnFile, fromSysHeader, node.`$node`.range)
}