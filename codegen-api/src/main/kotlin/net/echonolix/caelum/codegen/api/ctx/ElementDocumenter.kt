package net.echonolix.caelum.codegen.api.ctx

import com.squareup.kotlinpoet.Documentable
import net.echonolix.caelum.codegen.api.CElement
import net.echonolix.caelum.codegen.api.ElementCommentTag

public interface ElementDocumenter {
    context(ctx: CodegenContext)
    public fun buildKdocStr(sb: StringBuilder, element: CElement)

    public open class Base : ElementDocumenter {
        context(ctx: CodegenContext)
        override fun buildKdocStr(sb: StringBuilder, element: CElement) {
            val elementComment = element.tags.get<ElementCommentTag>()?.comment
            if (elementComment != null) {
                sb.appendLine(elementComment.removePrefix("//").trim())
            }
        }
    }
}

context(ctx: CodegenContext)
public fun <T : Documentable.Builder<T>> T.addKdoc(element: CElement) = apply {
    val sb = StringBuilder()
    ctx.buildKdocStr(sb, element)
    if (sb.isNotBlank()) {
        addKdoc(sb.toString())
    }
}
