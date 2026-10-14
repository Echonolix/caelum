package net.echonolix.caelum.sdl3.codegen

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

internal object SDLConstantGenerator {
    private const val PACKAGE_NAME = "net.echonolix.caelum.sdl3"
    private const val INVENTORY_CHUNK_SIZE = 64

    fun generate(registry: SDLConstantRegistry, outputDir: Path): Set<Path> {
        val packageDir = outputDir.resolve(PACKAGE_NAME.replace('.', '/')).createDirectories()
        return setOf(packageDir.resolve("SDLConstants.kt").also { it.writeText(constantsFile(registry)) })
    }

    private fun constantsFile(registry: SDLConstantRegistry): String = buildString {
        appendLine(FILE_HEADER)
        appendLine("package $PACKAGE_NAME")
        appendLine()
        registry.entries.forEach { constant ->
            appendLine("public const val ${constant.name}: ${constant.value.kotlinType()} = ${constant.value.literal()}")
        }
        appendLine()
        appendLine("public enum class SDLConstantSource { ENUM, MACRO }")
        appendLine()
        appendLine("public enum class SDLConstantType { INT, UINT, LONG, ULONG, FLOAT, DOUBLE, STRING }")
        appendLine()
        appendLine("public data class SDLConstantInfo public constructor(")
        appendLine("    public val name: String,")
        appendLine("    public val source: SDLConstantSource,")
        appendLine("    public val type: SDLConstantType,")
        appendLine(")")
        appendLine()
        appendLine("public data class SDLSkippedConstantInfo public constructor(")
        appendLine("    public val name: String,")
        appendLine("    public val source: SDLConstantSource,")
        appendLine("    public val expression: String,")
        appendLine("    public val reason: String,")
        appendLine(")")
        appendLine()
        appendLine("public object SDLConstants {")
        appendLine("    @JvmField")
        appendLine("    public val entries: List<SDLConstantInfo> = buildList(${registry.entries.size}) {")
        registry.entries.chunked(INVENTORY_CHUNK_SIZE).indices.forEach { index ->
            appendLine("        addAll(entries$index())")
        }
        appendLine("    }")
        appendLine()
        appendLine("    @JvmField")
        appendLine("    public val skipped: List<SDLSkippedConstantInfo> = buildList(${registry.skipped.size}) {")
        registry.skipped.chunked(INVENTORY_CHUNK_SIZE).indices.forEach { index ->
            appendLine("        addAll(skipped$index())")
        }
        appendLine("    }")
        registry.entries.chunked(INVENTORY_CHUNK_SIZE).forEachIndexed { index, chunk ->
            appendLine()
            appendLine("    private fun entries$index(): List<SDLConstantInfo> = listOf(")
            chunk.forEach { constant ->
                appendLine(
                    "        SDLConstantInfo(\"${constant.name}\", " +
                        "SDLConstantSource.${constant.source.name}, SDLConstantType.${constant.value.typeName()}),",
                )
            }
            appendLine("    )")
        }
        registry.skipped.chunked(INVENTORY_CHUNK_SIZE).forEachIndexed { index, chunk ->
            appendLine()
            appendLine("    private fun skipped$index(): List<SDLSkippedConstantInfo> = listOf(")
            chunk.forEach { constant ->
                appendLine(
                    "        SDLSkippedConstantInfo(\"${escape(constant.name)}\", " +
                        "SDLConstantSource.${constant.source.name}, \"${escape(constant.expression)}\", " +
                        "\"${escape(constant.reason)}\"),",
                )
            }
            appendLine("    )")
        }
        appendLine("}")
    }

    private fun SDLConstantValue.kotlinType(): String = when (this) {
        is SDLConstantValue.SignedInteger -> if (bits == 32) "Int" else "Long"
        is SDLConstantValue.UnsignedInteger -> if (bits == 32) "UInt" else "ULong"
        is SDLConstantValue.FloatingPoint -> if (singlePrecision) "Float" else "Double"
        is SDLConstantValue.StringValue -> "String"
    }

    private fun SDLConstantValue.typeName(): String = when (this) {
        is SDLConstantValue.SignedInteger -> if (bits == 32) "INT" else "LONG"
        is SDLConstantValue.UnsignedInteger -> if (bits == 32) "UINT" else "ULONG"
        is SDLConstantValue.FloatingPoint -> if (singlePrecision) "FLOAT" else "DOUBLE"
        is SDLConstantValue.StringValue -> "STRING"
    }

    private fun SDLConstantValue.literal(): String = when (this) {
        is SDLConstantValue.SignedInteger -> when {
            bits == 32 && value == Int.MIN_VALUE.toLong() -> "Int.MIN_VALUE"
            bits == 32 -> value.toInt().toString()
            value == Long.MIN_VALUE -> "Long.MIN_VALUE"
            else -> "${value}L"
        }
        is SDLConstantValue.UnsignedInteger -> if (bits == 32) "${value.toUInt()}u" else "${value}uL"
        is SDLConstantValue.FloatingPoint -> {
            require(value.isFinite()) { "Non-finite SDL constant is not supported: $value" }
            if (singlePrecision) "${value.toFloat()}f" else value.toString()
        }
        is SDLConstantValue.StringValue -> "\"${escape(value)}\""
    }

    private fun escape(value: String): String = buildString {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '$' -> append("\\$")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000c' -> append("\\u000c")
                else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
            }
        }
    }

    private const val FILE_HEADER = """@file:Suppress("FunctionName", "ObjectPropertyName", "unused")"""
}
