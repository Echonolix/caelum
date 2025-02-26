package org.echonolix.ktffi

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.MemberName.Companion.member
import java.lang.foreign.ValueLayout
import kotlin.reflect.KClass

enum class CBasicType(
    val kotlinType: KClass<*>,
    val literalSuffix: String,
    val valueLayout: ValueLayout,
    val valueLayoutName: String,
    val baseType: KClass<*> = kotlinType,
    val fromBase: String = "",
    val toBase: String = "",
) {
    void(Unit::class, "", ValueLayout.JAVA_BYTE, "JAVA_BYTE"),
    char(Char::class, "", ValueLayout.JAVA_BYTE, "JAVA_BYTE"),
    float(Float::class, "F", ValueLayout.JAVA_FLOAT, "JAVA_FLOAT"),
    double(Double::class, "", ValueLayout.JAVA_DOUBLE, "JAVA_DOUBLE"),
    int8_t(Byte::class, "", ValueLayout.JAVA_BYTE, "JAVA_BYTE"),
    uint8_t(UByte::class, "U", ValueLayout.JAVA_BYTE, "JAVA_BYTE", Byte::class, ".toUByte()", ".toByte()"),
    int16_t(Short::class, "", ValueLayout.JAVA_SHORT, "JAVA_SHORT"),
    uint16_t(UShort::class, "U", ValueLayout.JAVA_SHORT, "JAVA_SHORT", Short::class, ".toUShort()", ".toShort()"),
    int32_t(Int::class, "", ValueLayout.JAVA_INT, "JAVA_INT"),
    uint32_t(UInt::class, "U", ValueLayout.JAVA_INT, "JAVA_INT", Int::class, ".toUInt()", ".toInt()"),
    int64_t(Long::class, "L", ValueLayout.JAVA_LONG, "JAVA_LONG"),
    uint64_t(ULong::class, "UL", ValueLayout.JAVA_LONG, "JAVA_LONG", Long::class, ".toULong()", ".toLong()"),
    size_t(Long::class, "L", ValueLayout.JAVA_LONG, "JAVA_LONG"),
    int(Int::class, "", ValueLayout.JAVA_INT, "JAVA_INT");

    val kotlinTypeName: TypeName = kotlinType.asTypeName()
    val valueLayoutMember = ValueLayout::class.member(valueLayoutName)
    val nativeTypeName = if (name == "void") {
        WildcardTypeName.producerOf(ANY.copy(nullable = true))
    } else {
        ClassName(KTFFICodegenHelper.packageName, name)
    }

    val cType by lazy { CType.BasicType(this) }

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