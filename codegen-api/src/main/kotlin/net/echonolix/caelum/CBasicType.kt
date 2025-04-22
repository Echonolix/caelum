package net.echonolix.caelum

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
public sealed class CBasicType<T : Any>(
    public val cTypeNameStr: String,
    public val caelumCoreTypeNameStr: String,
    public val index: Int,
    public val kotlinType: KClass<T>,
    public val literalSuffix: String,
    public val valueLayout: ValueLayout,
    public val valueLayoutName: String,
    public val nativeDataType: KClass<*> = kotlinType,
    public val fromBase: String = "",
    public val toBase: String = "",
) {
    public object void : CBasicType<Unit>(
        "void",
        "NativeVoid",
        0,
        Unit::class,
        "",
        ValueLayout.JAVA_BYTE,
        "JAVA_BYTE"
    )

    public object char : CBasicType<Char>(
        "char",
        "NativeChar",
        1,
        Char::class,
        "",
        ValueLayout.JAVA_BYTE,
        "JAVA_BYTE"
    )

    public object int8_t : CBasicType<Byte>(
        "int8_t",
        "NativeInt8",
        2,
        Byte::class,
        "",
        ValueLayout.JAVA_BYTE,
        "JAVA_BYTE"
    )

    public object uint8_t : CBasicType<UByte>(
        "uint8_t",
        "NativeUInt8",
        3,
        UByte::class,
        "U",
        ValueLayout.JAVA_BYTE,
        "JAVA_BYTE",
        Byte::class,
        ".toUByte()",
        ".toByte()"
    )

    public object int16_t : CBasicType<Short>(
        "int16_t",
        "NativeInt16",
        4,
        Short::class,
        "",
        ValueLayout.JAVA_SHORT,
        "JAVA_SHORT"
    )

    public object uint16_t : CBasicType<UShort>(
        "uint16_t",
        "NativeUInt16",
        5,
        UShort::class,
        "U",
        ValueLayout.JAVA_SHORT,
        "JAVA_SHORT",
        Short::class,
        ".toUShort()",
        ".toShort()"
    )

    public object int32_t : CBasicType<Int>(
        "int32_t",
        "NativeInt32",
        6,
        Int::class,
        "",
        ValueLayout.JAVA_INT,
        "JAVA_INT"
    ) {
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

    public object uint32_t : CBasicType<UInt>(
        "uint32_t",
        "NativeUInt32",
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

    public object int : CBasicType<Int>(
        "int",
        "NativeInt",
        8,
        Int::class,
        "",
        ValueLayout.JAVA_INT,
        "JAVA_INT"
    ) {
        override fun codeBlock(valueStr: String): CodeBlock {
            return int32_t.codeBlock(valueStr)
        }
    }

    public object int64_t :
        CBasicType<Long>(
            "int64_t",
            "NativeInt64",
            9,
            Long::class,
            "L",
            ValueLayout.JAVA_LONG,
            "JAVA_LONG"
        ) {
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

    public object uint64_t : CBasicType<ULong>(
        "uint64_t",
        "NativeUInt64",
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

    public object size_t : CBasicType<Long>(
        "size_t",
        "NativeSize",
        11,
        Long::class,
        "L",
        ValueLayout.JAVA_LONG,
        "JAVA_LONG"
    )

    public object float : CBasicType<Float>(
        "float",
        "NativeFloat",
        12,
        Float::class,
        "F",
        ValueLayout.JAVA_FLOAT,
        "JAVA_FLOAT"
    ) {
        override fun codeBlock(valueStr: String): CodeBlock {
            return CodeBlock.of(valueStr)
        }
    }

    public object double : CBasicType<Double>(
        "double",
        "NativeDouble",
        12,
        Double::class,
        "",
        ValueLayout.JAVA_DOUBLE,
        "JAVA_DOUBLE"
    )

    public val nativeDataTypeName: TypeName = nativeDataType.asTypeName()
    public val ktApiTypeTypeName: TypeName = kotlinType.asTypeName()
    public val valueLayoutMember: MemberName = CaelumCodegenHelper.valueLayoutCname.member(valueLayoutName)
    public val caelumCoreTypeName: TypeName = if (cTypeNameStr == "void") {
        WildcardTypeName.producerOf(ANY.copy(nullable = true))
    } else {
        ClassName(CaelumCodegenHelper.basePkgName, caelumCoreTypeNameStr)
    }

    public open fun codeBlock(valueStr: String): CodeBlock {
        throw UnsupportedOperationException("Not implemented for $cTypeNameStr")
    }

    public val cType: CType.BasicType by lazy { CType.BasicType(this) }

    public companion object {
        private val invIntLiteralRegex = """~${CSyntax.intLiteralRegex}""".toRegex()

        public val ENTRIES: List<CBasicType<*>> by lazy {
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

        public val CTYPES: Map<CBasicType<*>, CType.BasicType> by lazy {
            ENTRIES.associateWith { it.cType }
        }

        public fun fromStringOrNull(type: String): CBasicType<*>? {
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

        public fun fromString(type: String): CBasicType<*> {
            return fromStringOrNull(type) ?: throw IllegalArgumentException("Unknown CBasicType: $type")
        }
    }

    public object Serializer : KSerializer<CBasicType<*>> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("type", PrimitiveKind.STRING)

        override fun serialize(
            encoder: Encoder, value: CBasicType<*>
        ) {
            encoder.encodeString(value.cTypeNameStr)
        }

        override fun deserialize(decoder: Decoder): CBasicType<*> {
            return fromString(decoder.decodeString())
        }
    }
}