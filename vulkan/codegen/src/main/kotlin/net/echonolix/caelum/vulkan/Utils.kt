package net.echonolix.caelum.vulkan

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeName
import kotlinx.serialization.decodeFromString
import net.echonolix.caelum.CTypeName
import net.echonolix.caelum.codegen.api.CType
import net.echonolix.caelum.codegen.api.OriginalNameTag
import net.echonolix.caelum.codegen.api.ctx.CodegenContext
import net.echonolix.caelum.codegen.api.ctx.filterTypeStream
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

fun CodegenContext.filterVkFunction(): List<CType.Function> =
    filterTypeStream<CType.Function>()
        .filter { it.first == it.second.tags.getOrNull<OriginalNameTag>()!!.name }
        .map { it.second }
        .filter { !it.name.startsWith("VkFuncPtr") }
        .filter { it.parameters.isNotEmpty() }
        .filter { it.parameters.first().type is CType.Handle }
        .sorted(
            compareBy<CType.Function> { !it.name.endsWith("ProcAddr") }
                .thenBy { it.name }
        )
        .toList()

context(ctx: CodegenContext)
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

context(ctx: CodegenContext)
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
    val parent = type.tags.getOrNull<VkHandleTag>()?.parent ?: return false
    return isDeviceBase(parent)
}

context(_: CodegenContext)
fun CType.Handle.objectBaseCName(): ClassName {
    return ClassName(packageName(), this.name)
}