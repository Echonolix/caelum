package net.echonolix.caelum.codegen.api

public class KDoc {
    public val lines: MutableList<String> = mutableListOf()
    public var since: String? = null

    override fun toString(): String {
        return buildString {
            lines.forEach {
                appendLine(it)
            }
            appendLine()
            if (since != null) {
                append("@since: ")
                appendLine(since)
            }
        }
    }
}