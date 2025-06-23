@file:OptIn(ExperimentalStdlibApi::class)

package net.echonolix.caelum.codegen.api

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy

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

public fun TypeSpec.Builder.addAllocOverload(typeName: TypeName): TypeSpec.Builder =
    addSuperinterface(CaelumCodegenHelper.allocOverloadCName.parameterizedBy(typeName))
        .addProperty(
            PropertySpec.builder("layoutDelegate", CaelumCodegenHelper.memoryLayoutCName)
                .addModifiers(KModifier.OVERRIDE)
                .getter(
                    FunSpec.getterBuilder()
                        .addStatement("return layout")
                        .build()
                )
                .build()
        )

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