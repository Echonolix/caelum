package net.echonolix.caelum.vulkan.ffi

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.Documentable
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeName
import kotlinx.serialization.decodeFromString
import net.echonolix.caelum.*
import net.echonolix.caelum.codegen.api.CElement
import net.echonolix.caelum.codegen.api.CType
import net.echonolix.caelum.codegen.api.CaelumCodegenContext
import net.echonolix.caelum.codegen.api.removeContinuousSpaces
import net.echonolix.caelum.codegen.api.toXMLTagFreeString
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

context(ctx: VulkanCodeGenContext)
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

fun VulkanCodeGenContext.filterVkFunction(): List<CType.Function> =
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

context(ctx: VulkanCodeGenContext)
fun List<CType.Function.Parameter>.toKtParamOverloadSpecs(annotations: Boolean) =
    toParamSpecs(annotations) {
        val paramType = it.type
        var pType = paramType.ktApiType()
        if (paramType is CType.Pointer && it.tags.has<OptionalTag>()) {
            pType = pType.copy(nullable = true)
        }
        if (paramType is CType.Handle) {
            pType = paramType.objectBaseCName()
        }
        pType
    }

context(ctx: VulkanCodeGenContext)
fun List<CType.Function.Parameter>.toKtParamSpecs(annotations: Boolean) =
    toParamSpecs(annotations) {
        var pType = it.type.ktApiType()
        if (it.type is CType.Pointer && it.tags.has<OptionalTag>()) {
            pType = pType.copy(nullable = true)
        }
        pType
    }

context(ctx: VulkanCodeGenContext)
fun List<CType.Function.Parameter>.toNativeParamSpecs(annotations: Boolean) =
    toParamSpecs(annotations) { it.type.nativeType() }

context(ctx: CaelumCodegenContext)
inline fun List<CType.Function.Parameter>.toParamSpecs(
    annotations: Boolean,
    typeMapper: (CType.Function.Parameter) -> TypeName
) = map {
    val builder = ParameterSpec.builder(it.name, typeMapper(it))
    if (annotations) {
        builder.addAnnotation(
            AnnotationSpec.builder(CTypeName::class)
                .addMember("%S", it.type.name)
                .build()
        )
    }
    builder.build()
}

tailrec fun isDeviceBase(type: CType.Handle): Boolean {
    if (type.name == "VkDevice") return true
    val parent = type.tags.get<VkHandleTag>()?.parent ?: return false
    return isDeviceBase(parent)
}

context(_: VulkanCodeGenContext)
fun CType.Handle.objectBaseCName(): ClassName {
    return ClassName(packageName(), this.name)
}