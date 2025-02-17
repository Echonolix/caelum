package org.echonolix.vulkan

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.Documentable
import com.squareup.kotlinpoet.FileSpec
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.util.CompactFragment
import org.echonolix.vulkan.schema.Element

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

fun FileSpec.Builder.addSuppress() = apply {
    addAnnotation(
        AnnotationSpec.builder(Suppress::class)
            .addMember("%S", "RemoveRedundantQualifierName")
            .addMember("%S", "PropertyName")
            .addMember("%S", "RedundantVisibilityModifier")
            .addMember("%S", "unused")
            .build()
    )
}


inline fun <reified T : Any> CompactFragment.tryParseXML(): T? {
    return runCatching {
        XML.decodeFromString<T>(this.contentString)
    }.getOrNull()
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

fun <T : Documentable.Builder<T>> T.tryAddKdoc(element: Element) = apply {
    val docs = element.docs
    val since = element.requiredBy
    if (docs == null && since == null) {
        return@apply
    }
    val sb = StringBuilder()
    if (docs != null) {
        sb.append(docs)
        sb.append("\n\n")
    }
    if (since != null) {
        sb.append("@since: ")
        sb.append(since)
    }

    addKdoc(sb.toString())
}