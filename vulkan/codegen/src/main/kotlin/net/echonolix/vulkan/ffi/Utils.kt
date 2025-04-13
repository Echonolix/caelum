package net.echonolix.vulkan.ffi

import com.squareup.kotlinpoet.Documentable
import kotlinx.serialization.decodeFromString
import net.echonolix.ktffi.CElement
import net.echonolix.ktffi.removeContinuousSpaces
import net.echonolix.ktffi.toXMLTagFreeString
import net.echonolix.vulkan.schema.Element
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.util.CompactFragment

val __xml = XML {
    defaultToGenericParser = true
}

inline fun <reified T : Any> CompactFragment.tryParseXML(): T? {
    return runCatching {
        __xml.decodeFromString<T>(this.contentString)
    }.getOrNull()
}

private val xmlTagRegex = Regex("<[^>]+>")

fun List<CompactFragment>.toXmlTagFreeString() =
    joinToString(" ") { it.contentString.toXMLTagFreeString() }.removeContinuousSpaces()

fun <T : Documentable.Builder<T>> T.tryAddKdoc(element: Element) = apply {
    val docs = element.docs
    val since = element.requiredBy
    if (docs == null && since == null) {
        return@apply
    }
    val sb = StringBuilder()
    if (docs != null) {
        sb.append(docs.removePrefix("//").trim())
        sb.append("\n\n")
    }
    if (since != null) {
        sb.append("@since: ")
        sb.append(since)
    }

    addKdoc(sb.toString())
}

context(ctx: VKFFICodeGenContext)
fun <T : Documentable.Builder<T>> T.tryAddKdoc(element: CElement) = apply {
    val docs = element.tags.get<ElementCommentTag>()?.comment
    val since = element.tags.get<RequiredByTag>()?.requiredBy
    val aliasDst = element.tags.get<AliasedTag>()?.dst
    if (docs == null && since == null && aliasDst == null) {
        return@apply
    }
    val sb = StringBuilder()
    if (docs != null) {
        sb.appendLine(docs.removePrefix("//").trim())
    }
    if (aliasDst != null) {
        sb.appendLine("Alias for [${aliasDst.memberName()}]")
    }
    if (since != null) {
        sb.append("@since: ")
        sb.append(since)
    }

    addKdoc(sb.toString())
}

interface MemberVisitor {
    fun visit(member: Element.Member)
    fun visitOpaqueType(index: Int, member: Element.Member, name: String)
    fun visitBasicType(index: Int, member: Element.Member, type: Element.BasicType)
    fun visitHandleType(index: Int, member: Element.Member, type: Element.HandleType)
    fun visitEnumType(index: Int, member: Element.Member, type: Element.EnumType)
    fun visitFlagType(index: Int, member: Element.Member, type: Element.FlagType, flagBitType: Element.FlagBitType?)
    fun visitFuncpointerType(index: Int, member: Element.Member, type: Element.FuncpointerType)
    fun visitStructType(index: Int, member: Element.Member, type: Element.Struct)
    fun visitUnionType(index: Int, member: Element.Member, type: Element.Union)
    fun visitPointer(index: Int, member: Element.Member, type: Element.Type)
    fun visitArray(index: Int, member: Element.Member, type: Element.Type)
}