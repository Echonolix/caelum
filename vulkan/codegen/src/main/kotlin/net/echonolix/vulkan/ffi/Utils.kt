package net.echonolix.vulkan.ffi

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.Documentable
import com.squareup.kotlinpoet.ParameterSpec
import kotlinx.serialization.decodeFromString
import net.echonolix.ktffi.*
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

fun List<CompactFragment>.toXmlTagFreeString() =
    joinToString(" ") { it.contentString.toXMLTagFreeString() }.removeContinuousSpaces()

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

fun VKFFICodeGenContext.filterVkFunction(): List<CType.Function> =
    filterTypeStream<CType.Function>()
        .filter { it.first == it.second.tags.get<OriginalFunctionNameTag>()!!.name }
        .map { it.second }
        .filter { !it.name.startsWith("VkFuncPtr") }
        .filter { it.parameters.isNotEmpty() }
        .filter { it.parameters.first().type is CType.Handle }
        .sorted(
            compareBy<CType.Function> { !it.name.endsWith("ProcAddr") }
                .thenBy { it.name }
        )
        .toList()

context(ctx: VKFFICodeGenContext)
fun List<CType.Function.Parameter>.toKtParamSpecs(annotations: Boolean) = map {
    var pType = it.type.ktApiType()
    if (it.type is CType.Pointer && it.optional) {
        pType = pType.copy(nullable = true)
    }
    ParameterSpec.builder(it.name, pType)
        .apply {
            if (annotations) {
                addAnnotation(
                    AnnotationSpec.builder(CTypeName::class)
                        .addMember("%S", it.type.name)
                        .build()
                )
            }
        }
        .build()
}

context(ctx: VKFFICodeGenContext)
fun List<CType.Function.Parameter>.toNativeParamSpecs(annotations: Boolean) = map {
    ParameterSpec.builder(it.name, it.type.nativeType())
        .apply {
            if (annotations) {
                addAnnotation(
                    AnnotationSpec.builder(CTypeName::class)
                        .addMember("%S", it.type.name)
                        .build()
                )
            }
        }
        .build()
}