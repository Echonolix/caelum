package net.echonolix.caelum.sdl3.codegen

internal data class SDLConstantRegistry(
    val entries: List<SDLGeneratedConstant>,
    val skipped: List<SDLSkippedConstant>,
) {
    val enumCount: Int get() = entries.count { it.source == SDLConstantSource.ENUM }
    val macroCount: Int get() = entries.count { it.source == SDLConstantSource.MACRO }
}

internal data class SDLGeneratedConstant(
    val name: String,
    val value: SDLConstantValue,
    val source: SDLConstantSource,
)

internal sealed interface SDLConstantValue {
    data class SignedInteger(val value: Long, val bits: Int) : SDLConstantValue
    data class UnsignedInteger(val value: ULong, val bits: Int) : SDLConstantValue
    data class FloatingPoint(val value: Double, val singlePrecision: Boolean) : SDLConstantValue
    data class StringValue(val value: String) : SDLConstantValue
}

internal enum class SDLConstantSource {
    ENUM,
    MACRO,
}

internal data class SDLSkippedConstant(
    val name: String,
    val source: SDLConstantSource,
    val expression: String,
    val reason: String,
)
