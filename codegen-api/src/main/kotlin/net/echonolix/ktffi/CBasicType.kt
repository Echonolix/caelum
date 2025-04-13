package net.echonolix.ktffi

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.MemberName.Companion.member
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.lang.foreign.ValueLayout
import kotlin.reflect.KClass

@OptIn(ExperimentalStdlibApi::class)
@Serializable(with = CBasicType.Serializer::class)
@Suppress("ClassName")
sealed class CBasicType<T : Any>(
    val name: String,
    val index: Int,
    val kotlinType: KClass<T>,
    val literalSuffix: String,
    val valueLayout: ValueLayout,
    val valueLayoutName: String,
    val baseType: KClass<*> = kotlinType,
    val fromBase: String = "",
    val toBase: String = "",
) {
    object void : CBasicType<Unit>("void", 0, Unit::class, "", ValueLayout.JAVA_BYTE, "JAVA_BYTE")
    object char : CBasicType<Char>("char", 1, Char::class, "", ValueLayout.JAVA_BYTE, "JAVA_BYTE")
    object int8_t : CBasicType<Byte>("int8_t", 2, Byte::class, "", ValueLayout.JAVA_BYTE, "JAVA_BYTE")
    object uint8_t : CBasicType<UByte>(
        "uint8_t",
        3,
        UByte::class,
        "U",
        ValueLayout.JAVA_BYTE,
        "JAVA_BYTE",
        Byte::class,
        ".toUByte()",
        ".toByte()"
    )

    object int16_t : CBasicType<Short>("int16_t", 4, Short::class, "", ValueLayout.JAVA_SHORT, "JAVA_SHORT")
    object uint16_t : CBasicType<UShort>(
        "uint16_t",
        5,
        UShort::class,
        "U",
        ValueLayout.JAVA_SHORT,
        "JAVA_SHORT",
        Short::class,
        ".toUShort()",
        ".toShort()"
    )

    object int32_t : CBasicType<Int>("int32_t", 6, Int::class, "", ValueLayout.JAVA_INT, "JAVA_INT") {
        override fun codeBlock(valueStr: String): CodeBlock {
            return valueStr.toIntOrNull()?.let {
                CodeBlock.of("%L", it)
            } ?: run {
                var fixedStr = valueStr
                invIntLiteralRegex.find(fixedStr)?.let {
                    val (_, num, suffix) = it.destructured
                    fixedStr = fixedStr.replaceRange(it.range, "${num.toInt().inv().toLiteralHexString()}$suffix")
                }
                CodeBlock.of(fixedStr)
            }
        }
    }

    object uint32_t : CBasicType<UInt>(
        "uint32_t",
        7,
        UInt::class,
        "U",
        ValueLayout.JAVA_INT,
        "JAVA_INT",
        Int::class,
        ".toUInt()",
        ".toInt()"
    ) {
        override fun codeBlock(valueStr: String): CodeBlock {
            return valueStr.toIntOrNull()?.let {
                CodeBlock.of("%L$literalSuffix", it)
            } ?: run {
                var fixedStr = valueStr
                invIntLiteralRegex.find(fixedStr)?.let {
                    val (_, num, suffix) = it.destructured
                    fixedStr = fixedStr.replaceRange(it.range, "${num.toUInt().inv().toLiteralHexString()}$suffix")
                }
                CodeBlock.of(fixedStr)
            }
        }
    }

    object int : CBasicType<Int>("int", 8, Int::class, "", ValueLayout.JAVA_INT, "JAVA_INT") {
        override fun codeBlock(valueStr: String): CodeBlock {
            return int32_t.codeBlock(valueStr)
        }
    }

    object int64_t : CBasicType<Long>("int64_t", 9, Long::class, "L", ValueLayout.JAVA_LONG, "JAVA_LONG") {
        override fun codeBlock(valueStr: String): CodeBlock {
            return valueStr.toLongOrNull()?.let {
                CodeBlock.of("%L$literalSuffix", it)
            } ?: run {
                var fixedStr = valueStr
                invIntLiteralRegex.find(fixedStr)?.let {
                    val (_, num, suffix) = it.destructured
                    fixedStr = fixedStr.replaceRange(it.range, "${num.toLong().inv().toLiteralHexString()}$suffix")
                }
                fixedStr = fixedStr.replace("LL", "L")
                CodeBlock.of(fixedStr)
            }
        }
    }

    object uint64_t : CBasicType<ULong>(
        "uint64_t",
        10,
        ULong::class,
        "UL",
        ValueLayout.JAVA_LONG,
        "JAVA_LONG",
        Long::class,
        ".toULong()",
        ".toLong()"
    ) {
        override fun codeBlock(valueStr: String): CodeBlock {
            return valueStr.toLongOrNull()?.let {
                CodeBlock.of("%L$literalSuffix", it)
            } ?: run {
                var fixedStr = valueStr
                invIntLiteralRegex.find(fixedStr)?.let {
                    val (_, num, suffix) = it.destructured
                    fixedStr = fixedStr.replaceRange(it.range, "${num.toULong().inv().toLiteralHexString()}$suffix")
                }
                fixedStr = fixedStr.replace("LL", "L")
                CodeBlock.of(fixedStr)
            }
        }
    }

    object size_t : CBasicType<Long>("size_t", 11, Long::class, "L", ValueLayout.JAVA_LONG, "JAVA_LONG")
    object float : CBasicType<Float>("float", 12, Float::class, "F", ValueLayout.JAVA_FLOAT, "JAVA_FLOAT") {
        override fun codeBlock(valueStr: String): CodeBlock {
            return CodeBlock.of(valueStr)
        }
    }
    object double : CBasicType<Double>("double", 12, Double::class, "", ValueLayout.JAVA_DOUBLE, "JAVA_DOUBLE")

    val kotlinTypeName: TypeName = kotlinType.asTypeName()
    val valueLayoutMember = KTFFICodegenHelper.valueLayoutCname.member(valueLayoutName)
    val ktffiNativeTypeName = if (name == "void") {
        WildcardTypeName.producerOf(ANY.copy(nullable = true))
    } else {
        ClassName(KTFFICodegenHelper.packageName, name)
    }

    open fun codeBlock(valueStr: String): CodeBlock {
        throw UnsupportedOperationException("Not implemented for $name")
    }

    val cType by lazy { CType.BasicType(this) }

    companion object {
        private val invIntLiteralRegex = """~${CSyntax.intLiteralRegex}""".toRegex()

        val ENTRIES by lazy {
            listOf(
                void,
                char,
                int8_t,
                uint8_t,
                int16_t,
                uint16_t,
                int32_t,
                uint32_t,
                int,
                int64_t,
                uint64_t,
                size_t,
                float,
                double
            )
        }

        val CTYPES by lazy {
            ENTRIES.associateWith { it.cType }
        }

        fun fromStringOrNull(type: String): CBasicType<*>? {
            return when (type) {
                "void" -> void
                "char" -> char
                "int8_t" -> int8_t
                "uint8_t" -> uint8_t
                "int16_t" -> int16_t
                "uint16_t" -> uint16_t
                "int32_t" -> int32_t
                "uint32_t" -> uint32_t
                "int" -> int
                "int64_t" -> int64_t
                "uint64_t" -> uint64_t
                "size_t" -> size_t
                "float" -> float
                "double" -> double
                else -> null
            }
        }

        fun fromString(type: String): CBasicType<*> {
            return fromStringOrNull(type) ?: throw IllegalArgumentException("Unknown CBasicType: $type")
        }
    }

    object Serializer : KSerializer<CBasicType<*>> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("type", PrimitiveKind.STRING)

        override fun serialize(
            encoder: Encoder,
            value: CBasicType<*>
        ) {
            encoder.encodeString(value.name)
        }

        override fun deserialize(decoder: Decoder): CBasicType<*> {
            return fromString(decoder.decodeString())
        }
    }
}