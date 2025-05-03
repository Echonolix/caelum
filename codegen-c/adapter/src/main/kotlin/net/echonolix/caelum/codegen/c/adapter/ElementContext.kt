package net.echonolix.caelum.codegen.c.adapter

import net.echonolix.caelum.AdapterASTVisitor

class ElementContext {
    fun parse(source: String) {
        val visitor = AdapterASTVisitor(this)
        c.ast.parse(source, visitor)
    }
}