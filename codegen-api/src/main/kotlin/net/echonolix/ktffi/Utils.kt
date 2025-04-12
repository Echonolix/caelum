package net.echonolix.ktffi

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec

fun FileSpec.Builder.addSuppress() = apply {
    addAnnotation(
        AnnotationSpec.builder(Suppress::class)
            .addMember("%S", "RemoveRedundantQualifierName")
            .addMember("%S", "PropertyName")
            .addMember("%S", "RedundantVisibilityModifier")
            .addMember("%S", "unused")
            .addMember("%S", "NOTHING_TO_INLINE")
            .addMember("%S", "RemoveExplicitTypeArguments")
            .build()
    )
}

fun String.decOrHexToInt(): Int = if (startsWith("0x")) {
    substring(2).toInt(16)
} else {
    toInt(10)
}

fun String.decOrHexToULong(): ULong = if (startsWith("0x")) {
    substring(2).toULong(16)
} else {
    toULong(10)
}

fun String.decOrHexToUInt(): UInt = if (startsWith("0x")) {
    substring(2).toUInt(16)
} else {
    toUInt(10)
}

private val xmlTagRegex = Regex("<[^>]+>")

fun String.toXMLTagFreeString(): String {
    return replace(xmlTagRegex, "")
}

fun String.removeContinuousSpaces(): String {
    return replace(""" +""".toRegex(), " ")
}

fun String.pascalCaseToAllCaps() = buildString {
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

fun String.decap(): String {
    return replaceFirstChar { it.lowercase() }
}
