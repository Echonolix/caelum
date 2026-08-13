package net.echonolix.caelum.sdl3.codegen

internal data class SDLRegistry(
    val functions: List<SDLFunction>,
    val skipped: List<SDLSkipped>,
    val namedTypes: Map<String, SDLNamedType>,
    val constants: List<SDLConstant>,
)

internal data class SDLConstant(
    val name: String,
    val kind: SDLConstantKind,
    val value: ULong,
)

internal enum class SDLConstantKind(val kotlinType: String) {
    INT("Int"),
    UINT("UInt"),
    ULONG("ULong"),
}

internal data class SDLFunction(
    val name: String,
    val returnType: SDLType,
    val parameters: List<SDLParameter>,
    val declaration: String,
)

internal data class SDLParameter(
    val name: String,
    val type: SDLType,
)

internal data class SDLSkipped(
    val name: String,
    val reason: String,
)

internal enum class SDLNamedKind {
    ENUM,
    FUNCTION_POINTER,
    GROUP,
    OPAQUE,
    POINTER_ALIAS,
    SCALAR_ALIAS,
}

internal data class SDLNamedType(
    val name: String,
    val kind: SDLNamedKind,
    val underlying: SDLScalar? = null,
)

internal sealed interface SDLType {
    data object Void : SDLType
    data class Scalar(val kind: SDLScalar) : SDLType
    data class Pointer(val pointee: String?, val depth: Int) : SDLType
    data class Aggregate(val name: String) : SDLType
}

internal enum class SDLScalar(
    val kotlinType: String,
    val carrierType: String,
    val descriptor: String,
    val toCarrier: (String) -> String,
    val fromCarrier: (String) -> String,
) {
    BOOL("Boolean", "Boolean", "NBool", { it }, { it }),
    BYTE("Byte", "Byte", "NInt8", { it }, { it }),
    UBYTE("UByte", "Byte", "NUInt8", { "$it.toByte()" }, { "$it.toUByte()" }),
    SHORT("Short", "Short", "NInt16", { it }, { it }),
    USHORT("UShort", "Short", "NUInt16", { "$it.toShort()" }, { "$it.toUShort()" }),
    INT("Int", "Int", "NInt", { it }, { it }),
    UINT("UInt", "Int", "NUInt32", { "$it.toInt()" }, { "$it.toUInt()" }),
    LONG("Long", "Long", "NInt64", { it }, { it }),
    ULONG("ULong", "Long", "NUInt64", { "$it.toLong()" }, { "$it.toULong()" }),
    FLOAT("Float", "Float", "NFloat", { it }, { it }),
    DOUBLE("Double", "Double", "NDouble", { it }, { it }),
}

internal fun SDLType.kotlinType(): String = when (this) {
    SDLType.Void -> "Unit"
    is SDLType.Scalar -> kind.kotlinType
    is SDLType.Pointer -> {
        val innermost = when (pointee) {
            null, "void" -> "*"
            "char" -> "NChar"
            "wchar_t" -> "NUInt16"
            else -> pointee
        }
        if (depth == 1) "NPointer<$innermost>" else (1 until depth).fold("NPointer<$innermost>") { value, _ -> "NPointer<$value>" }
    }
    is SDLType.Aggregate -> "NValue<$name>"
}
