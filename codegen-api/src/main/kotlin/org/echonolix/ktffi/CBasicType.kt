package org.echonolix.ktffi

import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import java.lang.foreign.ValueLayout
import kotlin.reflect.KClass

enum class CBasicType(val kotlinType: KClass<*>, val literalSuffix: String, val valueLayout: ValueLayout?, val valueLayoutName: String?) {
    void(Unit::class, "", null, null),
    char(Char::class, "", ValueLayout.JAVA_BYTE, "JAVA_BYTE"),
    float(Float::class, "F", ValueLayout.JAVA_FLOAT, "JAVA_FLOAT"),
    double(Double::class, "", ValueLayout.JAVA_DOUBLE, "JAVA_DOUBLE"),
    int8_t(Byte::class, "", ValueLayout.JAVA_BYTE, "JAVA_BYTE"),
    uint8_t(UByte::class, "U", ValueLayout.JAVA_BYTE, "JAVA_BYTE"),
    int16_t(Short::class, "", ValueLayout.JAVA_SHORT, "JAVA_SHORT"),
    uint16_t(UShort::class, "U", ValueLayout.JAVA_SHORT, "JAVA_SHORT"),
    int32_t(Int::class, "", ValueLayout.JAVA_INT, "JAVA_INT"),
    uint32_t(UInt::class, "U",    ValueLayout.JAVA_INT, "JAVA_INT"),
    int64_t(Long::class, "L", ValueLayout.JAVA_LONG, "JAVA_LONG"),
    uint64_t(ULong::class, "UL", ValueLayout.JAVA_LONG,  "JAVA_LONG"),
    size_t(Long::class, "L", ValueLayout.JAVA_LONG, "JAVA_LONG"),
    int(Int::class, "", ValueLayout.JAVA_INT, "JAVA_INT");

    val typeName: TypeName = kotlinType.asTypeName()

    companion object {
        fun fromStringOrNull(type: String): CBasicType? {
            return when (type) {
                "void" -> void
                "char" -> char
                "float" -> float
                "double" -> double
                "int8_t" -> int8_t
                "uint8_t" -> uint8_t
                "int16_t" -> int16_t
                "uint16_t" -> uint16_t
                "uint32_t" -> uint32_t
                "uint64_t" -> uint64_t
                "size_t" -> size_t
                "int" -> int
                else -> null
            }
        }
        fun fromString(type: String): CBasicType {
            return fromStringOrNull(type) ?: throw IllegalArgumentException("Unknown CBasicType: $type")
        }
    }
}