@file:OptIn(ExperimentalStdlibApi::class)

package net.echonolix.caelum.codegen.api

import com.squareup.kotlinpoet.*
import net.echonolix.caelum.codegen.api.ctx.CodegenContext

public fun FileSpec.Builder.addSuppress(): FileSpec.Builder = apply {
    addAnnotation(
        AnnotationSpec.builder(Suppress::class)
            .addMember("%S", "PropertyName")
            .addMember("%S", "unused")
            .addMember("%S", "ObjectPropertyName")
            .addMember("%S", "ReplaceGetOrSet")
            .build()
    )
}

public fun String.decOrHexToInt(): Int = if (startsWith("0x")) {
    substring(2).toInt(16)
} else {
    toInt(10)
}

public fun String.decOrHexToULong(): ULong = if (startsWith("0x")) {
    substring(2).toULong(16)
} else {
    toULong(10)
}

public fun String.decOrHexToUInt(): UInt = if (startsWith("0x")) {
    substring(2).toUInt(16)
} else {
    toUInt(10)
}

private val xmlTagRegex = Regex("<[^>]+>")

public fun String.toXMLTagFreeString(): String {
    return replace(xmlTagRegex, "")
}

public fun String.removeContinuousSpaces(): String {
    return replace(""" +""".toRegex(), " ")
}

public fun String.pascalCaseToAllCaps(): String = buildString {
    append(this@pascalCaseToAllCaps[0])
    for (i in 1..<this@pascalCaseToAllCaps.length) {
        val last = this@pascalCaseToAllCaps[i - 1]
        val c = this@pascalCaseToAllCaps[i]
        if (c.isUpperCase() && !last.isUpperCase()) {
            append('_')
        }
        append(c.uppercaseChar())
    }
}

public fun String.decap(): String {
    return replaceFirstChar { it.lowercase() }
}

private val hexFormat = HexFormat {
    upperCase = true
    number.prefix = "0x"
}

public fun Int.toLiteralHexString(): String = toHexString(hexFormat)
public fun UInt.toLiteralHexString(): String = toHexString(hexFormat)
public fun ULong.toLiteralHexString(): String = toHexString(hexFormat)
public fun Long.toLiteralHexString(): String = toHexString(hexFormat)

private val optInCName = ClassName("kotlin", "OptIn")

public fun <T : Annotatable.Builder<*>> T.addOptIns(vararg optIns: TypeName): T = apply {
    addAnnotation(
        AnnotationSpec.builder(optInCName)
            .apply {
                optIns.forEach {
                    addMember("%T::class", it)
                }
            }
            .build()
    )
}

public fun CType.Function.funcName(): String {
    return tags.getOrNull<OriginalNameTag>()?.name ?: name
}

public fun CType.Function.funcDescPropertyName(): String {
    return "_${funcName()}_fd"
}

public fun CType.Function.funcMethodHandlePropertyName(): String {
    return "_${funcName()}_mh"
}

context(ctx: CodegenContext)
public fun List<CType.Function.Parameter>.toParameterCode(dst: MutableList<CodeBlock>): MutableList<CodeBlock> {
    return this.mapTo(dst) {
        CodeBlock.of("%T.toNativeData(%N)", it.type.typeDescriptorTypeName()!!, it.name)
    }
}

context(ctx: CodegenContext)
public fun fromNativeDataCodeBlock(type: CType): CodeBlock {
    return if (type is CType.Pointer && type.elementType.typeDescriptorTypeName() == null) {
        CodeBlock.of(
            "%T.fromNativeData<%T>(",
            type.typeDescriptorTypeName()!!,
            CBasicType.char.caelumCoreTypeName
        )
    } else {
        CodeBlock.of("%T.fromNativeData(", type.typeDescriptorTypeName()!!)
    }
}