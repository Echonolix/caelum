package org.echonolix.ktffi

class KDoc {
    val lines = mutableListOf<String>()
    val since: String? = null

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