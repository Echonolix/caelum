package net.echonolix.ktffi

class KDoc {
    val lines = mutableListOf<String>()
    var since: String? = null

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